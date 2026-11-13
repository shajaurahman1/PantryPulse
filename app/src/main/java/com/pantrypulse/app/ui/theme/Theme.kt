package com.pantrypulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantrypulse.app.R

// Palette from the PantryPulse design: warm paper, near-black ink, one loud orange.
val Paper = Color(0xFFF2EFE9)
val Card = Color(0xFFFFFFFF)
val Ink = Color(0xFF17140F)
val Pulse = Color(0xFFFF5A2C)
val Peach = Color(0xFFFFD6C7)
val PeachSoft = Color(0xFFFFEDE6)
val Muted = Color(0xFF7A7268)
val Hairline = Color(0xFFE4DFD6)
val Fresh = Color(0xFF2F7D5B)

val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

/** Extra colours the design uses that Material's scheme has no slot for. */
@Immutable
data class PulseColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val onInk: Color,
    val accent: Color,
    val peach: Color,
    val peachSoft: Color,
    val muted: Color,
    val hairline: Color,
    val fresh: Color,
)

private val LightPulse = PulseColors(Paper, Card, Ink, Paper, Pulse, Peach, PeachSoft, Muted, Hairline, Fresh)
private val DarkPulse = PulseColors(
    paper = Color(0xFF14120F), card = Color(0xFF1F1C18), ink = Color(0xFFF2EFE9), onInk = Color(0xFF14120F),
    accent = Pulse, peach = Color(0xFF5A2A1C), peachSoft = Color(0xFF33201A), muted = Color(0xFFA59D92),
    hairline = Color(0xFF34302A), fresh = Color(0xFF6CC79D),
)

val LocalPulseColors = staticCompositionLocalOf { LightPulse }

object PulseTheme {
    val colors: PulseColors @Composable get() = LocalPulseColors.current
}

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Peach,
    onPrimaryContainer = Ink,
    secondary = Pulse,
    onSecondary = Color.White,
    secondaryContainer = Peach,
    onSecondaryContainer = Ink,
    tertiary = Fresh,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9EEE3),
    onTertiaryContainer = Color(0xFF0E3B28),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E4DC),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFF7F5F1),
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Color(0xFFE9E4DC),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFCFC8BD),
    outlineVariant = Hairline,
    error = Color(0xFFC8321E),
)

private val DarkColors = darkColorScheme(
    primary = DarkPulse.ink,
    onPrimary = DarkPulse.onInk,
    primaryContainer = DarkPulse.peach,
    onPrimaryContainer = DarkPulse.ink,
    secondary = Pulse,
    onSecondary = Color.White,
    secondaryContainer = DarkPulse.peach,
    onSecondaryContainer = DarkPulse.ink,
    tertiary = DarkPulse.fresh,
    background = DarkPulse.paper,
    onBackground = DarkPulse.ink,
    surface = DarkPulse.paper,
    onSurface = DarkPulse.ink,
    surfaceVariant = Color(0xFF2A2621),
    onSurfaceVariant = DarkPulse.muted,
    surfaceContainerLowest = DarkPulse.card,
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainer = DarkPulse.card,
    surfaceContainerHigh = DarkPulse.card,
    surfaceContainerHighest = Color(0xFF2A2621),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF4A453D),
    outlineVariant = DarkPulse.hairline,
    error = Color(0xFFFF8A75),
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.ExtraBold, tracking: Double = -0.03) = TextStyle(
    fontFamily = Bricolage, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (size * tracking).sp,
)
private fun mono(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = DmMono, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

private val AppTypography = Typography(
    displayLarge = display(52, 54),
    displayMedium = display(44, 46),
    displaySmall = display(38, 40),
    headlineLarge = display(32, 36),
    headlineMedium = display(28, 32),
    headlineSmall = display(24, 28, FontWeight.Bold),
    titleLarge = display(22, 28, FontWeight.Bold, -0.01),
    titleMedium = display(18, 24, FontWeight.Bold, -0.01),
    titleSmall = display(15, 20, FontWeight.Bold, 0.0),
    bodyLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = mono(13, 18),
    labelLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = mono(12, 16, FontWeight.Medium, 0.5),
    labelSmall = mono(11, 14, FontWeight.Medium, 1.0),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun PantryPulseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPulseColors provides if (darkTheme) DarkPulse else LightPulse) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Upper-case mono label, as in "THIS WEEK'S PULSE". */
val Eyebrow = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 1.2.sp)
