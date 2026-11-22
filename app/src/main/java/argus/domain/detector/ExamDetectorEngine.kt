package argus.domain.detector

import argus.domain.fingerprint.FingerprintGenerator
import argus.domain.fingerprint.FingerprintResult
import argus.domain.model.*
import argus.domain.signal.CollisionClusterer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

/**
 * 3단계 상태 머신(Baseline -> Monitoring -> Radar Locating) 기반 시험 부정행위 무선 기기 탐지 엔진.
 */
class ExamDetectorEngine(
    private val clock: () -> Long = { System.currentTimeMillis() }, initialConfig: DetectorConfig = DetectorConfig()
) {
    companion object {
        const val NOISE_FLOOR_RSSI = -80
        const val BASELINE_DURATION_SEC = 60
        const val MONITORING_WINDOW_MS = 5_000L
        const val OBSERVATION_WINDOW_MS = 15_000L
        const val NEW_DEVICE_MIN_OBSERVATIONS = 3
        const val NEW_DEVICE_MIN_RSSI = -68
        const val SURGE_DELTA_DBM = 15
        const val POWER_OFF_TIMEOUT_MS = 15_000L
        const val RADAR_VERY_CLOSE_RSSI = -45
        const val RADAR_NEAR_RSSI = -65
        const val MAX_RADAR_HISTORY = 30
    }

    @Volatile
    private var config: DetectorConfig = initialConfig

    private val clusterer = CollisionClusterer()

    // Phase 1 Baseline 저장소: <ClusterKey, MaxRSSI>
    private val baselineMap = mutableMapOf<String, Int>()
    private val baselineDevices = mutableMapOf<String, DetectedDevice>()

    // Phase 2 최근 관측 타임스탬프 (15초 슬라이딩 윈도우)
    private val observationTimestamps = mutableMapOf<String, ArrayDeque<Long>>()
    private val latestDevices = mutableMapOf<String, DetectedDevice>()
    private val activeAlerts = mutableMapOf<String, SuspiciousAlert>()

    // 5초 윈도우 버퍼: 해당 윈도우 동안 들어온 기기별 최고 RSSI 스냅샷
    private val windowBuffer = mutableMapOf<String, DetectedDevice>()
    private var lastWindowEvalMs: Long = 0L

    // 전원 차단 의심 추적
    private val recordedPowerOffKeys = mutableSetOf<String>()
    private val powerOffHistory = mutableListOf<PowerOffEvent>()

    private var baselineStartMs: Long = 0L
    private var monitoringStartMs: Long = 0L
    private var radarTargetKey: String? = null
    private val radarRssiHistory = ArrayDeque<Int>()

    private val _uiState = MutableStateFlow(
        DetectorUiState(
            baselineRemainingSec = initialConfig.baselineDurationSec,
            baselineTotalDurationSec = initialConfig.baselineDurationSec
        )
    )
    val uiState: StateFlow<DetectorUiState> = _uiState.asStateFlow()

    fun currentConfig(): DetectorConfig = config

    fun updateConfig(newConfig: DetectorConfig) {
        config = newConfig
        if (_uiState.value.phase == ScanPhase.IDLE) {
            _uiState.update {
                it.copy(
                    baselineRemainingSec = newConfig.baselineDurationSec,
                    baselineTotalDurationSec = newConfig.baselineDurationSec
                )
            }
        }
    }

    fun startBaseline() {
        val now = clock()
        val baselineSec = config.baselineDurationSec
        baselineStartMs = now
        monitoringStartMs = 0L
        baselineMap.clear()
        baselineDevices.clear()
        observationTimestamps.clear()
        latestDevices.clear()
        activeAlerts.clear()
        windowBuffer.clear()
        recordedPowerOffKeys.clear()
        powerOffHistory.clear()
        radarRssiHistory.clear()
        clusterer.reset()
        radarTargetKey = null

        _uiState.value = DetectorUiState(
            phase = ScanPhase.BASELINE,
            baselineRemainingSec = baselineSec,
            baselineTotalDurationSec = baselineSec,
            sessionStartedAtMillis = now
        )
    }

    fun startMonitoring() {
        val now = clock()
        if (monitoringStartMs == 0L) {
            monitoringStartMs = now
        }
        lastWindowEvalMs = now
        windowBuffer.clear()
        radarTargetKey = null
        radarRssiHistory.clear()
        _uiState.update {
            it.copy(
                phase = ScanPhase.MONITORING, baselineRemainingSec = 0, radarTarget = null
            )
        }
    }

    fun enterRadarMode(clusterKey: String) {
        val device = activeAlerts[clusterKey]?.device ?: latestDevices[clusterKey] ?: return
        radarTargetKey = clusterKey
        radarRssiHistory.clear()
        radarRssiHistory.addLast(device.rawRssi)
        _uiState.update {
            it.copy(
                phase = ScanPhase.RADAR_LOCATING, radarTarget = RadarTargetState(
                    targetDevice = device,
                    currentRawRssi = device.rawRssi,
                    currentFilteredRssi = device.filteredRssi,
                    peakRssi = device.rawRssi,
                    proximityZone = resolveProximity(device.rawRssi),
                    lastUpdatedMillis = device.lastSeenMillis,
                    rssiHistory = radarRssiHistory.toList()
                )
            )
        }
    }

    fun exitRadarMode() {
        radarTargetKey = null
        radarRssiHistory.clear()
        _uiState.update {
            it.copy(
                phase = ScanPhase.MONITORING, radarTarget = null
            )
        }
    }

    fun resetToIdle() {
        radarTargetKey = null
        radarRssiHistory.clear()
        _uiState.value = DetectorUiState(
            phase = ScanPhase.IDLE,
            baselineRemainingSec = config.baselineDurationSec,
            baselineTotalDurationSec = config.baselineDurationSec
        )
    }

    fun buildSessionRecord(
        id: String, title: String, endedAtMs: Long = clock()
    ): ScanSessionRecord? {
        val state = _uiState.value
        if (state.phase == ScanPhase.IDLE && state.totalPacketsProcessed == 0L && baselineMap.isEmpty()) {
            return null
        }
        val startedAt =
            state.sessionStartedAtMillis.takeIf { it > 0L } ?: baselineStartMs.takeIf { it > 0L } ?: endedAtMs
        val durationSec = ((endedAtMs - startedAt) / 1000L).coerceAtLeast(1L)
        val totalBaselineSec = state.baselineTotalDurationSec.coerceAtLeast(1)
        val actualBaselineSec = if (monitoringStartMs > 0L && baselineStartMs > 0L) {
            ((monitoringStartMs - baselineStartMs) / 1000L).toInt().coerceIn(0, totalBaselineSec)
        } else {
            (totalBaselineSec - state.baselineRemainingSec).coerceIn(0, totalBaselineSec)
        }

        val baselineRecords =
            baselineDevices.values.sortedByDescending { baselineMap[it.clusterKey] ?: it.rawRssi }.map { dev ->
                    BaselineDeviceRecord(
                        clusterKey = dev.clusterKey,
                        displayName = dev.displayName,
                        manufacturer = dev.manufacturer,
                        type = dev.type,
                        maxRssi = baselineMap[dev.clusterKey] ?: dev.rawRssi,
                        isSuspiciousAudioOrWearable = dev.isSuspiciousAudioOrWearable
                    )
                }

        val alertRecords = state.suspiciousAlerts.map { alert ->
            SuspiciousAlertRecord(
                clusterKey = alert.device.clusterKey,
                displayName = alert.device.displayName,
                manufacturer = alert.device.manufacturer,
                type = alert.device.type,
                reason = alert.reason,
                riskScore = alert.riskScore,
                rawRssi = alert.device.rawRssi,
                filteredRssi = alert.device.filteredRssi,
                baselineMaxRssi = alert.baselineMaxRssi,
                observationCountIn15s = alert.observationCountIn15s,
                isSuspiciousAudioOrWearable = alert.device.isSuspiciousAudioOrWearable,
                firstDetectedAt = alert.firstDetectedAt,
                lastDetectedAt = alert.lastDetectedAt
            )
        }

        return ScanSessionRecord(
            id = id,
            title = title,
            startedAtMillis = startedAt,
            endedAtMillis = endedAtMs,
            durationSec = durationSec,
            baselineDurationSec = actualBaselineSec,
            baselineDeviceCount = baselineMap.size,
            totalPacketsProcessed = state.totalPacketsProcessed,
            blePacketsProcessed = state.blePacketsProcessed,
            wifiPacketsProcessed = state.wifiPacketsProcessed,
            baselineDevices = baselineRecords,
            suspiciousAlerts = alertRecords,
            powerOffEvents = state.powerOffEvents,
            source = SessionSource.LOCAL,
            importedAtMillis = null
        )
    }

    fun onBleSignal(signal: RawBleSignal) {
        if (signal.rssi < config.noiseFloorRssi) return
        val fp = FingerprintGenerator.generateBle(signal)
        processSignal(fp, DeviceType.BLE, signal.rssi, signal.timestampMs)
    }

    fun onWifiSignal(signal: RawWifiSignal) {
        if (signal.rssi < config.noiseFloorRssi) return
        val fp = FingerprintGenerator.generateWifi(signal)
        processSignal(fp, DeviceType.WIFI, signal.rssi, signal.timestampMs)
    }

    /**
     * 주기적(1초마다) 호출되어 Baseline 카운트다운, 5초 모니터링 윈도우 평가, 전원 차단 감지를 수행한다.
     */
    fun tick(nowMs: Long = clock()) {
        val currentPhase = _uiState.value.phase
        val activeConfig = config
        when (currentPhase) {
            ScanPhase.BASELINE -> {
                val totalSec = _uiState.value.baselineTotalDurationSec.coerceAtLeast(1)
                val elapsedSec = ((nowMs - baselineStartMs) / 1000L).toInt().coerceAtLeast(0)
                val remaining = (totalSec - elapsedSec).coerceAtLeast(0)
                if (remaining == 0) {
                    startMonitoring()
                } else {
                    _uiState.update { it.copy(baselineRemainingSec = remaining) }
                }
            }

            ScanPhase.MONITORING -> {
                val elapsedMonitoring = if (monitoringStartMs > 0L) {
                    ((nowMs - monitoringStartMs) / 1000L).coerceAtLeast(0L)
                } else 0L
                _uiState.update { it.copy(monitoringElapsedSec = elapsedMonitoring) }

                if (nowMs - lastWindowEvalMs >= activeConfig.monitoringWindowMs) {
                    evaluateMonitoringWindow(nowMs)
                    lastWindowEvalMs = nowMs
                }
                detectPowerOffDevices(nowMs)
            }

            ScanPhase.RADAR_LOCATING -> {
                val elapsedMonitoring = if (monitoringStartMs > 0L) {
                    ((nowMs - monitoringStartMs) / 1000L).coerceAtLeast(0L)
                } else 0L
                _uiState.update { it.copy(monitoringElapsedSec = elapsedMonitoring) }
            }

            ScanPhase.IDLE -> Unit
        }
    }

    /**
     * 5초 윈도우 버퍼에 모인 기기들을 즉시 평가하도록 강제한다 (테스트 또는 즉시 갱신용).
     */
    fun flushMonitoringWindow(nowMs: Long = clock()) {
        if (_uiState.value.phase == ScanPhase.MONITORING) {
            evaluateMonitoringWindow(nowMs)
            lastWindowEvalMs = nowMs
            detectPowerOffDevices(nowMs)
        }
    }

    private fun processSignal(
        fp: FingerprintResult, type: DeviceType, rawRssi: Int, timestampMs: Long
    ) {
        val currentPhase = _uiState.value.phase
        if (currentPhase == ScanPhase.IDLE) return

        val cluster = clusterer.resolveCluster(
            baseFingerprint = fp.fingerprint,
            isGenericUnknown = fp.isGenericUnknown,
            rawRssi = rawRssi,
            timestampMs = timestampMs
        )

        val device = DetectedDevice(
            baseFingerprint = fp.fingerprint,
            clusterKey = cluster.clusterKey,
            type = type,
            displayName = fp.displayName,
            manufacturer = fp.manufacturer,
            rawRssi = rawRssi,
            filteredRssi = cluster.filteredRssi,
            advIntervalMs = cluster.estimatedIntervalMs,
            isSuspiciousAudioOrWearable = fp.isSuspiciousAudioOrWearable,
            lastSeenMillis = timestampMs
        )

        _uiState.update {
            it.copy(
                totalPacketsProcessed = it.totalPacketsProcessed + 1,
                blePacketsProcessed = it.blePacketsProcessed + if (type == DeviceType.BLE) 1L else 0L,
                wifiPacketsProcessed = it.wifiPacketsProcessed + if (type == DeviceType.WIFI) 1L else 0L
            )
        }

        when (currentPhase) {
            ScanPhase.BASELINE -> handleBaselineSignal(device)
            ScanPhase.MONITORING -> handleMonitoringSignal(device, timestampMs)
            ScanPhase.RADAR_LOCATING -> handleRadarSignal(device)
            ScanPhase.IDLE -> Unit
        }
    }

    private fun handleBaselineSignal(device: DetectedDevice) {
        val key = device.clusterKey
        val prevMax = baselineMap[key] ?: -120
        if (device.rawRssi > prevMax) {
            baselineMap[key] = device.rawRssi
        }
        baselineDevices[key] = device

        _uiState.update {
            it.copy(
                baselineDeviceCount = baselineMap.size,
                baselinePreviewDevices = baselineDevices.values.sortedByDescending { d -> d.rawRssi }.take(20))
        }
    }

    private fun handleMonitoringSignal(device: DetectedDevice, timestampMs: Long) {
        val key = device.clusterKey
        val obsWindowMs = config.observationWindowMs
        latestDevices[key] = device
        recordedPowerOffKeys.remove(key) // 다시 켜졌으면 전원 꺼짐 상태 해제

        // 15초 슬라이딩 윈도우 관측 횟수 기록
        val queue = observationTimestamps.getOrPut(key) { ArrayDeque() }
        queue.addLast(timestampMs)
        while (queue.isNotEmpty() && timestampMs - queue.first() > obsWindowMs) {
            queue.removeFirst()
        }

        // 5초 윈도우 버퍼에 해당 윈도우 내 최고 RSSI 유지
        val buffered = windowBuffer[key]
        if (buffered == null || device.rawRssi >= buffered.rawRssi) {
            windowBuffer[key] = device
        }
    }

    private fun evaluateMonitoringWindow(nowMs: Long) {
        if (windowBuffer.isEmpty()) return
        val activeConfig = config

        for ((key, device) in windowBuffer) {
            val queue = observationTimestamps[key]
            while (queue != null && queue.isNotEmpty() && nowMs - queue.first() > activeConfig.observationWindowMs) {
                queue.removeFirst()
            }
            val countIn15s = queue?.size ?: 0
            val baselineMaxRssi = baselineMap[key]

            val isNewTrigger =
                baselineMaxRssi == null && countIn15s >= activeConfig.newDeviceMinObservations && device.rawRssi > activeConfig.newDeviceMinRssi

            val isSurgeTrigger =
                baselineMaxRssi != null && (device.rawRssi - baselineMaxRssi) >= activeConfig.surgeDeltaDbm

            if (isNewTrigger || isSurgeTrigger) {
                val reason = if (isNewTrigger) AlertReason.NEW_DEVICE else AlertReason.SUDDEN_APPROACH
                val risk = RiskScorer.calculate(device, reason, countIn15s)
                val existing = activeAlerts[key]
                activeAlerts[key] = SuspiciousAlert(
                    device = device,
                    reason = reason,
                    riskScore = risk,
                    observationCountIn15s = countIn15s,
                    baselineMaxRssi = baselineMaxRssi,
                    firstDetectedAt = existing?.firstDetectedAt ?: nowMs,
                    lastDetectedAt = nowMs
                )
            }
        }

        windowBuffer.clear()
        emitAlerts()
    }

    private fun detectPowerOffDevices(nowMs: Long) {
        val timeoutMs = config.powerOffTimeoutMs
        var added = false
        for ((key, device) in latestDevices) {
            // 베이스라인에 없던 신규 기기 또는 이미 의심 경고가 떴던 기기가 갑자기 사라진 경우
            val wasTracked = baselineMap[key] == null || activeAlerts.containsKey(key)
            if (!wasTracked) continue
            if (recordedPowerOffKeys.contains(key)) continue

            if (nowMs - device.lastSeenMillis >= timeoutMs) {
                recordedPowerOffKeys.add(key)
                powerOffHistory.add(
                    0, PowerOffEvent(
                        clusterKey = key,
                        displayName = device.displayName,
                        manufacturer = device.manufacturer,
                        type = device.type,
                        lastRssi = device.rawRssi,
                        disappearedAtMillis = nowMs
                    )
                )
                added = true
            }
        }
        if (added) {
            _uiState.update { it.copy(powerOffEvents = powerOffHistory.toList()) }
        }
    }

    private fun handleRadarSignal(device: DetectedDevice) {
        if (device.clusterKey != radarTargetKey) return
        radarRssiHistory.addLast(device.rawRssi)
        while (radarRssiHistory.size > MAX_RADAR_HISTORY) {
            radarRssiHistory.removeFirst()
        }
        val historySnapshot = radarRssiHistory.toList()
        _uiState.update { state ->
            val prevPeak = state.radarTarget?.peakRssi ?: device.rawRssi
            val newPeak = maxOf(prevPeak, device.rawRssi)
            state.copy(
                radarTarget = RadarTargetState(
                    targetDevice = device,
                    currentRawRssi = device.rawRssi,
                    currentFilteredRssi = device.filteredRssi,
                    peakRssi = newPeak,
                    proximityZone = resolveProximity(device.rawRssi),
                    lastUpdatedMillis = device.lastSeenMillis,
                    rssiHistory = historySnapshot
                )
            )
        }
    }

    private fun emitAlerts() {
        val sorted =
            activeAlerts.values.sortedWith(compareByDescending<SuspiciousAlert> { it.riskScore }.thenByDescending { it.device.rawRssi })
        _uiState.update { it.copy(suspiciousAlerts = sorted) }
    }

    private fun resolveProximity(rssi: Int): ProximityZone = when {
        rssi >= config.radarVeryCloseRssi -> ProximityZone.VERY_CLOSE
        rssi >= config.radarNearRssi -> ProximityZone.NEAR
        else -> ProximityZone.FAR
    }
}
