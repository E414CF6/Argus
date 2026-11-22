package argus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import argus.R
import argus.domain.model.ProximityZone

@Composable
fun AnimatedRadarScope(
    rawRssi: Int, filteredRssi: Double, proximityZone: ProximityZone, zoneColor: Color, modifier: Modifier = Modifier
) {
    // 0.0 = far (-90 dBm), 1.0 = very close (-30 dBm)
    val targetFraction = ((filteredRssi.toFloat() + 90f) / 60f).coerceIn(0.05f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction, animationSpec = tween(durationMillis = 350), label = "rssiFraction"
    )

    val pulseDurationMs = when (proximityZone) {
        ProximityZone.VERY_CLOSE -> 900
        ProximityZone.NEAR -> 1500
        ProximityZone.FAR -> 2400
    }
    val infiniteTransition = rememberInfiniteTransition(label = "radarAnim")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "sweep"
    )
    val pulseRadiusFraction by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 1.0f, animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDurationMs, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "pulse"
    )

    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surfaceBg = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.size(230.dp), contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 8.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            // Subtle radial background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        zoneColor.copy(alpha = 0.18f), zoneColor.copy(alpha = 0.04f), Color.Transparent
                    ), center = center, radius = radius
                ), radius = radius, center = center
            )

            // Rotating radar sweep cone
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1.0f to zoneColor.copy(alpha = 0.28f),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                )
            }

            // Concentric range rings (100%, 66%, 33%)
            listOf(1.0f, 0.68f, 0.36f).forEachIndexed { idx, scale ->
                drawCircle(
                    color = if (idx == 2) zoneColor.copy(alpha = 0.5f) else ringColor,
                    radius = radius * scale,
                    center = center,
                    style = Stroke(width = if (idx == 2) 1.8.dp.toPx() else 1.2.dp.toPx())
                )
            }

            // Crosshair lines
            drawLine(
                color = ringColor,
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = ringColor,
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.dp.toPx()
            )

            // Expanding pulse ring
            drawCircle(
                color = zoneColor.copy(alpha = (1f - pulseRadiusFraction) * 0.65f),
                radius = radius * pulseRadiusFraction,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Active signal strength ring matching proximity
            val signalRingRadius = radius * (1f - animatedFraction * 0.72f)
            drawCircle(
                color = zoneColor.copy(alpha = 0.85f),
                radius = signalRingRadius.coerceAtLeast(radius * 0.24f),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Center dBm badge
        Surface(
            shape = RoundedCornerShape(50),
            color = surfaceBg.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$rawRssi",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = zoneColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RssiSparklineCard(
    history: List<Int>, zoneColor: Color, seatLockRssi: Int = -45, modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.radar_sparkline_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (history.isNotEmpty()) {
                    Text(
                        text = "${history.min()} ~ ${history.max()} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                val w = size.width
                val h = size.height
                val minDb = -90f
                val maxDb = -30f

                fun yForRssi(rssi: Float): Float {
                    val norm = ((rssi - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    return h - (norm * (h - 8.dp.toPx())) - 4.dp.toPx()
                }

                // Dashed seat lock threshold line
                val seatY = yForRssi(seatLockRssi.toFloat())
                drawLine(
                    color = gridColor,
                    start = Offset(0f, seatY),
                    end = Offset(w, seatY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )

                // Dashed -65 dBm near line
                val nearY = yForRssi(-65f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, nearY),
                    end = Offset(w, nearY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )

                if (history.size >= 2) {
                    val linePath = Path()
                    val fillPath = Path()
                    val stepX = w / (history.size - 1).coerceAtLeast(1)

                    history.forEachIndexed { idx, rssi ->
                        val x = idx * stepX
                        val y = yForRssi(rssi.toFloat())
                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo((history.size - 1) * stepX, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath, brush = Brush.verticalGradient(
                            colors = listOf(zoneColor.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = linePath, color = zoneColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val lastX = (history.size - 1) * stepX
                    val lastY = yForRssi(history.last().toFloat())
                    drawCircle(color = zoneColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
                } else if (history.size == 1) {
                    val y = yForRssi(history.first().toFloat())
                    drawCircle(color = zoneColor, radius = 4.dp.toPx(), center = Offset(w / 2f, y))
                }
            }
        }
    }
}
