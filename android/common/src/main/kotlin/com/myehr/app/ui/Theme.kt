package com.myehr.app.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
private fun style(f: FontFamily, size: Int, line: Int, weight: Int) = TextStyle(fontFamily = f, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp)

/** Generous line heights: Hebrew reads better a bit looser, and it helps older eyes. */
private fun type(f: FontFamily) = Typography(
    displaySmall = style(f, 34, 42, 600),       // big numbers on cards
    headlineSmall = style(f, 24, 32, 600),
    titleLarge = style(f, 21, 28, 600),
    titleMedium = style(f, 17, 24, 600),
    titleSmall = style(f, 15, 22, 600),
    bodyLarge = style(f, 17, 26, 400),
    bodyMedium = style(f, 15, 23, 400),
    bodySmall = style(f, 13, 19, 400),
    labelLarge = style(f, 15, 20, 500),
    labelMedium = style(f, 13, 18, 500),
    labelSmall = style(f, 12, 16, 500),
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


/** A complete look: colors (light + dark) and card roundness. */
class Look(val name: String, val light: ColorScheme, val dark: ColorScheme, val lightStatus: StatusColors, val darkStatus: StatusColors, val cardRadius: Int)

val PineLook = Look("אורן", Light, Dark, LightStatus, DarkStatus, 20)

val OceanLook = Look(
    "ים",
    lightColorScheme(
        primary = Color(0xFF24476B), onPrimary = Color.White, primaryContainer = Color(0xFFDCE7F2), onPrimaryContainer = Color(0xFF0F2740),
        secondary = Color(0xFFE07A5F), onSecondary = Color.White, secondaryContainer = Color(0xFFFBE3DB), onSecondaryContainer = Color(0xFF5A2213),
        background = Color(0xFFF6F7F5), onBackground = Color(0xFF1F2328), surface = Color(0xFFF6F7F5), onSurface = Color(0xFF1F2328),
        surfaceVariant = Color(0xFFE8ECEE), onSurfaceVariant = Color(0xFF5B636B),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFE8ECEE), surfaceContainerHighest = Color(0xFFE8ECEE),
        outline = Color(0xFFDDE2E6), outlineVariant = Color(0xFFDDE2E6), error = Color(0xFFB3261E),
    ),
    darkColorScheme(
        primary = Color(0xFF9CC2E8), onPrimary = Color(0xFF0B2236), primaryContainer = Color(0xFF213A55), onPrimaryContainer = Color(0xFFD6E6F7),
        secondary = Color(0xFFF2A58F), onSecondary = Color(0xFF3B140A), secondaryContainer = Color(0xFF4D2A20), onSecondaryContainer = Color(0xFFFFDDD2),
        background = Color(0xFF15181C), onBackground = Color(0xFFE8EBEE), surface = Color(0xFF15181C), onSurface = Color(0xFFE8EBEE),
        surfaceVariant = Color(0xFF262B31), onSurfaceVariant = Color(0xFFA9B1B9),
        surfaceContainerLowest = Color(0xFF1E2227), surfaceContainerLow = Color(0xFF1E2227), surfaceContainer = Color(0xFF1E2227),
        surfaceContainerHigh = Color(0xFF262B31), surfaceContainerHighest = Color(0xFF262B31),
        outline = Color(0xFF343A41), outlineVariant = Color(0xFF343A41), error = Color(0xFFF2B8B5),
    ),
    StatusColors(Color(0xFFB3261E), Color(0xFFF9DEDC), Color(0xFF2E7D5B), Color(0xFFDDEFE6), Color(0xFFE07A5F), Color(0xFFFBE3DB), Color(0xFF5B636B)),
    StatusColors(Color(0xFFF2B8B5), Color(0xFF4A1F1D), Color(0xFF8FD1B2), Color(0xFF1E3A2E), Color(0xFFF2A58F), Color(0xFF4D2A20), Color(0xFFA9B1B9)),
    14,
)

