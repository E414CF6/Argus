package argus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import argus.data.feedback.RadarFeedbackController
import argus.data.history.ScanHistoryRepository
import argus.data.preferences.AppLanguage
import argus.data.preferences.AppPreferences
import argus.data.preferences.AppSettings
import argus.data.preferences.ThemeMode
import argus.data.scanner.AndroidBleScanner
import argus.data.scanner.AndroidWifiScanner
import argus.data.scanner.BleScannerError
import argus.domain.detector.ExamDetectorEngine
import argus.domain.model.DetectorUiState
import argus.domain.model.ImportResult
import argus.domain.model.ScanPhase
import argus.domain.model.ScanSessionRecord
import argus.domain.stats.ScanStatisticsCalculator
import argus.domain.stats.ScanStatisticsSummary
import argus.domain.stats.StatsTimeframe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class AppExtraState(
    val settings: AppSettings = AppSettings(),
    val wifiThrottledWarning: Boolean = false,
    val bleError: BleScannerError? = null,
    val sessionTitleDraft: String = ""
) {
    val soundEnabled: Boolean get() = settings.soundEnabled
    val hapticEnabled: Boolean get() = settings.hapticEnabled
    val language: AppLanguage get() = settings.language
    val themeMode: ThemeMode get() = settings.themeMode
    val keepScreenOn: Boolean get() = settings.keepScreenOn
}

sealed interface UiEvent {
    data object SessionSaved : UiEvent
    data object SessionDeleted : UiEvent
    data object HistoryCleared : UiEvent
    data object SettingsReset : UiEvent
    data class ExportSuccess(val count: Int) : UiEvent
    data class ExportFailed(val reason: String) : UiEvent
    data class ImportSuccess(val result: ImportResult) : UiEvent
    data object ImportFailed : UiEvent
}

class ArgusViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val initialSettings = prefs.loadSettings()

    private val repository = ScanHistoryRepository(
        storageFile = File(application.filesDir, "argus_scan_history.json")
    )

    private val engine = ExamDetectorEngine(initialConfig = initialSettings.detectorConfig)
    val detectorState: StateFlow<DetectorUiState> = engine.uiState

    private val _extraState = MutableStateFlow(AppExtraState(settings = initialSettings))
    val extraState: StateFlow<AppExtraState> = _extraState.asStateFlow()

    private val _sessions = MutableStateFlow<List<ScanSessionRecord>>(emptyList())
    val sessions: StateFlow<List<ScanSessionRecord>> = _sessions.asStateFlow()

    private val _statsTimeframe = MutableStateFlow(StatsTimeframe.ALL)
    val statsTimeframe: StateFlow<StatsTimeframe> = _statsTimeframe.asStateFlow()

    val statistics: StateFlow<ScanStatisticsSummary> = combine(_sessions, _statsTimeframe) { list, tf ->
        ScanStatisticsCalculator.calculate(list, tf)
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = ScanStatisticsSummary()
    )

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val feedbackController = RadarFeedbackController(application)

    private val bleScanner = AndroidBleScanner(context = application, onSignal = { signal ->
        engine.onBleSignal(signal)
        maybeTriggerRadarFeedback()
    }, onError = { err ->
        _extraState.update { it.copy(bleError = err) }
    })

    private val wifiScanner = AndroidWifiScanner(context = application, onSignal = { signal ->
        engine.onWifiSignal(signal)
        maybeTriggerRadarFeedback()
    }, onThrottledWarning = { throttled ->
        _extraState.update { it.copy(wifiThrottledWarning = throttled) }
    })

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.loadAll() }
            _sessions.value = loaded
        }
    }

    fun updateSessionTitleDraft(title: String) {
        _extraState.update { it.copy(sessionTitleDraft = title) }
    }

    fun startBaseline() {
        _extraState.update { it.copy(bleError = null, wifiThrottledWarning = false) }
        engine.startBaseline()
        ensureScanningStarted()
    }

    fun skipToMonitoring() {
        engine.startMonitoring()
        ensureScanningStarted()
    }

    fun enterRadar(clusterKey: String) {
        engine.enterRadarMode(clusterKey)
    }

    fun exitRadar() {
        engine.exitRadarMode()
    }

    fun cancelBaselineWithoutSaving() {
        stopScannersInternal()
        engine.resetToIdle()
    }

    fun stopAndSaveSession(forceSave: Boolean = false) {
        val currentState = detectorState.value
        val currentSettings = _extraState.value.settings
        val draftTitle = _extraState.value.sessionTitleDraft.trim()
        val startMs = currentState.sessionStartedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val defaultTitle = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(startMs))
        val finalTitle = draftTitle.ifEmpty { "Exam · $defaultTitle" }

        val hasData =
            currentState.phase == ScanPhase.MONITORING || currentState.phase == ScanPhase.RADAR_LOCATING || currentState.baselineDeviceCount > 0 || currentState.totalPacketsProcessed > 0L

        val shouldSave = hasData && (forceSave || currentSettings.autoSaveSession)

        val record = if (shouldSave) {
            engine.buildSessionRecord(
                id = UUID.randomUUID().toString(), title = finalTitle
            )
        } else {
            null
        }

        stopScannersInternal()
        engine.resetToIdle()
        _extraState.update { it.copy(sessionTitleDraft = "") }

        if (record != null) {
            viewModelScope.launch {
                val updated = withContext(Dispatchers.IO) { repository.saveSession(record) }
                _sessions.value = updated
                _uiEvents.tryEmit(UiEvent.SessionSaved)
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.renameSession(sessionId, newTitle) }
            _sessions.value = updated
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.deleteSession(sessionId) }
            _sessions.value = updated
            _uiEvents.tryEmit(UiEvent.SessionDeleted)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repository.clearAll() }
            _sessions.value = updated
            _uiEvents.tryEmit(UiEvent.HistoryCleared)
        }
    }

    fun buildExportJson(targetSessions: List<ScanSessionRecord>): String = repository.exportToJson(targetSessions)

    fun buildExportCsv(targetSessions: List<ScanSessionRecord>): String = repository.exportToCsv(targetSessions)

    fun notifyExportSuccess(count: Int) {
        _uiEvents.tryEmit(UiEvent.ExportSuccess(count))
    }

    fun notifyExportFailed(reason: String) {
        _uiEvents.tryEmit(UiEvent.ExportFailed(reason))
    }

    fun importFromJson(jsonString: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { repository.importFromJson(jsonString) }
            }
            outcome.onSuccess { (result, updated) ->
                _sessions.value = updated
                _uiEvents.tryEmit(UiEvent.ImportSuccess(result))
            }.onFailure {
                _uiEvents.tryEmit(UiEvent.ImportFailed)
            }
        }
    }

    fun setStatsTimeframe(timeframe: StatsTimeframe) {
        _statsTimeframe.value = timeframe
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val prev = _extraState.value.settings
        val candidate = transform(prev)
        // Guarantee at least one scan source remains active
        val sanitized = if (!candidate.enableBleScan && !candidate.enableWifiScan) {
            prev
        } else {
            candidate
        }
        prefs.saveSettings(sanitized)
        engine.updateConfig(sanitized.detectorConfig)
        _extraState.update { it.copy(settings = sanitized) }

        // Apply scan source changes immediately if currently scanning
        if (detectorState.value.phase != ScanPhase.IDLE) {
            if (sanitized.enableBleScan) bleScanner.start() else bleScanner.stop()
            if (sanitized.enableWifiScan) {
                wifiScanner.register()
                wifiScanner.triggerScan()
            } else {
                wifiScanner.unregister()
                _extraState.update { it.copy(wifiThrottledWarning = false) }
            }
        }
    }

    fun resetSettings() {
        val defaults = prefs.resetToDefaults()
        engine.updateConfig(defaults.detectorConfig)
        _extraState.update { it.copy(settings = defaults) }
        _uiEvents.tryEmit(UiEvent.SettingsReset)
    }

    fun setLanguage(language: AppLanguage) {
        updateSettings { it.copy(language = language) }
    }

    fun toggleSound() {
        updateSettings { it.copy(soundEnabled = !it.soundEnabled) }
    }

    fun toggleHaptic() {
        updateSettings { it.copy(hapticEnabled = !it.hapticEnabled) }
    }

    fun dismissError() {
        _extraState.update { it.copy(bleError = null) }
    }

    private fun stopScannersInternal() {
        tickerJob?.cancel()
        tickerJob = null
        bleScanner.stop()
        wifiScanner.unregister()
    }

    private fun ensureScanningStarted() {
        val activeSettings = _extraState.value.settings
        if (activeSettings.enableBleScan) {
            bleScanner.start()
        } else {
            bleScanner.stop()
        }
        if (activeSettings.enableWifiScan) {
            wifiScanner.register()
            wifiScanner.triggerScan()
        } else {
            wifiScanner.unregister()
        }

        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            var secCounter = 0
            while (isActive) {
                delay(1_000L)
                engine.tick()
                secCounter++
                val wifiCfg = _extraState.value.settings
                val interval = wifiCfg.wifiScanIntervalSec.coerceAtLeast(3)
                if (wifiCfg.enableWifiScan && secCounter % interval == 0 && detectorState.value.phase != ScanPhase.IDLE) {
                    wifiScanner.triggerScan()
                }
            }
        }
    }

    private fun maybeTriggerRadarFeedback() {
        val state = detectorState.value
        if (state.phase != ScanPhase.RADAR_LOCATING) return
        val target = state.radarTarget ?: return
        val s = _extraState.value.settings
        feedbackController.onProximityPulse(
            rssi = target.currentRawRssi,
            zone = target.proximityZone,
            soundEnabled = s.soundEnabled,
            hapticEnabled = s.hapticEnabled
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopScannersInternal()
        engine.resetToIdle()
        feedbackController.release()
    }
}
