package argus.domain.detector

import argus.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExamDetectorEngineTest {

    private fun airpodsSignal(rssi: Int, ts: Long) = RawBleSignal(
        deviceName = null,
        serviceUuids16Bit = emptyList(),
        companyId = 0x004C,
        manufacturerData = byteArrayOf(0x07, 0x19, 0x01, 0x02),
        rssi = rssi,
        timestampMs = ts
    )

    private fun hallwayWatchSignal(rssi: Int, ts: Long) = RawBleSignal(
        deviceName = "HallwayWatch",
        serviceUuids16Bit = listOf("180D"),
        companyId = 0x0075,
        manufacturerData = byteArrayOf(0x42, 0x01),
        rssi = rssi,
        timestampMs = ts
    )

    @Test
    fun `Phase 1 Baseline은 60초 카운트다운 후 자동으로 Monitoring으로 전환된다`() {
        var now = 10_000L
        val engine = ExamDetectorEngine(clock = { now })

        engine.startBaseline()
        assertThat(engine.uiState.value.phase).isEqualTo(ScanPhase.BASELINE)
        assertThat(engine.uiState.value.baselineRemainingSec).isEqualTo(60)

        engine.onBleSignal(hallwayWatchSignal(rssi = -76, ts = now + 1000L))
        assertThat(engine.uiState.value.baselineDeviceCount).isEqualTo(1)

        now += 30_000L
        engine.tick(now)
        assertThat(engine.uiState.value.phase).isEqualTo(ScanPhase.BASELINE)
        assertThat(engine.uiState.value.baselineRemainingSec).isEqualTo(30)

        now += 30_000L
        engine.tick(now)
        assertThat(engine.uiState.value.phase).isEqualTo(ScanPhase.MONITORING)
    }

    @Test
    fun `Phase 2 신규 기기는 15초 내 3회 이상 관측되고 RSSI가 -68 초과일 때만 경고를 발생시킨다`() {
        var now = 0L
        val engine = ExamDetectorEngine(clock = { now })
        engine.startBaseline()
        now = 60_000L
        engine.startMonitoring()

        // 1회, 2회 관측만으로는 경고 미발생
        engine.onBleSignal(airpodsSignal(rssi = -60, ts = 61_000L))
        engine.onBleSignal(airpodsSignal(rssi = -59, ts = 62_000L))
        engine.flushMonitoringWindow(65_000L)
        assertThat(engine.uiState.value.suspiciousAlerts).isEmpty()

        // 약한 신호(-72 dBm)로 3회째 들어와도 -68 dBm 이하이므로 경고 미발생
        engine.onBleSignal(airpodsSignal(rssi = -72, ts = 66_000L))
        engine.flushMonitoringWindow(70_000L)
        assertThat(engine.uiState.value.suspiciousAlerts).isEmpty()

        // 강한 신호(-55 dBm)로 관측되면 15초 내 3회 이상 + > -68 dBm 충족 -> 경고 발생
        engine.onBleSignal(airpodsSignal(rssi = -55, ts = 71_000L))
        engine.flushMonitoringWindow(75_000L)

        val alerts = engine.uiState.value.suspiciousAlerts
        assertThat(alerts).hasSize(1)
        assertThat(alerts.first().reason).isEqualTo(AlertReason.NEW_DEVICE)
        assertThat(alerts.first().device.isSuspiciousAudioOrWearable).isTrue()
    }

    @Test
    fun `Phase 2 Baseline 기기가 +15 dBm 이상 급접근하면 SUDDEN_APPROACH 경고가 발생한다`() {
        var now = 0L
        val engine = ExamDetectorEngine(clock = { now })
        engine.startBaseline()
        engine.onBleSignal(hallwayWatchSignal(rssi = -76, ts = 5_000L))

        now = 60_000L
        engine.startMonitoring()

        // -76 -> -58 (+18 dBm 급상승)
        engine.onBleSignal(hallwayWatchSignal(rssi = -58, ts = 62_000L))
        engine.flushMonitoringWindow(65_000L)

        val alerts = engine.uiState.value.suspiciousAlerts
        assertThat(alerts).hasSize(1)
        assertThat(alerts.first().reason).isEqualTo(AlertReason.SUDDEN_APPROACH)
        assertThat(alerts.first().baselineMaxRssi).isEqualTo(-76)
    }

    @Test
    fun `시험 시작 직후 관측되다 15초 이상 사라진 기기는 전원 차단 의심 로그에 기록된다`() {
        var now = 0L
        val engine = ExamDetectorEngine(clock = { now })
        engine.startBaseline()
        now = 60_000L
        engine.startMonitoring()

        // 학생이 입실 직후 에어팟을 켰다가 끔
        engine.onBleSignal(airpodsSignal(rssi = -56, ts = 61_000L))
        engine.flushMonitoringWindow(65_000L)

        // 15초 이상 미관측 상태로 tick 진행 (61_000 + 16_000 = 77_000)
        now = 77_000L
        engine.tick(now)

        val powerOffs = engine.uiState.value.powerOffEvents
        assertThat(powerOffs).hasSize(1)
        assertThat(powerOffs.first().manufacturer).isEqualTo("Apple")
    }

    @Test
    fun `Phase 3 Radar 모드는 타겟 기기 신호만 추적하고 -45 dBm 이상에서 VERY_CLOSE를 반환한다`() {
        var now = 0L
        val engine = ExamDetectorEngine(clock = { now })
        engine.startBaseline()
        now = 60_000L
        engine.startMonitoring()

        engine.onBleSignal(airpodsSignal(rssi = -60, ts = 61_000L))
        engine.onBleSignal(airpodsSignal(rssi = -58, ts = 62_000L))
        engine.onBleSignal(airpodsSignal(rssi = -55, ts = 63_000L))
        engine.flushMonitoringWindow(65_000L)

        val targetKey = engine.uiState.value.suspiciousAlerts.first().device.clusterKey
        engine.enterRadarMode(targetKey)
        assertThat(engine.uiState.value.phase).isEqualTo(ScanPhase.RADAR_LOCATING)

        // 다른 기기 신호는 무시되어야 함
        engine.onBleSignal(hallwayWatchSignal(rssi = -40, ts = 66_000L))
        assertThat(engine.uiState.value.radarTarget?.currentRawRssi).isEqualTo(-55)

        // 타겟 기기가 -42 dBm으로 근접
        engine.onBleSignal(airpodsSignal(rssi = -42, ts = 67_000L))
        val radar = engine.uiState.value.radarTarget
        assertThat(radar?.currentRawRssi).isEqualTo(-42)
        assertThat(radar?.proximityZone).isEqualTo(ProximityZone.VERY_CLOSE)
        assertThat(radar?.rssiHistory).containsExactly(-55, -42).inOrder()
    }

    @Test
    fun `buildSessionRecord는 현재 스캔 세션의 기준선, 경고, 패킷 요약을 스냅샷으로 생성한다`() {
        var now = 10_000L
        val engine = ExamDetectorEngine(clock = { now })
        engine.startBaseline()
        engine.onBleSignal(hallwayWatchSignal(rssi = -75, ts = 15_000L))

        now = 70_000L
        engine.startMonitoring()
        engine.onBleSignal(airpodsSignal(rssi = -56, ts = 71_000L))
        engine.onBleSignal(airpodsSignal(rssi = -54, ts = 72_000L))
        engine.onBleSignal(airpodsSignal(rssi = -52, ts = 73_000L))
        engine.flushMonitoringWindow(75_000L)

        val record = engine.buildSessionRecord(
            id = "test-session", title = "Room 302", endedAtMs = 130_000L
        )

        assertThat(record).isNotNull()
        assertThat(record!!.id).isEqualTo("test-session")
        assertThat(record.title).isEqualTo("Room 302")
        assertThat(record.durationSec).isEqualTo(120L)
        assertThat(record.baselineDeviceCount).isEqualTo(1)
        assertThat(record.blePacketsProcessed).isEqualTo(4L)
        assertThat(record.wifiPacketsProcessed).isEqualTo(0L)
        assertThat(record.suspiciousAlerts).hasSize(1)
        assertThat(record.suspiciousAlerts.first().manufacturer).isEqualTo("Apple")
    }

    @Test
    fun `사용자 지정 DetectorConfig가 Baseline 시간, 노이즈 하한, 급접근 임계치, 레이더 좌석 특정 기준에 반영된다`() {
        var now = 0L
        val customConfig = DetectorConfig(
            baselineDurationSec = 30, noiseFloorRssi = -75, surgeDeltaDbm = 10, radarVeryCloseRssi = -50
        )
        val engine = ExamDetectorEngine(clock = { now }, initialConfig = customConfig)
        engine.startBaseline()
        assertThat(engine.uiState.value.baselineRemainingSec).isEqualTo(30)

        // -78 dBm은 noiseFloorRssi(-75) 미만이므로 무시되어야 함
        engine.onBleSignal(hallwayWatchSignal(rssi = -78, ts = 1_000L))
        assertThat(engine.uiState.value.baselineDeviceCount).isEqualTo(0)

        // -70 dBm은 수집됨
        engine.onBleSignal(hallwayWatchSignal(rssi = -70, ts = 2_000L))
        assertThat(engine.uiState.value.baselineDeviceCount).isEqualTo(1)

        // 30초 경과 시 자동 모니터링 전환
        now = 30_000L
        engine.tick(now)
        assertThat(engine.uiState.value.phase).isEqualTo(ScanPhase.MONITORING)

        // -70 -> -60 (+10 dB)는 커스텀 surgeDeltaDbm(10)을 충족하므로 경고 발생
        engine.onBleSignal(hallwayWatchSignal(rssi = -60, ts = 31_000L))
        engine.flushMonitoringWindow(35_000L)
        assertThat(engine.uiState.value.suspiciousAlerts).hasSize(1)

        val key = engine.uiState.value.suspiciousAlerts.first().device.clusterKey
        engine.enterRadarMode(key)
        engine.onBleSignal(hallwayWatchSignal(rssi = -49, ts = 36_000L))
        assertThat(engine.uiState.value.radarTarget?.proximityZone).isEqualTo(ProximityZone.VERY_CLOSE)
    }
}
