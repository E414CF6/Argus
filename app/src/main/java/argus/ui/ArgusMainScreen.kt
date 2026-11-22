package argus.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import argus.R
import argus.data.scanner.BleScannerError
import argus.domain.model.*
import argus.ui.components.AnimatedRadarScope
import argus.ui.components.RssiSparklineCard
import argus.ui.components.formatDuration
import argus.ui.components.formatTimeOnly
import argus.ui.theme.LocalArgusColors

enum class MainTab {
    SCANNER, HISTORY, STATISTICS
}

private enum class AlertFilter {
    ALL, HIGH_RISK, AUDIO_WEARABLE, BLE, WIFI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArgusMainScreen(viewModel: ArgusViewModel) {
    val state by viewModel.detectorState.collectAsState()
    val extra by viewModel.extraState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val stats by viewModel.statistics.collectAsState()
    val statsTimeframe by viewModel.statsTimeframe.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(MainTab.SCANNER) }
    var showSettingsModal by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            val message = when (event) {
                UiEvent.SessionSaved -> context.getString(R.string.msg_session_saved)
                UiEvent.SessionDeleted -> context.getString(R.string.msg_session_deleted)
                UiEvent.HistoryCleared -> context.getString(R.string.msg_history_cleared)
                UiEvent.SettingsReset -> context.getString(R.string.msg_settings_reset)
                is UiEvent.ExportSuccess -> context.getString(R.string.msg_export_success, event.count)
                is UiEvent.ExportFailed -> context.getString(R.string.msg_export_failed, event.reason)
                is UiEvent.ImportSuccess -> context.getString(
                    R.string.msg_import_success, event.result.importedCount, event.result.skippedDuplicateCount
                )

                UiEvent.ImportFailed -> context.getString(R.string.msg_import_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val isRadarFullScreen = currentTab == MainTab.SCANNER && state.phase == ScanPhase.RADAR_LOCATING

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isRadarFullScreen) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ), title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = phaseSubtitle(state),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.phase == ScanPhase.IDLE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }, actions = {
                    if (state.phase != ScanPhase.IDLE) {
                        FilledTonalButton(
                            onClick = { viewModel.stopAndSaveSession() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_stop_scan),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    IconButton(onClick = { showSettingsModal = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_open_settings)
                        )
                    }
                })
            }
        },
        bottomBar = {
            if (!isRadarFullScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.SCANNER,
                        onClick = { currentTab = MainTab.SCANNER },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (state.phase != ScanPhase.IDLE && currentTab != MainTab.SCANNER) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                }) {
                                Icon(Icons.Default.Radar, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(R.string.nav_scanner)) })
                    NavigationBarItem(
                        selected = currentTab == MainTab.HISTORY,
                        onClick = { currentTab = MainTab.HISTORY },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (sessions.isNotEmpty()) {
                                        Badge { Text("${sessions.size.coerceAtMost(99)}") }
                                    }
                                }) {
                                Icon(Icons.Default.History, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(R.string.nav_history)) })
                    NavigationBarItem(
                        selected = currentTab == MainTab.STATISTICS,
                        onClick = { currentTab = MainTab.STATISTICS },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_stats)) })
                }
            }
        }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (currentTab == MainTab.SCANNER) {
                if (extra.wifiThrottledWarning && state.phase != ScanPhase.IDLE) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.wifi_throttle_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                }) {
                                Text(stringResource(R.string.action_settings))
                            }
                        }
                    }
                }

                extra.bleError?.let { err ->
                    val errText = when (err) {
                        BleScannerError.BleUnavailable -> stringResource(R.string.error_ble_unavailable)
                        is BleScannerError.ScanFailed -> stringResource(R.string.error_ble_scan_failed, err.errorCode)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissError() }) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                }
            }

            when (currentTab) {
                MainTab.SCANNER -> {
                    when (state.phase) {
                        ScanPhase.IDLE -> IdlePhaseView(
                            sessionLabelDraft = extra.sessionTitleDraft,
                            baselineDurationSec = extra.settings.detectorConfig.baselineDurationSec,
                            savedSessionsCount = sessions.size,
                            onLabelChange = { viewModel.updateSessionTitleDraft(it) },
                            onStartBaseline = { viewModel.startBaseline() },
                            onOpenHistory = { currentTab = MainTab.HISTORY })

                        ScanPhase.BASELINE -> BaselinePhaseView(
                            state = state,
                            onSkipToMonitoring = { viewModel.skipToMonitoring() },
                            onCancel = { viewModel.cancelBaselineWithoutSaving() })

                        ScanPhase.MONITORING -> MonitoringPhaseView(
                            state = state,
                            onSelectRadarTarget = { viewModel.enterRadar(it) },
                            onFinishAndSave = { viewModel.stopAndSaveSession(forceSave = true) })

                        ScanPhase.RADAR_LOCATING -> {
                            val radar = state.radarTarget
                            if (radar != null) {
                                RadarPhaseView(
                                    radar = radar,
                                    seatLockRssi = extra.settings.detectorConfig.radarVeryCloseRssi,
                                    soundEnabled = extra.soundEnabled,
                                    hapticEnabled = extra.hapticEnabled,
                                    onToggleSound = { viewModel.toggleSound() },
                                    onToggleHaptic = { viewModel.toggleHaptic() },
                                    onBack = { viewModel.exitRadar() })
                            }
                        }
                    }
                }

                MainTab.HISTORY -> HistoryScreen(
                    sessions = sessions,
                    onBuildExportJson = { viewModel.buildExportJson(it) },
                    onBuildExportCsv = { viewModel.buildExportCsv(it) },
                    onExportSuccess = { viewModel.notifyExportSuccess(it) },
                    onExportFailed = { viewModel.notifyExportFailed(it) },
                    onImportJson = { viewModel.importFromJson(it) },
                    onRenameSession = { id, title -> viewModel.renameSession(id, title) },
                    onDeleteSession = { viewModel.deleteSession(it) })

                MainTab.STATISTICS -> StatisticsScreen(
                    stats = stats, timeframe = statsTimeframe, onSelectTimeframe = { viewModel.setStatsTimeframe(it) })
            }
        }
    }

    if (showSettingsModal) {
        SettingsModalSheet(
            settings = extra.settings,
            savedSessionCount = sessions.size,
            onDismiss = { showSettingsModal = false },
            onSelectLanguage = { lang ->
                if (lang != extra.language) {
                    viewModel.setLanguage(lang)
                    (context as? Activity)?.recreate()
                }
            },
            onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
            onResetSettings = { viewModel.resetSettings() },
            onClearAllHistory = { viewModel.clearAllHistory() })
    }
}

