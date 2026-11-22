package argus.domain.stats

import argus.domain.model.AlertReason
import argus.domain.model.DeviceType
import argus.domain.model.ScanSessionRecord
import argus.domain.model.SessionSource

enum class StatsTimeframe {
    ALL, LAST_7_DAYS, LAST_30_DAYS
}

data class NamedCount(
    val name: String, val count: Int
)

data class FrequentDeviceStat(
    val clusterKey: String,
    val displayName: String,
    val manufacturer: String,
    val type: DeviceType,
    val occurrences: Int,
    val peakRiskScore: Int
)

data class SessionTrendPoint(
    val sessionId: String,
    val title: String,
    val startedAtMillis: Long,
    val alertCount: Int,
    val highRiskCount: Int,
    val maxRiskScore: Int,
    val durationSec: Long
)

data class ScanStatisticsSummary(
    val totalSessions: Int = 0,
    val localSessions: Int = 0,
    val importedSessions: Int = 0,
    val totalDurationSec: Long = 0L,
    val totalPacketsProcessed: Long = 0L,
    val totalBaselineDevices: Int = 0,
    val totalAlerts: Int = 0,
    val highRiskAlertCount: Int = 0,
    val mediumRiskAlertCount: Int = 0,
    val lowRiskAlertCount: Int = 0,
    val bleAlertCount: Int = 0,
    val wifiAlertCount: Int = 0,
    val audioWearableAlertCount: Int = 0,
    val newDeviceAlertCount: Int = 0,
    val suddenApproachAlertCount: Int = 0,
    val totalPowerOffEvents: Int = 0,
    val avgAlertsPerSession: Double = 0.0,
    val peakRiskScore: Int = 0,
    val avgMaxRiskScore: Double = 0.0,
    val topManufacturers: List<NamedCount> = emptyList(),
    val topDevices: List<FrequentDeviceStat> = emptyList(),
    val sessionTrend: List<SessionTrendPoint> = emptyList()
)

object ScanStatisticsCalculator {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun calculate(
        sessions: List<ScanSessionRecord>, timeframe: StatsTimeframe, nowMillis: Long = System.currentTimeMillis()
    ): ScanStatisticsSummary {
        val cutoffMillis = when (timeframe) {
            StatsTimeframe.ALL -> Long.MIN_VALUE
            StatsTimeframe.LAST_7_DAYS -> nowMillis - (7L * DAY_MS)
            StatsTimeframe.LAST_30_DAYS -> nowMillis - (30L * DAY_MS)
        }

        val filtered = sessions.filter { it.startedAtMillis >= cutoffMillis }
        if (filtered.isEmpty()) return ScanStatisticsSummary()

        val allAlerts = filtered.flatMap { it.suspiciousAlerts }

        val highRisk = allAlerts.count { it.riskScore >= 70 }
        val mediumRisk = allAlerts.count { it.riskScore in 40..69 }
        val lowRisk = allAlerts.count { it.riskScore < 40 }

        val bleAlerts = allAlerts.count { it.type == DeviceType.BLE }
        val wifiAlerts = allAlerts.count { it.type == DeviceType.WIFI }
        val audioWearable = allAlerts.count { it.isSuspiciousAudioOrWearable }
        val newDevice = allAlerts.count { it.reason == AlertReason.NEW_DEVICE }
        val suddenApproach = allAlerts.count { it.reason == AlertReason.SUDDEN_APPROACH }

        val totalPowerOffs = filtered.sumOf { it.powerOffEvents.size }
        val peakRisk = allAlerts.maxOfOrNull { it.riskScore } ?: 0
        val avgMaxRisk = filtered.map { it.maxRiskScore }.average()

        val topManufacturers =
            allAlerts.groupingBy { it.manufacturer }.eachCount().entries.sortedByDescending { it.value }.take(6)
                .map { NamedCount(it.key, it.value) }

        val topDevices = allAlerts.groupBy { it.clusterKey }.values.map { group ->
            val sample = group.maxByOrNull { it.riskScore } ?: group.first()
            FrequentDeviceStat(
                clusterKey = sample.clusterKey,
                displayName = sample.displayName,
                manufacturer = sample.manufacturer,
                type = sample.type,
                occurrences = group.size,
                peakRiskScore = group.maxOf { it.riskScore })
        }.sortedWith(compareByDescending<FrequentDeviceStat> { it.occurrences }.thenByDescending { it.peakRiskScore })
            .take(5)

        val trend = filtered.sortedBy { it.startedAtMillis }.takeLast(10).map { s ->
            SessionTrendPoint(
                sessionId = s.id,
                title = s.title,
                startedAtMillis = s.startedAtMillis,
                alertCount = s.suspiciousAlerts.size,
                highRiskCount = s.highRiskAlertCount,
                maxRiskScore = s.maxRiskScore,
                durationSec = s.durationSec
            )
        }

        return ScanStatisticsSummary(
            totalSessions = filtered.size,
            localSessions = filtered.count { it.source == SessionSource.LOCAL },
            importedSessions = filtered.count { it.source == SessionSource.IMPORTED },
            totalDurationSec = filtered.sumOf { it.durationSec },
            totalPacketsProcessed = filtered.sumOf { it.totalPacketsProcessed },
            totalBaselineDevices = filtered.sumOf { it.baselineDeviceCount },
            totalAlerts = allAlerts.size,
            highRiskAlertCount = highRisk,
            mediumRiskAlertCount = mediumRisk,
            lowRiskAlertCount = lowRisk,
            bleAlertCount = bleAlerts,
            wifiAlertCount = wifiAlerts,
            audioWearableAlertCount = audioWearable,
            newDeviceAlertCount = newDevice,
            suddenApproachAlertCount = suddenApproach,
            totalPowerOffEvents = totalPowerOffs,
            avgAlertsPerSession = allAlerts.size.toDouble() / filtered.size.toDouble(),
            peakRiskScore = peakRisk,
            avgMaxRiskScore = avgMaxRisk,
            topManufacturers = topManufacturers,
            topDevices = topDevices,
            sessionTrend = trend
        )
    }
}
