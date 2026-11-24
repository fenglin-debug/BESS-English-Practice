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

val GoogleBlue = Color(0xFF1A73E8)
val GoogleBlueSoft = Color(0xFFE8F0FE)
val GoogleBlueOnSoft = Color(0xFF174EA6)

val GoogleGreen = Color(0xFF1E8E3E)
val GoogleGreenSoft = Color(0xFFE6F4EA)
val GoogleGreenOnSoft = Color(0xFF137333)

val GoogleYellow = Color(0xFFF9AB00)
val GoogleYellowSoft = Color(0xFFFEF7E0)
val GoogleYellowOnSoft = Color(0xFFB06000)

val GoogleRed = Color(0xFFD93025)
val GoogleRedSoft = Color(0xFFFCE8E6)
val GoogleRedOnSoft = Color(0xFFA50E0E)

val GoogleCanvasLight = Color(0xFFF8FAFD)
val GoogleSurfaceVariantLight = Color(0xFFF0F4F9)
val GoogleOutlineLight = Color(0xFFE0E3EB)

val GoogleFourColors = listOf(GoogleBlue, GoogleGreen, GoogleYellow, GoogleRed)
val GoogleFourSoftColors = listOf(GoogleBlueSoft, GoogleGreenSoft, GoogleYellowSoft, GoogleRedSoft)
val GoogleFourOnSoftColors = listOf(GoogleBlueOnSoft, GoogleGreenOnSoft, GoogleYellowOnSoft, GoogleRedOnSoft)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = GoogleBlueSoft,
    onPrimaryContainer = GoogleBlueOnSoft,
    secondary = GoogleGreen,
    onSecondary = Color.White,
    secondaryContainer = GoogleGreenSoft,
    onSecondaryContainer = GoogleGreenOnSoft,
    tertiary = GoogleYellow,
    onTertiary = Color(0xFF3F2300),
    tertiaryContainer = GoogleYellowSoft,
    onTertiaryContainer = GoogleYellowOnSoft,
    error = GoogleRed,
    onError = Color.White,
    errorContainer = GoogleRedSoft,
    onErrorContainer = GoogleRedOnSoft,
    background = GoogleCanvasLight,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = GoogleSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF5F6368),
    outline = GoogleOutlineLight,
    outlineVariant = Color(0xFFECEEF4),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF81C995),
    onSecondary = Color(0xFF073818),
    secondaryContainer = Color(0xFF0F5223),
    onSecondaryContainer = Color(0xFFC4EED0),
    tertiary = Color(0xFFFDD663),
    onTertiary = Color(0xFF3F2300),
    tertiaryContainer = Color(0xFF5F3C00),
    onTertiaryContainer = Color(0xFFFEF0C7),
    error = Color(0xFFF28B82),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFCE8E6),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1F20),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF282A2C),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4043),
    outlineVariant = Color(0xFF2D2F31),
)

private val EssTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
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
