package argus.data.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import argus.domain.model.RawWifiSignal

/**
 * Android WifiManager 기반 주기적 Wi-Fi 스캐너.
 *
 * 참고: Android 9+에서는 개발자 옵션의 'Wi-Fi scan throttling'을 꺼야 5초 간격 실시간 스캔이 원활히 동작한다.
 */
class AndroidWifiScanner(
    private val context: Context,
    private val onSignal: (RawWifiSignal) -> Unit,
    private val onThrottledWarning: (Boolean) -> Unit = {}
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var isRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            onThrottledWarning(!updated)

            val results = runCatching { wifiManager?.scanResults }.getOrNull() ?: return
            val now = System.currentTimeMillis()
            for (sr in results) {
                @Suppress("DEPRECATION") val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sr.wifiSsid?.toString()?.removeSurrounding("\"") ?: sr.SSID
                } else {
                    sr.SSID
                }
                onSignal(
                    RawWifiSignal(
                        ssid = ssid,
                        bssid = sr.BSSID,
                        capabilities = sr.capabilities.orEmpty(),
                        frequencyMhz = sr.frequency,
                        rssi = sr.level,
                        timestampMs = now
                    )
                )
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(scanReceiver, filter)
        }
        isRegistered = true
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun triggerScan() {
        runCatching {
            val started = wifiManager?.startScan() ?: false
            if (!started) {
                onThrottledWarning(true)
            }
        }
    }

    fun unregister() {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(scanReceiver) }
        isRegistered = false
    }
}
