package argus.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import argus.R
import argus.data.preferences.AppLanguage
import argus.data.preferences.AppSettings
import argus.data.preferences.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: AppSettings,
    savedSessionCount: Int,
    onDismiss: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onResetSettings: () -> Unit,
    onClearAllHistory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSourceGuardNotice by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Modal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. General & Display
                SettingsSectionCard(title = stringResource(R.string.settings_section_general)) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.language == AppLanguage.SYSTEM,
                            onClick = { onSelectLanguage(AppLanguage.SYSTEM) },
                            label = { Text(stringResource(R.string.lang_system)) }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.KOREAN,
                            onClick = { onSelectLanguage(AppLanguage.KOREAN) },
                            label = { Text("한국어") }
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English") }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onUpdateSettings { it.copy(themeMode = ThemeMode.SYSTEM) } },
                            label = { Text(stringResource(R.string.theme_system)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onUpdateSettings { it.copy(themeMode = ThemeMode.LIGHT) } },
                            label = { Text(stringResource(R.string.theme_light)) }
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onUpdateSettings { it.copy(themeMode = ThemeMode.DARK) } },
                            label = { Text(stringResource(R.string.theme_dark)) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_keep_screen_on),
                        description = stringResource(R.string.settings_keep_screen_on_desc),
                        checked = settings.keepScreenOn,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(keepScreenOn = checked) }
                        }
                    )
                }

                // 2. Scanner & Sources
                SettingsSectionCard(title = stringResource(R.string.settings_section_scanner)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_enable_ble),
                        description = stringResource(R.string.settings_enable_ble_desc),
                        checked = settings.enableBleScan,
                        onCheckedChange = { checked ->
                            if (!checked && !settings.enableWifiScan) {
                                showSourceGuardNotice = true
                            } else {
                                showSourceGuardNotice = false
                                onUpdateSettings { it.copy(enableBleScan = checked) }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_enable_wifi),
                        description = stringResource(R.string.settings_enable_wifi_desc),
                        checked = settings.enableWifiScan,
                        onCheckedChange = { checked ->
                            if (!checked && !settings.enableBleScan) {
                                showSourceGuardNotice = true
                            } else {
                                showSourceGuardNotice = false
                                onUpdateSettings { it.copy(enableWifiScan = checked) }
                            }
                        }
                    )
                    if (showSourceGuardNotice) {
                        Text(
                            text = stringResource(R.string.settings_at_least_one_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = stringResource(R.string.settings_wifi_interval),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { sec ->
                            FilterChip(
                                selected = settings.wifiScanIntervalSec == sec,
                                enabled = settings.enableWifiScan,
                                onClick = { onUpdateSettings { it.copy(wifiScanIntervalSec = sec) } },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_save),
                        description = stringResource(R.string.settings_auto_save_desc),
                        checked = settings.autoSaveSession,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(autoSaveSession = checked) }
                        }
                    )
                }

                // 3. Detection Engine Sensitivity
                SettingsSectionCard(title = stringResource(R.string.settings_section_detection)) {
                    val cfg = settings.detectorConfig

                    Text(
                        text = stringResource(R.string.settings_baseline_duration),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 90, 120).forEach { sec ->
                            FilterChip(
                                selected = cfg.baselineDurationSec == sec,
                                onClick = {
                                    onUpdateSettings {
                                        it.copy(detectorConfig = it.detectorConfig.copy(baselineDurationSec = sec))
                                    }
                                },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        title = stringResource(R.string.settings_noise_floor, cfg.noiseFloorRssi),
                        description = stringResource(R.string.settings_noise_floor_desc),
                        value = cfg.noiseFloorRssi.toFloat(),
                        valueRange = -90f..-65f,
                        steps = 24,
                        onValueChange = { v ->
                            onUpdateSettings {
                                it.copy(detectorConfig = it.detectorConfig.copy(noiseFloorRssi = v.roundToInt()))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        title = stringResource(R.string.settings_new_device_rssi, cfg.newDeviceMinRssi),
                        description = stringResource(R.string.settings_new_device_rssi_desc),
                        value = cfg.newDeviceMinRssi.toFloat(),
                        valueRange = -78f..-55f,
                        steps = 22,
                        onValueChange = { v ->
                            onUpdateSettings {
                                it.copy(detectorConfig = it.detectorConfig.copy(newDeviceMinRssi = v.roundToInt()))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsSliderItem(
                        title = stringResource(R.string.settings_surge_delta, cfg.surgeDeltaDbm),
                        description = stringResource(R.string.settings_surge_delta_desc),
                        value = cfg.surgeDeltaDbm.toFloat(),
                        valueRange = 10f..25f,
                        steps = 14,
                        onValueChange = { v ->
                            onUpdateSettings {
                                it.copy(detectorConfig = it.detectorConfig.copy(surgeDeltaDbm = v.roundToInt()))
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = stringResource(R.string.settings_power_off_timeout),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30).forEach { sec ->
                            val ms = sec * 1_000L
                            FilterChip(
                                selected = cfg.powerOffTimeoutMs == ms,
                                onClick = {
                                    onUpdateSettings {
                                        it.copy(detectorConfig = it.detectorConfig.copy(powerOffTimeoutMs = ms))
                                    }
                                },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }

                // 4. Radar & Feedback
                SettingsSectionCard(title = stringResource(R.string.settings_section_radar)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_radar_sound),
                        description = null,
                        checked = settings.soundEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(soundEnabled = checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_radar_haptic),
                        description = null,
                        checked = settings.hapticEnabled,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(hapticEnabled = checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsSliderItem(
                        title = stringResource(
                            R.string.settings_seat_lock_rssi,
                            settings.detectorConfig.radarVeryCloseRssi
                        ),
                        description = stringResource(R.string.settings_seat_lock_rssi_desc),
                        value = settings.detectorConfig.radarVeryCloseRssi.toFloat(),
                        valueRange = -55f..-35f,
                        steps = 19,
                        onValueChange = { v ->
                            onUpdateSettings {
                                it.copy(detectorConfig = it.detectorConfig.copy(radarVeryCloseRssi = v.roundToInt()))
                            }
                        }
                    )
                }

                // 5. Data & Reset
                SettingsSectionCard(title = stringResource(R.string.settings_section_data)) {
                    OutlinedButton(
                        onClick = onResetSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_reset_defaults))
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryConfirm = true },
                        enabled = savedSessionCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm_msg, savedSessionCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryConfirm = false
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
