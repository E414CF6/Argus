package argus.domain.model

enum class SessionSource {
    LOCAL, IMPORTED
}

data class BaselineDeviceRecord(
    val clusterKey: String,
    val displayName: String,
    val manufacturer: String,
    val type: DeviceType,
    val maxRssi: Int,
    val isSuspiciousAudioOrWearable: Boolean
)

data class SuspiciousAlertRecord(
    val clusterKey: String,
    val displayName: String,
    val manufacturer: String,
    val type: DeviceType,
    val reason: AlertReason,
    val riskScore: Int,
    val rawRssi: Int,
    val filteredRssi: Double,
    val baselineMaxRssi: Int?,
    val observationCountIn15s: Int,
    val isSuspiciousAudioOrWearable: Boolean,
    val firstDetectedAt: Long,
    val lastDetectedAt: Long
)

data class ScanSessionRecord(
    val id: String,
    val title: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSec: Long,
    val baselineDurationSec: Int,
    val baselineDeviceCount: Int,
    val totalPacketsProcessed: Long,
    val blePacketsProcessed: Long,
    val wifiPacketsProcessed: Long,
    val baselineDevices: List<BaselineDeviceRecord>,
    val suspiciousAlerts: List<SuspiciousAlertRecord>,
    val powerOffEvents: List<PowerOffEvent>,
    val source: SessionSource = SessionSource.LOCAL,
    val importedAtMillis: Long? = null
) {
    val maxRiskScore: Int
        get() = suspiciousAlerts.maxOfOrNull { it.riskScore } ?: 0

    val highRiskAlertCount: Int
        get() = suspiciousAlerts.count { it.riskScore >= 70 }

    val bleAlertCount: Int
        get() = suspiciousAlerts.count { it.type == DeviceType.BLE }

    val wifiAlertCount: Int
        get() = suspiciousAlerts.count { it.type == DeviceType.WIFI }
}

data class ExportBundle(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long,
    val appVersion: String,
    val sessions: List<ScanSessionRecord>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class ImportResult(
    val importedCount: Int, val skippedDuplicateCount: Int, val totalInFile: Int
)
