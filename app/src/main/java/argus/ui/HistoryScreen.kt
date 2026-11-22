package argus.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import argus.R
import argus.domain.model.*
import argus.ui.components.formatDateTime
import argus.ui.components.formatDuration
import argus.ui.components.formatFileTimestamp
import argus.ui.components.formatTimeOnly
import argus.ui.theme.LocalArgusColors

private enum class HistoryFilter {
    ALL, LOCAL, IMPORTED, WITH_ALERTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<ScanSessionRecord>,
    onBuildExportJson: (List<ScanSessionRecord>) -> String,
    onBuildExportCsv: (List<ScanSessionRecord>) -> String,
    onExportSuccess: (Int) -> Unit,
    onExportFailed: (String) -> Unit,
    onImportJson: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingExportTargets by remember { mutableStateOf<List<ScanSessionRecord>>(emptyList()) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = onBuildExportJson(pendingExportTargets)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream")
        }.onSuccess {
            onExportSuccess(pendingExportTargets.size)
        }.onFailure { err ->
            onExportFailed(err.message ?: "Write error")
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = onBuildExportCsv(pendingExportTargets)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream")
        }.onSuccess {
            onExportSuccess(pendingExportTargets.size)
        }.onFailure { err ->
            onExportFailed(err.message ?: "Write error")
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: ""
        }.onSuccess { text ->
            onImportJson(text)
        }.onFailure { err ->
            onExportFailed(err.message ?: "Read error")
        }
    }

    val filteredSessions = remember(sessions, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase()
        sessions.filter { s ->
            val matchesFilter = when (activeFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.LOCAL -> s.source == SessionSource.LOCAL
                HistoryFilter.IMPORTED -> s.source == SessionSource.IMPORTED
                HistoryFilter.WITH_ALERTS -> s.suspiciousAlerts.isNotEmpty()
            }
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                s.title.lowercase().contains(q) ||
                        s.suspiciousAlerts.any {
                            it.displayName.lowercase().contains(q) ||
                                    it.manufacturer.lowercase().contains(q)
                        }
            }
            matchesFilter && matchesQuery
        }
    }

    val selectedSession = remember(sessions, selectedSessionId) {
        sessions.find { it.id == selectedSessionId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Export / Import Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.history_subtitle, sessions.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { jsonImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.history_action_import), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Export All Row when sessions exist
        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val targets = filteredSessions.ifEmpty { sessions }
                        pendingExportTargets = targets
                        jsonExportLauncher.launch("argus_history_${formatFileTimestamp()}.json")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.history_action_export_json), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val targets = filteredSessions.ifEmpty { sessions }
                        pendingExportTargets = targets
                        csvExportLauncher.launch("argus_report_${formatFileTimestamp()}.csv")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.history_action_export_csv), maxLines = 1)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.history_search_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeFilter == HistoryFilter.ALL,
                    onClick = { activeFilter = HistoryFilter.ALL },
                    label = { Text(stringResource(R.string.history_filter_all)) }
                )
                FilterChip(
                    selected = activeFilter == HistoryFilter.LOCAL,
                    onClick = { activeFilter = HistoryFilter.LOCAL },
                    label = { Text(stringResource(R.string.history_filter_local)) }
                )
                FilterChip(
                    selected = activeFilter == HistoryFilter.IMPORTED,
                    onClick = { activeFilter = HistoryFilter.IMPORTED },
                    label = { Text(stringResource(R.string.history_filter_imported)) }
                )
                FilterChip(
                    selected = activeFilter == HistoryFilter.WITH_ALERTS,
                    onClick = { activeFilter = HistoryFilter.WITH_ALERTS },
                    label = { Text(stringResource(R.string.history_filter_alerts)) }
                )
            }
        }

        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.history_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredSessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        onClick = { selectedSessionId = session.id }
                    )
                }
            }
        }
    }

    if (selectedSession != null) {
        SessionDetailBottomSheet(
            session = selectedSession,
            onDismiss = { selectedSessionId = null },
            onRename = { newTitle -> onRenameSession(selectedSession.id, newTitle) },
            onDelete = {
                selectedSessionId = null
                onDeleteSession(selectedSession.id)
            },
            onExportJson = {
                pendingExportTargets = listOf(selectedSession)
                jsonExportLauncher.launch("argus_session_${formatFileTimestamp(selectedSession.startedAtMillis)}.json")
            },
            onExportCsv = {
                pendingExportTargets = listOf(selectedSession)
                csvExportLauncher.launch("argus_session_${formatFileTimestamp(selectedSession.startedAtMillis)}.csv")
            },
            onShareSummary = { shareSessionSummary(context, selectedSession) }
        )
    }
}

