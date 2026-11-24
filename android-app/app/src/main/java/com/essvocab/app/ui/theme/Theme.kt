package com.essvocab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GridEmerald = Color(0xFF0A6C52)
val GridEmeraldLight = Color(0xFF34D399)
val GridEmeraldContainer = Color(0xFFD1FAE5)
val GridEmeraldDarkContainer = Color(0xFF064E3B)

val EnergyAmber = Color(0xFFD97706)
val EnergyAmberContainer = Color(0xFFFEF3C7)
val EnergyBlue = Color(0xFF0284C7)
val EnergyRed = Color(0xFFDC2626)

private val LightColorScheme = lightColorScheme(
    primary = GridEmerald,
    onPrimary = Color.White,
    primaryContainer = GridEmeraldContainer,
    onPrimaryContainer = Color(0xFF05382B),
    secondary = EnergyAmber,
    onSecondary = Color.White,
    secondaryContainer = EnergyAmberContainer,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = EnergyBlue,
    background = Color(0xFFF6F9F8),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9F1EE),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
)

private val DarkColorScheme = darkColorScheme(
    primary = GridEmeraldLight,
    onPrimary = Color(0xFF022C22),
    primaryContainer = GridEmeraldDarkContainer,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0B1210),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF121D1A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1D2C27),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
)

private val EssTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun EssVocabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = EssTypography,
        content = content,
    )
}
