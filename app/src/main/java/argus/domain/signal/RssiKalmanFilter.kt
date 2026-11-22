package argus.domain.signal

import kotlin.math.abs

/**
 * BLE/Wi-Fi RSSI의 멀티패스 노이즈를 완화하기 위한 1D Kalman Filter.
 *
 * @param processNoise Q: 실제 신호 변화 허용치 (작을수록 부드럽고, 클수록 이동에 민감)
 * @param measurementNoise R: 스캔 측정치의 노이즈 분산
 */
class RssiKalmanFilter(
    private val processNoise: Double = 0.08, private val measurementNoise: Double = 4.0
) {
    private var estimate: Double? = null
    private var errorCovariance: Double = 1.0

    fun update(measurement: Double): Double {
        val currentEstimate = estimate
        if (currentEstimate == null) {
            estimate = measurement
            return measurement
        }

        // Prediction step
        val predictedCovariance = errorCovariance + processNoise

        // Measurement update step
        val kalmanGain = predictedCovariance / (predictedCovariance + measurementNoise)
        val newEstimate = currentEstimate + kalmanGain * (measurement - currentEstimate)
        errorCovariance = (1.0 - kalmanGain) * predictedCovariance
        estimate = newEstimate
        return newEstimate
    }

    fun currentEstimate(): Double? = estimate

    fun distanceFromEstimate(measurement: Double): Double {
        val est = estimate ?: return 0.0
        return abs(measurement - est)
    }
}
