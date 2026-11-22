package argus.domain.detector

import argus.domain.model.AlertReason
import argus.domain.model.DetectedDevice

/**
 * 탐지된 이상 징후 기기의 위험도 점수(0~100)를 산정한다.
 */
object RiskScorer {

    fun calculate(
        device: DetectedDevice, reason: AlertReason, observationCountIn15s: Int
    ): Int {
        var score = 0

        // 1. 오디오/웨어러블/핫스팟 시그니처 (+35)
        if (device.isSuspiciousAudioOrWearable) {
            score += 35
        }

        // 2. 탐지 사유 가중치 (+25 / +20)
        score += when (reason) {
            AlertReason.NEW_DEVICE -> 25
            AlertReason.SUDDEN_APPROACH -> 20
        }

        // 3. 신호 세기 가중치 (-75 dBm -> 0점, -45 dBm 이상 -> 30점)
        val rssiNormalized = ((device.rawRssi + 75).coerceIn(0, 30) / 30.0 * 30.0).toInt()
        score += rssiNormalized

        // 4. 최근 15초 내 관측 빈도 (+10 max)
        score += (observationCountIn15s.coerceAtMost(5) * 2)

        return score.coerceIn(0, 100)
    }
}