@Composable
private fun SessionHistoryCard(
    session: ScanSessionRecord,
    onClick: () -> Unit
) {
    val semanticColors = LocalArgusColors.current
    val riskColor = when {
        session.maxRiskScore >= 70 -> semanticColors.danger
        session.maxRiskScore >= 40 -> semanticColors.warning
        else -> semanticColors.safe
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (session.source == SessionSource.IMPORTED) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (session.source == SessionSource.IMPORTED) R.string.history_badge_imported
                                    else R.string.history_badge_local
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDateTime(session.startedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = riskColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (session.suspiciousAlerts.isEmpty()) {
                            stringResource(R.string.history_card_alerts, 0)
                        } else {
                            stringResource(R.string.history_card_peak_risk, session.maxRiskScore)
                        },
                        color = riskColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniPill(
                    text = stringResource(R.string.history_card_duration, formatDuration(session.durationSec)),
                    modifier = Modifier.weight(1f)
                )
                StatMiniPill(
                    text = stringResource(R.string.history_card_baseline, session.baselineDeviceCount),
                    modifier = Modifier.weight(1f)
                )
                StatMiniPill(
                    text = stringResource(R.string.history_card_alerts, session.suspiciousAlerts.size),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatMiniPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailBottomSheet(
    session: ScanSessionRecord,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onShareSummary: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatDateTime(session.startedAtMillis)} · ${formatDuration(session.durationSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.history_detail_rename))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.history_detail_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.history_detail_packets,
                    session.totalPacketsProcessed,
                    session.blePacketsProcessed,
                    session.wifiPacketsProcessed
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onExportJson,
                    label = { Text(stringResource(R.string.history_detail_export_json)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = onExportCsv,
                    label = { Text(stringResource(R.string.history_detail_export_csv)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = onShareSummary,
                    label = { Text(stringResource(R.string.history_detail_share)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.history_detail_alerts, session.suspiciousAlerts.size),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (session.suspiciousAlerts.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_detail_no_alerts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(session.suspiciousAlerts, key = { it.clusterKey }) { alert ->
                        HistoryAlertRow(alert)
                    }
                }
            }

            if (session.powerOffEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_detail_power_off, session.powerOffEvents.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                session.powerOffEvents.take(5).forEach { ev ->
                    Text(
                        text = "• ${ev.displayName} (${ev.manufacturer}) · ${formatTimeOnly(ev.disappearedAtMillis)} · ${ev.lastRssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var titleInput by remember { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.history_rename_title)) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(titleInput)
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_msg, session.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryAlertRow(alert: SuspiciousAlertRecord) {
    val semanticColors = LocalArgusColors.current
    val riskColor = when {
        alert.riskScore >= 70 -> semanticColors.danger
        alert.riskScore >= 40 -> semanticColors.warning
        else -> semanticColors.safe
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (alert.type == DeviceType.BLE) Icons.Default.Bluetooth else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (alert.type == DeviceType.BLE) semanticColors.bleAccent else semanticColors.wifiAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val reasonText = when (alert.reason) {
                    AlertReason.NEW_DEVICE -> stringResource(R.string.alert_reason_new, alert.observationCountIn15s)
                    AlertReason.SUDDEN_APPROACH -> stringResource(
                        R.string.alert_reason_surge,
                        alert.rawRssi - (alert.baselineMaxRssi ?: alert.rawRssi)
                    )
                }
                Text(
                    text = "${alert.manufacturer} · $reasonText · ${alert.rawRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = riskColor
            ) {
                Text(
                    text = "${alert.riskScore}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun shareSessionSummary(context: Context, session: ScanSessionRecord) {
    val body = buildString {
        appendLine("[Argus Report] ${session.title}")
        appendLine("Date: ${formatDateTime(session.startedAtMillis)}")
        appendLine("Duration: ${formatDuration(session.durationSec)}")
        appendLine("Baseline Devices: ${session.baselineDeviceCount}")
        appendLine("Packets: ${session.totalPacketsProcessed} (BLE ${session.blePacketsProcessed}, Wi-Fi ${session.wifiPacketsProcessed})")
        appendLine("Suspicious Alerts: ${session.suspiciousAlerts.size} (Max Risk ${session.maxRiskScore})")
        session.suspiciousAlerts.forEachIndexed { idx, a ->
            appendLine("${idx + 1}. ${a.displayName} [${a.manufacturer}] - Risk ${a.riskScore}, ${a.rawRssi} dBm (${a.reason})")
        }
        if (session.powerOffEvents.isNotEmpty()) {
            appendLine("Power-Off Events: ${session.powerOffEvents.size}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Argus Report: ${session.title}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
