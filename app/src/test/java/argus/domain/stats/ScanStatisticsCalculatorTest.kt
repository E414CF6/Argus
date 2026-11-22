package argus.domain.stats

import argus.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanStatisticsCalculatorTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    private fun makeAlert(
        key: String, name: String, mfr: String, type: DeviceType, reason: AlertReason, risk: Int, audio: Boolean
    ) = SuspiciousAlertRecord(
        clusterKey = key,
        displayName = name,
        manufacturer = mfr,
        type = type,
        reason = reason,
        riskScore = risk,
        rawRssi = -55,
        filteredRssi = -56.0,
        baselineMaxRssi = if (reason == AlertReason.SUDDEN_APPROACH) -74 else null,
        observationCountIn15s = 4,
        isSuspiciousAudioOrWearable = audio,
        firstDetectedAt = 1000L,
        lastDetectedAt = 2000L
    )

    @Test
    fun `Statistics calculator aggregates KPIs, breakdowns, manufacturers, and timeframe filtering`() {
        val now = 1_800_000_000_000L
        val recentSession = ScanSessionRecord(
            id = "recent",
            title = "Hall A",
            startedAtMillis = now - 2 * dayMs,
            endedAtMillis = now - 2 * dayMs + 300_000L,
            durationSec = 300L,
            baselineDurationSec = 60,
            baselineDeviceCount = 5,
            totalPacketsProcessed = 500L,
            blePacketsProcessed = 450L,
            wifiPacketsProcessed = 50L,
            baselineDevices = emptyList(),
            suspiciousAlerts = listOf(
                makeAlert("k1", "AirPods", "Apple", DeviceType.BLE, AlertReason.NEW_DEVICE, 85, true),
                makeAlert("k2", "Galaxy Watch", "Samsung", DeviceType.BLE, AlertReason.SUDDEN_APPROACH, 55, true)
            ),
            powerOffEvents = listOf(
                PowerOffEvent("k1", "AirPods", "Apple", DeviceType.BLE, -55, now - 2 * dayMs + 200_000L)
            ),
            source = SessionSource.LOCAL
        )

        val oldSession = ScanSessionRecord(
            id = "old",
            title = "Hall B",
            startedAtMillis = now - 20 * dayMs,
            endedAtMillis = now - 20 * dayMs + 600_000L,
            durationSec = 600L,
            baselineDurationSec = 60,
            baselineDeviceCount = 8,
            totalPacketsProcessed = 800L,
            blePacketsProcessed = 700L,
            wifiPacketsProcessed = 100L,
            baselineDevices = emptyList(),
            suspiciousAlerts = listOf(
                makeAlert("k1", "AirPods", "Apple", DeviceType.BLE, AlertReason.NEW_DEVICE, 90, true),
                makeAlert("k3", "Student iPhone", "OUI_A4:83:E7", DeviceType.WIFI, AlertReason.NEW_DEVICE, 35, true)
            ),
            powerOffEvents = emptyList(),
            source = SessionSource.IMPORTED,
            importedAtMillis = now - dayMs
        )

        val allStats = ScanStatisticsCalculator.calculate(
            sessions = listOf(recentSession, oldSession), timeframe = StatsTimeframe.ALL, nowMillis = now
        )

        assertThat(allStats.totalSessions).isEqualTo(2)
        assertThat(allStats.localSessions).isEqualTo(1)
        assertThat(allStats.importedSessions).isEqualTo(1)
        assertThat(allStats.totalDurationSec).isEqualTo(900L)
        assertThat(allStats.totalAlerts).isEqualTo(4)
        assertThat(allStats.highRiskAlertCount).isEqualTo(2)
        assertThat(allStats.mediumRiskAlertCount).isEqualTo(1)
        assertThat(allStats.lowRiskAlertCount).isEqualTo(1)
        assertThat(allStats.bleAlertCount).isEqualTo(3)
        assertThat(allStats.wifiAlertCount).isEqualTo(1)
        assertThat(allStats.newDeviceAlertCount).isEqualTo(3)
        assertThat(allStats.suddenApproachAlertCount).isEqualTo(1)
        assertThat(allStats.totalPowerOffEvents).isEqualTo(1)
        assertThat(allStats.peakRiskScore).isEqualTo(90)
        assertThat(allStats.topManufacturers.first().name).isEqualTo("Apple")
        assertThat(allStats.topManufacturers.first().count).isEqualTo(2)
        assertThat(allStats.topDevices.first().clusterKey).isEqualTo("k1")
        assertThat(allStats.topDevices.first().occurrences).isEqualTo(2)
        assertThat(allStats.sessionTrend).hasSize(2)

        val sevenDayStats = ScanStatisticsCalculator.calculate(
            sessions = listOf(recentSession, oldSession), timeframe = StatsTimeframe.LAST_7_DAYS, nowMillis = now
        )
        assertThat(sevenDayStats.totalSessions).isEqualTo(1)
        assertThat(sevenDayStats.totalAlerts).isEqualTo(2)
        assertThat(sevenDayStats.peakRiskScore).isEqualTo(85)
    }
}