@Composable
private fun IdlePhaseView(
    sessionLabelDraft: String,
    baselineDurationSec: Int,
    savedSessionsCount: Int,
    onLabelChange: (String) -> Unit,
    onStartBaseline: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.idle_hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.idle_hero_desc_sec, baselineDurationSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = sessionLabelDraft,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.idle_session_label_hint)) },
                    placeholder = { Text(stringResource(R.string.idle_session_label_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onStartBaseline,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.idle_btn_start_sec, baselineDurationSec),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // 3-Step Workflow Explainer
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkflowStepCard(
                title = stringResource(R.string.idle_step_1_title),
                desc = stringResource(R.string.idle_step_1_desc),
                modifier = Modifier.weight(1f)
            )
            WorkflowStepCard(
                title = stringResource(R.string.idle_step_2_title),
                desc = stringResource(R.string.idle_step_2_desc),
                modifier = Modifier.weight(1f)
            )
            WorkflowStepCard(
                title = stringResource(R.string.idle_step_3_title),
                desc = stringResource(R.string.idle_step_3_desc),
                modifier = Modifier.weight(1f)
            )
        }

        if (savedSessionsCount > 0) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.idle_recent_sessions, savedSessionsCount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = stringResource(R.string.idle_view_history),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowStepCard(
    title: String, desc: String, modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2
            )
        }
    }
}

@Composable
private fun BaselinePhaseView(
    state: DetectorUiState, onSkipToMonitoring: () -> Unit, onCancel: () -> Unit
) {
    val progress = 1f - (state.baselineRemainingSec.toFloat() / state.baselineTotalDurationSec.coerceAtLeast(1))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.baseline_collecting),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress }, modifier = Modifier.size(118.dp), strokeWidth = 9.dp
                    )
                    Text(
                        text = "${state.baselineRemainingSec}s",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.baseline_devices_found, state.baselineDeviceCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.baseline_packets_processed,
                        state.totalPacketsProcessed,
                        state.blePacketsProcessed,
                        state.wifiPacketsProcessed
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.baseline_btn_cancel))
            }
            Button(
                onClick = onSkipToMonitoring, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1.3f)
            ) {
                Text(stringResource(R.string.baseline_btn_skip))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.baseline_preview_title, state.baselinePreviewDevices.size),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.baselinePreviewDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.baseline_waiting_signals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(state.baselinePreviewDevices, key = { it.clusterKey }) { dev ->
                    DevicePreviewRow(dev)
                }
            }
        }
    }
}

