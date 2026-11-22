package argus.data.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import argus.domain.model.ProximityZone

/**
 * Phase 3 RADAR LOCATING 모드에서 RSSI 근접도에 따라 비프음 및 진동 피드백을 출력한다.
 */
class RadarFeedbackController(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
    }.getOrNull()

    private var lastFeedbackAtMs: Long = 0L

    fun onProximityPulse(rssi: Int, zone: ProximityZone, soundEnabled: Boolean, hapticEnabled: Boolean) {
        val now = System.currentTimeMillis()
        val intervalMs = when (zone) {
            ProximityZone.VERY_CLOSE -> 220L
            ProximityZone.NEAR -> 550L
            ProximityZone.FAR -> 1100L
        }

        if (now - lastFeedbackAtMs < intervalMs) return
        lastFeedbackAtMs = now

        if (soundEnabled) {
            val tone = when (zone) {
                ProximityZone.VERY_CLOSE -> ToneGenerator.TONE_PROP_BEEP2
                ProximityZone.NEAR -> ToneGenerator.TONE_PROP_BEEP
                ProximityZone.FAR -> ToneGenerator.TONE_PROP_ACK
            }
            val durationMs = if (zone == ProximityZone.VERY_CLOSE) 110 else 65
            runCatching { toneGenerator?.startTone(tone, durationMs) }
        }

        if (hapticEnabled) {
            val vibMs = when (zone) {
                ProximityZone.VERY_CLOSE -> 100L
                ProximityZone.NEAR -> 55L
                ProximityZone.FAR -> 25L
            }
            val amplitude = when {
                rssi >= -45 -> 255
                rssi >= -65 -> 160
                else -> 80
            }
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(vibMs, amplitude))
            }
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }
}
