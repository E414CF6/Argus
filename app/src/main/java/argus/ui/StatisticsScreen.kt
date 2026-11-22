package argus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import argus.R
import argus.domain.model.DeviceType
import argus.domain.stats.ScanStatisticsSummary
import argus.domain.stats.SessionTrendPoint
import argus.domain.stats.StatsTimeframe
import argus.ui.components.formatDuration
import argus.ui.theme.LocalArgusColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatisticsScreen(
    stats: ScanStatisticsSummary, timeframe: StatsTimeframe, onSelectTimeframe: (StatsTimeframe) -> Unit
) {
    val semanticColors = LocalArgusColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.stats_subtitle, stats.totalSessions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = timeframe == StatsTimeframe.ALL,
                onClick = { onSelectTimeframe(StatsTimeframe.ALL) },
                label = { Text(stringResource(R.string.stats_range_all)) })
            FilterChip(
                selected = timeframe == StatsTimeframe.LAST_7_DAYS,
                onClick = { onSelectTimeframe(StatsTimeframe.LAST_7_DAYS) },
                label = { Text(stringResource(R.string.stats_range_7d)) })
            FilterChip(
                selected = timeframe == StatsTimeframe.LAST_30_DAYS,
                onClick = { onSelectTimeframe(StatsTimeframe.LAST_30_DAYS) },
                label = { Text(stringResource(R.string.stats_range_30d)) })
        }

        if (stats.totalSessions == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.stats_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.stats_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                // 2x2 KPI Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiCard(
                                icon = Icons.Default.Security,
                                label = stringResource(R.string.stats_kpi_sessions),
                                value = stats.totalSessions.toString(),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                icon = Icons.Default.Schedule,
                                label = stringResource(R.string.stats_kpi_watch_time),
                                value = formatDuration(stats.totalDurationSec),
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiCard(
                                icon = Icons.Default.Warning,
                                label = stringResource(R.string.stats_kpi_alerts),
                                value = stats.totalAlerts.toString(),
                                accent = semanticColors.warning,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                icon = Icons.Default.Bolt,
                                label = stringResource(R.string.stats_kpi_high_risk),
                                value = stats.highRiskAlertCount.toString(),
                                accent = semanticColors.danger,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Secondary Compact KPI Strip
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MiniMetricColumn(
                                label = stringResource(R.string.stats_kpi_packets),
                                value = formatCompactNumber(stats.totalPacketsProcessed),
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricColumn(
                                label = stringResource(R.string.stats_kpi_power_off),
                                value = stats.totalPowerOffEvents.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricColumn(
                                label = stringResource(R.string.stats_kpi_avg_alerts),
                                value = "%.1f".format(Locale.US, stats.avgAlertsPerSession),
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricColumn(
                                label = stringResource(R.string.stats_kpi_peak_risk),
                                value = stats.peakRiskScore.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Trend Chart across recent sessions
                if (stats.sessionTrend.isNotEmpty()) {
                    item {
                        SessionTrendChartCard(trend = stats.sessionTrend)
                    }
                }

                // Signal & Trigger Breakdown
                item {
                    SignalBreakdownCard(stats = stats)
                }

                // Risk Score Distribution
                item {
                    RiskDistributionCard(stats = stats)
                }

                // Top Manufacturers
                if (stats.topManufacturers.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.stats_top_mfr_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                val maxCount = stats.topManufacturers.maxOf { it.count }.coerceAtLeast(1)
                                stats.topManufacturers.forEachIndexed { idx, item ->
                                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(104.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        LinearProgressIndicator(
                                            progress = { item.count.toFloat() / maxCount.toFloat() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.count.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Frequent Flagged Devices
                if (stats.topDevices.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.stats_top_devices_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                stats.topDevices.forEachIndexed { idx, dev ->
                                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (dev.type == DeviceType.BLE) Icons.Default.Bluetooth else Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (dev.type == DeviceType.BLE) semanticColors.bleAccent else semanticColors.wifiAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${dev.manufacturer} · ${dev.clusterKey.take(10)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.stats_count_times, dev.occurrences, dev.peakRiskScore
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MiniMetricColumn(
    label: String, value: String, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SessionTrendChartCard(trend: List<SessionTrendPoint>) {
    val semanticColors = LocalArgusColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val dangerColor = semanticColors.danger
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val dateFmt = SimpleDateFormat("MM/dd", Locale.getDefault())

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_chart_trend_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.stats_chart_trend_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(color = primaryColor, label = stringResource(R.string.stats_legend_alerts))
                    LegendDot(color = dangerColor, label = stringResource(R.string.stats_legend_high_risk))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val maxAlerts = (trend.maxOfOrNull { it.alertCount } ?: 1).coerceAtLeast(1)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val w = size.width
                val h = size.height
                val slotWidth = w / trend.size.coerceAtLeast(1)
                val barWidth = (slotWidth * 0.42f).coerceIn(10f, 34f)

                // Horizontal baseline & mid guide
                drawLine(
                    color = gridColor, start = Offset(0f, h - 1f), end = Offset(w, h - 1f), strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 0.8.dp.toPx()
                )

                trend.forEachIndexed { idx, point ->
                    val centerX = idx * slotWidth + slotWidth / 2f
                    val totalBarH = (point.alertCount.toFloat() / maxAlerts.toFloat()) * (h - 14.dp.toPx())
                    val highBarH = (point.highRiskCount.toFloat() / maxAlerts.toFloat()) * (h - 14.dp.toPx())

                    val xLeft = centerX - barWidth / 2f
                    if (totalBarH > 0f) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.8f),
                            topLeft = Offset(xLeft, h - totalBarH),
                            size = Size(barWidth, totalBarH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    } else {
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(xLeft, h - 4.dp.toPx()),
                            size = Size(barWidth, 4.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }

                    if (highBarH > 0f) {
                        drawRoundRect(
                            color = dangerColor,
                            topLeft = Offset(xLeft, h - highBarH),
                            size = Size(barWidth, highBarH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                trend.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${point.alertCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFmt.format(Date(point.startedAtMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignalBreakdownCard(stats: ScanStatisticsSummary) {
    val semanticColors = LocalArgusColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.stats_chart_breakdown_title),
                style = MaterialTheme.typography.titleMedium
            )

            SegmentedComparisonRow(
                title = stringResource(R.string.stats_ble_vs_wifi),
                leftLabel = "BLE ${stats.bleAlertCount}",
                rightLabel = "Wi-Fi ${stats.wifiAlertCount}",
                leftCount = stats.bleAlertCount,
                rightCount = stats.wifiAlertCount,
                leftColor = semanticColors.bleAccent,
                rightColor = semanticColors.wifiAccent
            )

            SegmentedComparisonRow(
                title = stringResource(R.string.stats_reason_split),
                leftLabel = "${
                    stringResource(
                        R.string.alert_reason_new, stats.newDeviceAlertCount
                    ).substringBefore(" (")
                } ${stats.newDeviceAlertCount}",
                rightLabel = "${
                    stringResource(
                        R.string.alert_reason_surge, 0
                    ).substringBefore(" (")
                } ${stats.suddenApproachAlertCount}",
                leftCount = stats.newDeviceAlertCount,
                rightCount = stats.suddenApproachAlertCount,
                leftColor = MaterialTheme.colorScheme.primary,
                rightColor = semanticColors.warning
            )

            val otherAlerts = (stats.totalAlerts - stats.audioWearableAlertCount).coerceAtLeast(0)
            SegmentedComparisonRow(
                title = stringResource(R.string.stats_audio_share),
                leftLabel = "${stringResource(R.string.alert_tag_audio)} ${stats.audioWearableAlertCount}",
                rightLabel = "Other $otherAlerts",
                leftCount = stats.audioWearableAlertCount,
                rightCount = otherAlerts,
                leftColor = semanticColors.danger,
                rightColor = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SegmentedComparisonRow(
    title: String,
    leftLabel: String,
    rightLabel: String,
    leftCount: Int,
    rightCount: Int,
    leftColor: Color,
    rightColor: Color
) {
    val total = (leftCount + rightCount).coerceAtLeast(1)
    val leftFrac = (leftCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Column {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(rightColor.copy(alpha = 0.25f))
        ) {
            if (leftCount + rightCount == 0) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                if (leftFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(leftFrac.coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(leftColor)
                    )
                }
                val rightFrac = 1f - leftFrac
                if (rightFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(rightFrac.coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(rightColor)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(leftLabel, style = MaterialTheme.typography.labelSmall, color = leftColor)
            Text(
                rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RiskDistributionCard(stats: ScanStatisticsSummary) {
    val semanticColors = LocalArgusColors.current
    val total = stats.totalAlerts.coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.stats_risk_dist_title), style = MaterialTheme.typography.titleMedium
            )
            RiskBarRow(
                label = stringResource(R.string.stats_risk_high),
                count = stats.highRiskAlertCount,
                total = total,
                color = semanticColors.danger
            )
            RiskBarRow(
                label = stringResource(R.string.stats_risk_med),
                count = stats.mediumRiskAlertCount,
                total = total,
                color = semanticColors.warning
            )
            RiskBarRow(
                label = stringResource(R.string.stats_risk_low),
                count = stats.lowRiskAlertCount,
                total = total,
                color = semanticColors.safe
            )
        }
    }
}

@Composable
private fun RiskBarRow(label: String, count: Int, total: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(116.dp)
        )
        LinearProgressIndicator(
            progress = { count.toFloat() / total.toFloat() },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$count", style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatCompactNumber(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(Locale.US, n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(Locale.US, n / 1_000.0)
    else -> n.toString()
}
