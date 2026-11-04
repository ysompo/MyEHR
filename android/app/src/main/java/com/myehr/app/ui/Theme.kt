package com.myehr.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myehr.app.R

/*
 * MyEHR design tokens.
 * Warm paper surfaces, pine-green primary, saffron accent. Status is never color-only:
 * every status also has an arrow and a word (see StatusPill).
 */

// ---------- color ----------
private object Palette {
    // light
    val paper = Color(0xFFFBF6EE)        // background
    val card = Color(0xFFFFFDF9)         // card surface
    val sand = Color(0xFFF1E8DA)         // tinted surface, chips
    val ink = Color(0xFF2B2622)          // text
    val inkSoft = Color(0xFF6B625A)      // secondary text
    val line = Color(0xFFE5DACB)         // dividers, outlines
    val pine = Color(0xFF1F5C55)         // primary
    val pineSoft = Color(0xFFD5E8E3)     // primary container
    val saffron = Color(0xFFE3A23B)      // accent
    val saffronSoft = Color(0xFFFBE9C8)
    val brick = Color(0xFFB8432F)        // out of range
    val brickSoft = Color(0xFFF8DDD5)
    val sage = Color(0xFF4F7F5E)         // in range
    val sageSoft = Color(0xFFDDEBDF)
    // dark
    val night = Color(0xFF1B1916)
    val nightCard = Color(0xFF262320)
    val nightSand = Color(0xFF332F2A)
    val moon = Color(0xFFF1EBE2)
    val moonSoft = Color(0xFFB5ACA1)
    val nightLine = Color(0xFF423C35)
    val pineLight = Color(0xFF8CCBBE)
    val pineDeep = Color(0xFF1E3F3A)
    val saffronLight = Color(0xFFF0C067)
    val brickLight = Color(0xFFF08F7A)
    val brickDeep = Color(0xFF4A2820)
    val sageLight = Color(0xFF94C4A1)
    val sageDeep = Color(0xFF243A2B)
}

/** Colors Material doesn't have a slot for. */
@Immutable
data class StatusColors(val high: Color, val highBg: Color, val ok: Color, val okBg: Color, val accent: Color, val accentBg: Color, val soft: Color)

private val LightStatus = StatusColors(Palette.brick, Palette.brickSoft, Palette.sage, Palette.sageSoft, Palette.saffron, Palette.saffronSoft, Palette.inkSoft)
private val DarkStatus = StatusColors(Palette.brickLight, Palette.brickDeep, Palette.sageLight, Palette.sageDeep, Palette.saffronLight, Color(0xFF4A3A1E), Palette.moonSoft)
val LocalStatus = staticCompositionLocalOf { LightStatus }

private val Light: ColorScheme = lightColorScheme(
    primary = Palette.pine, onPrimary = Color.White,
    primaryContainer = Palette.pineSoft, onPrimaryContainer = Color(0xFF0E3631),
    secondary = Palette.saffron, onSecondary = Palette.ink,
    secondaryContainer = Palette.saffronSoft, onSecondaryContainer = Color(0xFF4A330B),
    background = Palette.paper, onBackground = Palette.ink,
    surface = Palette.paper, onSurface = Palette.ink,
    surfaceVariant = Palette.sand, onSurfaceVariant = Palette.inkSoft,
    surfaceContainerLowest = Palette.card, surfaceContainerLow = Palette.card,
    surfaceContainer = Palette.card, surfaceContainerHigh = Palette.sand, surfaceContainerHighest = Palette.sand,
    outline = Palette.line, outlineVariant = Palette.line,
    error = Palette.brick, onError = Color.White, errorContainer = Palette.brickSoft, onErrorContainer = Color(0xFF5A1A0E),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Palette.pineLight, onPrimary = Color(0xFF0B2B27),
    primaryContainer = Palette.pineDeep, onPrimaryContainer = Color(0xFFCDEDE6),
    secondary = Palette.saffronLight, onSecondary = Color(0xFF2E2008),
    secondaryContainer = Color(0xFF4A3A1E), onSecondaryContainer = Color(0xFFF8E3BC),
    background = Palette.night, onBackground = Palette.moon,
    surface = Palette.night, onSurface = Palette.moon,
    surfaceVariant = Palette.nightSand, onSurfaceVariant = Palette.moonSoft,
    surfaceContainerLowest = Palette.nightCard, surfaceContainerLow = Palette.nightCard,
    surfaceContainer = Palette.nightCard, surfaceContainerHigh = Palette.nightSand, surfaceContainerHighest = Palette.nightSand,
    outline = Palette.nightLine, outlineVariant = Palette.nightLine,
    error = Palette.brickLight, onError = Color(0xFF3A120A), errorContainer = Palette.brickDeep, onErrorContainer = Color(0xFFFFDAD2),
)

// ---------- type ----------
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun rubik(w: Int) = Font(R.font.rubik_var, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700))

private fun style(size: Int, line: Int, weight: Int) = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp)

/** Generous line heights: Hebrew with niqqud-free text still reads better a bit looser, and it helps older eyes. */
private val Type = Typography(
    displaySmall = style(34, 42, 600),       // big numbers on cards
    headlineSmall = style(24, 32, 600),
    titleLarge = style(21, 28, 600),
    titleMedium = style(17, 24, 600),
    titleSmall = style(15, 22, 600),
    bodyLarge = style(17, 26, 400),
    bodyMedium = style(15, 23, 400),
    bodySmall = style(13, 19, 400),
    labelLarge = style(15, 20, 500),
    labelMedium = style(13, 18, 500),
    labelSmall = style(12, 16, 500),
)

// ---------- shape ----------
private val Shape = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),   // cards
    large = RoundedCornerShape(28.dp),    // sheets, ask box
    extraLarge = RoundedCornerShape(32.dp),
)

// ---------- spacing ----------
object Space { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp }

// ---------- user text size (on top of the phone's own setting) ----------
enum class TextSize(val label: String, val scale: Float) { NORMAL("רגיל", 1f), LARGE("גדול", 1.15f), XLARGE("גדול מאוד", 1.3f) }

object UiPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("myehr_ui", Context.MODE_PRIVATE)
    fun textSize(c: Context) = runCatching { TextSize.valueOf(prefs(c).getString("text_size", null)!!) }.getOrDefault(TextSize.NORMAL)
    fun setTextSize(c: Context, t: TextSize) = prefs(c).edit().putString("text_size", t.name).apply()
}

@Composable
fun MyEhrTheme(textSize: TextSize = TextSize.NORMAL, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(d.density, d.fontScale * textSize.scale),
        LocalStatus provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
    }
}