@Composable
private fun DevicePreviewRow(device: DetectedDevice) {
    val semanticColors = LocalArgusColors.current
    val accent = if (device.type == DeviceType.BLE) semanticColors.bleAccent else semanticColors.wifiAccent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.14f), modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (device.type == DeviceType.BLE) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.manufacturer} · ${device.clusterKey.take(12)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "${device.rawRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MonitoringPhaseView(
    state: DetectorUiState, onSelectRadarTarget: (String) -> Unit, onFinishAndSave: () -> Unit
) {
    val semanticColors = LocalArgusColors.current
    var showPowerOffLogs by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(AlertFilter.ALL) }

    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f, animationSpec = infiniteRepeatable(
            animation = tween(900), repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    val filteredAlerts = remember(state.suspiciousAlerts, activeFilter) {
        state.suspiciousAlerts.filter { alert ->
            when (activeFilter) {
                AlertFilter.ALL -> true
                AlertFilter.HIGH_RISK -> alert.riskScore >= 70
                AlertFilter.AUDIO_WEARABLE -> alert.device.isSuspiciousAudioOrWearable
                AlertFilter.BLE -> alert.device.type == DeviceType.BLE
                AlertFilter.WIFI -> alert.device.type == DeviceType.WIFI
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
    ) {
        // Live Guard Status Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .alpha(pulseAlpha)
                                .background(semanticColors.safe, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.monitoring_live_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = semanticColors.safe,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.monitoring_stats_bar,
                            state.baselineDeviceCount,
                            state.totalPacketsProcessed,
                            formatDuration(state.monitoringElapsedSec)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onFinishAndSave,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.monitoring_finish_save),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.monitoring_alerts_title, state.suspiciousAlerts.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Alert filter chips
        if (state.suspiciousAlerts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeFilter == AlertFilter.ALL,
                    onClick = { activeFilter = AlertFilter.ALL },
                    label = { Text(stringResource(R.string.monitoring_filter_all)) })
                FilterChip(
                    selected = activeFilter == AlertFilter.HIGH_RISK,
                    onClick = { activeFilter = AlertFilter.HIGH_RISK },
                    label = { Text(stringResource(R.string.monitoring_filter_high_risk)) })
                FilterChip(
                    selected = activeFilter == AlertFilter.AUDIO_WEARABLE,
                    onClick = { activeFilter = AlertFilter.AUDIO_WEARABLE },
                    label = { Text(stringResource(R.string.monitoring_filter_audio)) })
                FilterChip(
                    selected = activeFilter == AlertFilter.BLE,
                    onClick = { activeFilter = AlertFilter.BLE },
                    label = { Text(stringResource(R.string.monitoring_filter_ble)) })
                FilterChip(
                    selected = activeFilter == AlertFilter.WIFI,
                    onClick = { activeFilter = AlertFilter.WIFI },
                    label = { Text(stringResource(R.string.monitoring_filter_wifi)) })
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (state.suspiciousAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape, color = semanticColors.safeContainer, modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = semanticColors.safe,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.monitoring_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.monitoring_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (filteredAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.monitoring_empty_filtered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filteredAlerts, key = { it.device.clusterKey }) { alert ->
                    SuspiciousAlertCard(
                        alert = alert, onClick = { onSelectRadarTarget(alert.device.clusterKey) })
                }
            }
        }

        if (state.powerOffEvents.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .clickable { showPowerOffLogs = !showPowerOffLogs }
                .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.power_off_title, state.powerOffEvents.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(if (showPowerOffLogs) R.string.power_off_collapse else R.string.power_off_expand),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (showPowerOffLogs) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.powerOffEvents, key = { "${it.clusterKey}_${it.disappearedAtMillis}" }) { ev ->
                        PowerOffEventRow(ev)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuspiciousAlertCard(
    alert: SuspiciousAlert, onClick: () -> Unit
) {
    val semanticColors = LocalArgusColors.current
    val dev = alert.device
    val isHighRisk = alert.riskScore >= 70
    val isMedRisk = alert.riskScore in 40..69
    val riskColor = when {
        isHighRisk -> semanticColors.danger
        isMedRisk -> semanticColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val typeColor = if (dev.type == DeviceType.BLE) semanticColors.bleAccent else semanticColors.wifiAccent

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighRisk) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left risk accent stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(124.dp)
                    .background(riskColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (dev.type == DeviceType.BLE) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dev.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = riskColor, shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.alert_risk_badge, alert.riskScore),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    val reasonLabel = when (alert.reason) {
                        AlertReason.NEW_DEVICE -> stringResource(R.string.alert_reason_new, alert.observationCountIn15s)
                        AlertReason.SUDDEN_APPROACH -> {
                            val delta = dev.rawRssi - (alert.baselineMaxRssi ?: dev.rawRssi)
                            stringResource(R.string.alert_reason_surge, delta)
                        }
                    }
                    AssistChip(
                        onClick = onClick,
                        label = { Text(reasonLabel, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                        })
                    if (dev.isSuspiciousAudioOrWearable) {
                        AssistChip(onClick = onClick, label = {
                            Text(
                                text = stringResource(
                                    if (dev.type == DeviceType.WIFI) R.string.alert_tag_hotspot
                                    else R.string.alert_tag_audio
                                ), style = MaterialTheme.typography.labelSmall
                            )
                        }, leadingIcon = {
                            Icon(
                                Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(14.dp)
                            )
                        })
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dev.manufacturer} · FP:${dev.clusterKey.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${dev.rawRssi} dBm",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = riskColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.alert_tap_radar),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerOffEventRow(event: PowerOffEvent) {
    Surface(
        shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.power_off_row_desc, event.displayName, event.manufacturer),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${formatTimeOnly(event.disappearedAtMillis)} · ${event.lastRssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun RadarPhaseView(
    radar: RadarTargetState,
    seatLockRssi: Int,
    soundEnabled: Boolean,
    hapticEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleHaptic: () -> Unit,
    onBack: () -> Unit
) {
    val semanticColors = LocalArgusColors.current
    val dev = radar.targetDevice
    val gaugeFraction = ((radar.currentFilteredRssi.toFloat() + 90f) / 60f).coerceIn(0f, 1f)
    val zoneColor by animateColorAsState(
        targetValue = when (radar.proximityZone) {
            ProximityZone.VERY_CLOSE -> semanticColors.danger
            ProximityZone.NEAR -> semanticColors.warning
            ProximityZone.FAR -> semanticColors.radarBeam
        }, label = "radarColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.radar_back_desc)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dev.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${dev.manufacturer} · ${dev.clusterKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp), color = zoneColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = stringResource(
                            when (radar.proximityZone) {
                                ProximityZone.VERY_CLOSE -> R.string.radar_zone_very_close_badge
                                ProximityZone.NEAR -> R.string.radar_zone_near_badge
                                ProximityZone.FAR -> R.string.radar_zone_far_badge
                            }
                        ),
                        color = zoneColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedRadarScope(
                rawRssi = radar.currentRawRssi,
                filteredRssi = radar.currentFilteredRssi,
                proximityZone = radar.proximityZone,
                zoneColor = zoneColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.radar_filtered_peak, radar.currentFilteredRssi, radar.peakRssi
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Proximity Gauge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    LinearProgressIndicator(
                        progress = { gaugeFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp)),
                        color = zoneColor,
                        trackColor = zoneColor.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.radar_scale_far), style = MaterialTheme.typography.labelSmall)
                        Text(
                            stringResource(R.string.radar_scale_seat_dynamic, seatLockRssi),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.radar_scale_max), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val zoneMessage = stringResource(
                        when (radar.proximityZone) {
                            ProximityZone.VERY_CLOSE -> R.string.radar_zone_very_close
                            ProximityZone.NEAR -> R.string.radar_zone_near
                            ProximityZone.FAR -> R.string.radar_zone_far
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(zoneColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = zoneMessage,
                            color = zoneColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            RssiSparklineCard(
                history = radar.rssiHistory, zoneColor = zoneColor, seatLockRssi = seatLockRssi
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleSound, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(if (soundEnabled) R.string.radar_sound_on else R.string.radar_sound_off))
                }
                OutlinedButton(
                    onClick = onToggleHaptic, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(if (hapticEnabled) R.string.radar_haptic_on else R.string.radar_haptic_off))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.radar_btn_back))
            }
        }
    }
}

@Composable
private fun phaseSubtitle(state: DetectorUiState): String = when (state.phase) {
    ScanPhase.IDLE -> stringResource(R.string.phase_idle)
    ScanPhase.BASELINE -> stringResource(R.string.phase_baseline, state.baselineRemainingSec)
    ScanPhase.MONITORING -> stringResource(R.string.phase_monitoring)
    ScanPhase.RADAR_LOCATING -> stringResource(R.string.phase_radar)
}