val OliveLook = Look(
    "זית",
    lightColorScheme(
        primary = Color(0xFF55672F), onPrimary = Color.White, primaryContainer = Color(0xFFE4EAD2), onPrimaryContainer = Color(0xFF243012),
        secondary = Color(0xFFC8693F), onSecondary = Color.White, secondaryContainer = Color(0xFFF7DFD2), onSecondaryContainer = Color(0xFF52240F),
        background = Color(0xFFFAF5EC), onBackground = Color(0xFF2A2620), surface = Color(0xFFFAF5EC), onSurface = Color(0xFF2A2620),
        surfaceVariant = Color(0xFFEFE7D8), onSurfaceVariant = Color(0xFF6A6254),
        surfaceContainerLowest = Color(0xFFFFFCF6), surfaceContainerLow = Color(0xFFFFFCF6), surfaceContainer = Color(0xFFFFFCF6),
        surfaceContainerHigh = Color(0xFFEFE7D8), surfaceContainerHighest = Color(0xFFEFE7D8),
        outline = Color(0xFFE6DCC9), outlineVariant = Color(0xFFE6DCC9), error = Color(0xFFA8322A),
    ),
    darkColorScheme(
        primary = Color(0xFFBFCF94), onPrimary = Color(0xFF1E2809), primaryContainer = Color(0xFF34401C), onPrimaryContainer = Color(0xFFE2EBC6),
        secondary = Color(0xFFEFA07D), onSecondary = Color(0xFF3E1806), secondaryContainer = Color(0xFF4F2A18), onSecondaryContainer = Color(0xFFFFDCCB),
        background = Color(0xFF1A1914), onBackground = Color(0xFFEEE9DE), surface = Color(0xFF1A1914), onSurface = Color(0xFFEEE9DE),
        surfaceVariant = Color(0xFF302D25), onSurfaceVariant = Color(0xFFB4AD9F),
        surfaceContainerLowest = Color(0xFF24221C), surfaceContainerLow = Color(0xFF24221C), surfaceContainer = Color(0xFF24221C),
        surfaceContainerHigh = Color(0xFF302D25), surfaceContainerHighest = Color(0xFF302D25),
        outline = Color(0xFF3F3B31), outlineVariant = Color(0xFF3F3B31), error = Color(0xFFF0A49A),
    ),
    StatusColors(Color(0xFFA8322A), Color(0xFFF6DAD6), Color(0xFF55672F), Color(0xFFE4EAD2), Color(0xFFC8693F), Color(0xFFF7DFD2), Color(0xFF6A6254)),
    StatusColors(Color(0xFFF0A49A), Color(0xFF4A231E), Color(0xFFBFCF94), Color(0xFF34401C), Color(0xFFEFA07D), Color(0xFF4F2A18), Color(0xFFB4AD9F)),
    28,
)

/** For weaker eyesight: near-black on white (or white on black), dark outlines, strong status colors. */
val ContrastLook = Look(
    "ניגודיות גבוהה",
    lightColorScheme(
        primary = Color(0xFF0B3D91), onPrimary = Color.White, primaryContainer = Color(0xFFD6E2FF), onPrimaryContainer = Color(0xFF001A4D),
        secondary = Color(0xFF8A4300), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE0C2), onSecondaryContainer = Color(0xFF2E1500),
        background = Color.White, onBackground = Color(0xFF111111), surface = Color.White, onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF333333),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEDEDED), surfaceContainerHighest = Color(0xFFEDEDED),
        outline = Color(0xFF5C5C5C), outlineVariant = Color(0xFF5C5C5C), error = Color(0xFFA0001C),
    ),
    darkColorScheme(
        primary = Color(0xFFA8C4FF), onPrimary = Color(0xFF001A4D), primaryContainer = Color(0xFF0F2E66), onPrimaryContainer = Color.White,
        secondary = Color(0xFFFFC489), onSecondary = Color(0xFF2E1500), secondaryContainer = Color(0xFF4D2600), onSecondaryContainer = Color.White,
        background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White,
        surfaceVariant = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceContainerLowest = Color(0xFF0D0D0D), surfaceContainerLow = Color(0xFF0D0D0D), surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerHigh = Color(0xFF1F1F1F), surfaceContainerHighest = Color(0xFF1F1F1F),
        outline = Color(0xFFB0B0B0), outlineVariant = Color(0xFFB0B0B0), error = Color(0xFFFFB3B3),
    ),
    StatusColors(Color(0xFFA0001C), Color(0xFFFFDADB), Color(0xFF1B5E20), Color(0xFFD9F2DB), Color(0xFF8A4300), Color(0xFFFFE0C2), Color(0xFF333333)),
    StatusColors(Color(0xFFFFB3B3), Color(0xFF4D0010), Color(0xFFA5E8AA), Color(0xFF0D3311), Color(0xFFFFC489), Color(0xFF4D2600), Color(0xFFE0E0E0)),
    16,
)

val Looks: List<Look> get() = listOf(PineLook, OceanLook, OliveLook, ContrastLook)

@Composable
fun MyEhrTheme(textSize: TextSize = TextSize.NORMAL, dark: Boolean = isSystemInDarkTheme(), look: Look = PineLook, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(d.density, d.fontScale * textSize.scale),
        LocalStatus provides if (dark) look.darkStatus else look.lightStatus,
    ) {
        val shapes = Shape.copy(medium = RoundedCornerShape(look.cardRadius.dp), large = RoundedCornerShape((look.cardRadius + 8).dp))
        MaterialTheme(colorScheme = if (dark) look.dark else look.light, typography = type(PlatformFonts.family), shapes = shapes, content = content)
    }
}
