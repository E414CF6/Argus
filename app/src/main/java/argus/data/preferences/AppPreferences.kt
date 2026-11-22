package argus.data.preferences

import android.content.Context
import android.content.res.Configuration
import argus.domain.model.DetectorConfig
import java.util.Locale

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    KOREAN("ko"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag) {
            "ko" -> KOREAN
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            runCatching { if (name != null) valueOf(name) else SYSTEM }.getOrDefault(SYSTEM)
    }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val enableBleScan: Boolean = true,
    val enableWifiScan: Boolean = true,
    val wifiScanIntervalSec: Int = 5,
    val autoSaveSession: Boolean = true,
    val detectorConfig: DetectorConfig = DetectorConfig()
)

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val defaults = DetectorConfig()
        val config = DetectorConfig(
            baselineDurationSec = prefs.getInt(KEY_BASELINE_SEC, defaults.baselineDurationSec),
            noiseFloorRssi = prefs.getInt(KEY_NOISE_FLOOR_RSSI, defaults.noiseFloorRssi),
            newDeviceMinRssi = prefs.getInt(KEY_NEW_DEVICE_MIN_RSSI, defaults.newDeviceMinRssi),
            surgeDeltaDbm = prefs.getInt(KEY_SURGE_DELTA_DBM, defaults.surgeDeltaDbm),
            powerOffTimeoutMs = prefs.getLong(KEY_POWER_OFF_TIMEOUT_MS, defaults.powerOffTimeoutMs),
            radarVeryCloseRssi = prefs.getInt(KEY_RADAR_SEAT_LOCK_RSSI, defaults.radarVeryCloseRssi)
        )
        return AppSettings(
            language = getLanguage(),
            themeMode = getThemeMode(),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            soundEnabled = isSoundEnabled(),
            hapticEnabled = isHapticEnabled(),
            enableBleScan = prefs.getBoolean(KEY_ENABLE_BLE, true),
            enableWifiScan = prefs.getBoolean(KEY_ENABLE_WIFI, true),
            wifiScanIntervalSec = prefs.getInt(KEY_WIFI_INTERVAL_SEC, 5),
            autoSaveSession = prefs.getBoolean(KEY_AUTO_SAVE, true),
            detectorConfig = config
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_LANGUAGE, settings.language.tag)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putBoolean(KEY_HAPTIC, settings.hapticEnabled)
            .putBoolean(KEY_ENABLE_BLE, settings.enableBleScan)
            .putBoolean(KEY_ENABLE_WIFI, settings.enableWifiScan)
            .putInt(KEY_WIFI_INTERVAL_SEC, settings.wifiScanIntervalSec)
            .putBoolean(KEY_AUTO_SAVE, settings.autoSaveSession)
            .putInt(KEY_BASELINE_SEC, settings.detectorConfig.baselineDurationSec)
            .putInt(KEY_NOISE_FLOOR_RSSI, settings.detectorConfig.noiseFloorRssi)
            .putInt(KEY_NEW_DEVICE_MIN_RSSI, settings.detectorConfig.newDeviceMinRssi)
            .putInt(KEY_SURGE_DELTA_DBM, settings.detectorConfig.surgeDeltaDbm)
            .putLong(KEY_POWER_OFF_TIMEOUT_MS, settings.detectorConfig.powerOffTimeoutMs)
            .putInt(KEY_RADAR_SEAT_LOCK_RSSI, settings.detectorConfig.radarVeryCloseRssi)
            .apply()
    }

    fun resetToDefaults(): AppSettings {
        val currentLang = getLanguage()
        val defaults = AppSettings(language = currentLang)
        saveSettings(defaults)
        return defaults
    }

    fun getLanguage(): AppLanguage =
        AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    fun getThemeMode(): ThemeMode =
        ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND, true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun isHapticEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC, true)

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "argus_prefs"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SOUND = "radar_sound"
        private const val KEY_HAPTIC = "radar_haptic"
        private const val KEY_ENABLE_BLE = "enable_ble_scan"
        private const val KEY_ENABLE_WIFI = "enable_wifi_scan"
        private const val KEY_WIFI_INTERVAL_SEC = "wifi_scan_interval_sec"
        private const val KEY_AUTO_SAVE = "auto_save_session"
        private const val KEY_BASELINE_SEC = "detector_baseline_sec"
        private const val KEY_NOISE_FLOOR_RSSI = "detector_noise_floor_rssi"
        private const val KEY_NEW_DEVICE_MIN_RSSI = "detector_new_device_min_rssi"
        private const val KEY_SURGE_DELTA_DBM = "detector_surge_delta_dbm"
        private const val KEY_POWER_OFF_TIMEOUT_MS = "detector_power_off_timeout_ms"
        private const val KEY_RADAR_SEAT_LOCK_RSSI = "detector_radar_seat_lock_rssi"

        fun wrapContextWithLanguage(base: Context): Context {
            val lang = AppPreferences(base).getLanguage()
            val tag = lang.tag ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
