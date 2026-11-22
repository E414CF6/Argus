package argus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import argus.data.preferences.ThemeMode

@Immutable
data class ArgusSemanticColors(
    val safe: Color,
    val safeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val bleAccent: Color,
    val wifiAccent: Color,
    val radarBeam: Color
)

private val LightSemanticColors = ArgusSemanticColors(
    safe = Color(0xFF0E9F6E),
    safeContainer = Color(0xFFD1FAE5),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerContainer = Color(0xFFFEE2E2),
    bleAccent = Color(0xFF2563EB),
    wifiAccent = Color(0xFF0891B2),
    radarBeam = Color(0xFF0284C7)
)

private val DarkSemanticColors = ArgusSemanticColors(
    safe = Color(0xFF34D399),
    safeContainer = Color(0xFF064E3B),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF451A03),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF450A0A),
    bleAccent = Color(0xFF60A5FA),
    wifiAccent = Color(0xFF22D3EE),
    radarBeam = Color(0xFF38BDF8)
)

val LocalArgusColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F4C81),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E8F8),
    onPrimaryContainer = Color(0xFF072947),
    secondary = Color(0xFF0891B2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF56B4E9),
    onPrimary = Color(0xFF04263D),
    primaryContainer = Color(0xFF0E3A5C),
    onPrimaryContainer = Color(0xFFD6EEFF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF042F38),
    secondaryContainer = Color(0xFF0E4452),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3D2200),
    tertiaryContainer = Color(0xFF4B2B05),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0B111E),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

private val ArgusTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp
    ), titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold
    ), titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold
    ), bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp
    ), labelLarge = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold
    ), labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium
    )
)

@Composable
fun ArgusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalArgusColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme, typography = ArgusTypography, content = content
        )
    }
}
