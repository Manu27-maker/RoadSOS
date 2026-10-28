package com.roadsos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF070B12)
val BgTop = Color(0xFF0B1322)
val BgBottom = Color(0xFF05080E)
val Panel = Color(0xFF0E1520)
val PanelRaised = Color(0xFF1A2738)
val PanelTop = Color(0xFF15202F)
val Stroke = Color(0x1AFFFFFF)
val StrokeStrong = Color(0x33FFFFFF)
val TextMain = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)
val TextDim = Color(0xFF7C8CA0)
val Red = Color(0xFFEF4444)
val RedBright = Color(0xFFFF5B5B)
val RedDark = Color(0xFFB91C1C)
val Cyan = Color(0xFF22D3EE)
val CyanSoft = Color(0xFF67E8F9)
val Amber = Color(0xFFF59E0B)
val Orange = Color(0xFFFB923C)
val Green = Color(0xFF34D399)
val GreenSoft = Color(0xFF6EE7B7)

private val Scheme = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = RedDark,
    onPrimaryContainer = Color.White,
    secondary = Cyan,
    onSecondary = Bg,
    tertiary = Amber,
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF334155),
    error = Red,
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun RoadSosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
