package argus.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import argus.R
import argus.data.preferences.AppLanguage
import argus.ui.theme.LocalArgusColors

fun requiredScanPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}.toTypedArray()

fun hasAllScanPermissions(context: Context): Boolean =
    requiredScanPermissions().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

private fun isPermissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionGateScreen(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val semanticColors = LocalArgusColors.current
    var deniedOnce by remember { mutableStateOf(false) }
    var permissionCheckTick by remember { mutableStateOf(0) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionCheckTick++
        if (result.values.all { it } || hasAllScanPermissions(context)) {
            onPermissionsGranted()
        } else {
            deniedOnce = true
        }
    }

    val bleGranted = remember(permissionCheckTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }
    val locationGranted = remember(permissionCheckTick) {
        isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val wifiGranted = remember(permissionCheckTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            locationGranted
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top language switcher bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box {
                TextButton(onClick = { langMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = stringResource(R.string.action_language),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.SYSTEM -> stringResource(R.string.action_language)
                            AppLanguage.KOREAN -> "한국어"
                            AppLanguage.ENGLISH -> "English"
                        }
                    )
                }
                DropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_system)) },
                        onClick = {
                            langMenuExpanded = false
                            onSelectLanguage(AppLanguage.SYSTEM)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_korean)) },
                        onClick = {
                            langMenuExpanded = false
                            onSelectLanguage(AppLanguage.KOREAN)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lang_english)) },
                        onClick = {
                            langMenuExpanded = false
                            onSelectLanguage(AppLanguage.ENGLISH)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.perm_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.perm_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionRationaleCard(
            icon = Icons.Default.Bluetooth,
            title = stringResource(R.string.perm_ble_title),
            description = stringResource(R.string.perm_ble_desc),
            granted = bleGranted,
            grantedColor = semanticColors.safe
        )
        Spacer(modifier = Modifier.height(10.dp))
        PermissionRationaleCard(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.perm_location_title),
            description = stringResource(R.string.perm_location_desc),
            granted = locationGranted,
            grantedColor = semanticColors.safe
        )
        Spacer(modifier = Modifier.height(10.dp))
        PermissionRationaleCard(
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.perm_wifi_title),
            description = stringResource(R.string.perm_wifi_desc),
            granted = wifiGranted,
            grantedColor = semanticColors.safe
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (deniedOnce) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.perm_denied_banner),
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Button(
            onClick = {
                if (hasAllScanPermissions(context)) {
                    onPermissionsGranted()
                } else {
                    launcher.launch(requiredScanPermissions())
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = stringResource(R.string.perm_btn_grant),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (deniedOnce) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.perm_btn_open_settings))
            }
        }
    }
}

@Composable
private fun PermissionRationaleCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedColor: androidx.compose.ui.graphics.Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (granted) R.string.perm_status_granted else R.string.perm_status_required
                ),
                tint = if (granted) grantedColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
