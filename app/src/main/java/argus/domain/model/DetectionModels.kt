package argus.domain.model

enum class ScanPhase {
    IDLE, BASELINE, MONITORING, RADAR_LOCATING
}

enum class DeviceType {
    BLE, WIFI
}

/**
 * Android BluetoothLeScanner ScanResult로부터 추출한 플랫폼 독립적 BLE 원시 신호.
 */
data class RawBleSignal(
    val deviceName: String?,
    val serviceUuids16Bit: List<String>,
    val companyId: Int?,
    val manufacturerData: ByteArray?,
    val rssi: Int,
    val timestampMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawBleSignal) return false
        return deviceName == other.deviceName && serviceUuids16Bit == other.serviceUuids16Bit && companyId == other.companyId && manufacturerData.contentEquals(
            other.manufacturerData
        ) && rssi == other.rssi && timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = deviceName.hashCode()
        result = 31 * result + serviceUuids16Bit.hashCode()
        result = 31 * result + (companyId ?: 0)
        result = 31 * result + (manufacturerData?.contentHashCode() ?: 0)
        result = 31 * result + rssi
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

/**
 * Android WifiManager ScanResult로부터 추출한 플랫폼 독립적 Wi-Fi 원시 신호.
 */
data class RawWifiSignal(
    val ssid: String?,
    val bssid: String?,
    val capabilities: String,
    val frequencyMhz: Int,
    val rssi: Int,
    val timestampMs: Long
)

/**
 * 지문 생성 및 Kalman 필터링이 적용된 기기 스냅샷.
 */
data class DetectedDevice(
    val baseFingerprint: String,
    val clusterKey: String,
    val type: DeviceType,
    val displayName: String,
    val manufacturer: String,
    val rawRssi: Int,
    val filteredRssi: Double,
    val advIntervalMs: Long?,
    val isSuspiciousAudioOrWearable: Boolean,
    val lastSeenMillis: Long
)

enum class AlertReason {
    NEW_DEVICE, SUDDEN_APPROACH
}

/**
 * Phase 2 모니터링 중 이상 징후로 판별된 의심 기기 항목.
 */
data class SuspiciousAlert(
    val device: DetectedDevice,
    val reason: AlertReason,
    val riskScore: Int,
    val observationCountIn15s: Int,
    val baselineMaxRssi: Int?,
    val firstDetectedAt: Long,
    val lastDetectedAt: Long
)

/**
 * 시험 시작 직후 관측되었다가 사라진 '전원 차단 의심' 로그 항목.
 */
data class PowerOffEvent(
    val clusterKey: String,
    val displayName: String,
    val manufacturer: String,
    val type: DeviceType,
    val lastRssi: Int,
    val disappearedAtMillis: Long
)

enum class ProximityZone {
    FAR,        // < -65 dBm
    NEAR,       // -65 dBm .. -46 dBm
    VERY_CLOSE  // >= -45 dBm (좌석 특정 임계치)
}

/**
 * Phase 3 레이더 추적 모드 상태.
 */
data class RadarTargetState(
    val targetDevice: DetectedDevice,
    val currentRawRssi: Int,
    val currentFilteredRssi: Double,
    val peakRssi: Int,
    val proximityZone: ProximityZone,
    val lastUpdatedMillis: Long,
    val rssiHistory: List<Int> = emptyList()
)

/**
 * UI에서 구독하는 전체 탐지 엔진 상태.
 */
data class DetectorUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val baselineRemainingSec: Int = 60,
    val baselineTotalDurationSec: Int = 60,
    val baselineDeviceCount: Int = 0,
    val baselinePreviewDevices: List<DetectedDevice> = emptyList(),
    val suspiciousAlerts: List<SuspiciousAlert> = emptyList(),
    val powerOffEvents: List<PowerOffEvent> = emptyList(),
    val radarTarget: RadarTargetState? = null,
    val totalPacketsProcessed: Long = 0L,
    val blePacketsProcessed: Long = 0L,
    val wifiPacketsProcessed: Long = 0L,
    val sessionStartedAtMillis: Long = 0L,
    val monitoringElapsedSec: Long = 0L
)

data class DetectorConfig(
    val baselineDurationSec: Int = 60,
    val noiseFloorRssi: Int = -80,
    val monitoringWindowMs: Long = 5_000L,
    val observationWindowMs: Long = 15_000L,
    val newDeviceMinObservations: Int = 3,
    val newDeviceMinRssi: Int = -68,
    val surgeDeltaDbm: Int = 15,
    val powerOffTimeoutMs: Long = 15_000L,
    val radarVeryCloseRssi: Int = -45,
    val radarNearRssi: Int = -65
)
