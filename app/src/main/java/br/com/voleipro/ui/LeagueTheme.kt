package br.com.voleipro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.voleipro.domain.Appearance
import br.com.voleipro.domain.Preferences
import br.com.voleipro.domain.ThemePalette

private val ShieldNavy = Color(0xFF0C2D5B)
private val ShieldMagenta = Color(0xFFC919B5)
private val ShieldYellow = Color(0xFFF6EC13)
private val ShieldPink = Color(0xFFF9B9F0)

private val ShieldLight = lightColorScheme(
    primary = ShieldNavy, onPrimary = Color.White,
    primaryContainer = ShieldPink, onPrimaryContainer = ShieldNavy,
    secondary = ShieldMagenta, onSecondary = Color.White,
    secondaryContainer = ShieldPink, onSecondaryContainer = ShieldNavy,
    tertiary = Color(0xFF5C5400), onTertiary = Color.White,
    tertiaryContainer = ShieldYellow, onTertiaryContainer = ShieldNavy,
    background = Color(0xFFF9FAFF), onBackground = ShieldNavy,
    surface = Color(0xFFF9FAFF), onSurface = ShieldNavy, surfaceTint = ShieldNavy,
    surfaceContainerLow = Color(0xFFF2F5FC), surfaceContainer = Color(0xFFEAF0FB),
    surfaceContainerHigh = Color(0xFFE2EAF6), surfaceContainerHighest = Color(0xFFDAE3F1),
    surfaceVariant = Color(0xFFE3EAF5), onSurfaceVariant = Color(0xFF425571),
    inverseSurface = ShieldNavy, inverseOnSurface = Color.White, inversePrimary = ShieldPink,
    outline = Color(0xFF62728A), outlineVariant = Color(0xFFBCC9DE),
)

private val ShieldDark = darkColorScheme(
    primary = ShieldPink, onPrimary = ShieldNavy,
    primaryContainer = ShieldMagenta, onPrimaryContainer = Color.White,
    secondary = ShieldPink, onSecondary = ShieldNavy,
    secondaryContainer = Color(0xFF673360), onSecondaryContainer = Color(0xFFFFDEF8),
    tertiary = ShieldYellow, onTertiary = ShieldNavy,
    tertiaryContainer = ShieldYellow, onTertiaryContainer = ShieldNavy,
    background = Color(0xFF0B1627), onBackground = Color(0xFFF0F3FF),
    surface = ShieldNavy, onSurface = Color(0xFFF0F3FF), surfaceTint = ShieldPink,
    surfaceContainerLow = Color(0xFF13243A), surfaceContainer = Color(0xFF182C46),
    surfaceContainerHigh = Color(0xFF213851), surfaceContainerHighest = Color(0xFF29445E),
    surfaceVariant = Color(0xFF2B425E), onSurfaceVariant = Color(0xFFC9D5E8),
    inverseSurface = Color(0xFFF0F3FF), inverseOnSurface = ShieldNavy, inversePrimary = ShieldMagenta,
    outline = Color(0xFF91A5BE), outlineVariant = Color(0xFF52677F),
)

private val ImprovisadasNeon = Color(0xFFFF1675)
private val ImprovisadasCharcoal = Color(0xFF121215)
private val ImprovisadasSalmon = Color(0xFFF24976)
private val ImprovisadasGraphite = Color(0xFF2A2B30)

private val ImprovisadasLight = lightColorScheme(
    primary = Color(0xFFA7154A), onPrimary = Color.White,
    primaryContainer = ImprovisadasNeon, onPrimaryContainer = ImprovisadasCharcoal,
    secondary = Color(0xFF9A3054), onSecondary = Color.White,
    secondaryContainer = ImprovisadasSalmon, onSecondaryContainer = ImprovisadasCharcoal,
    tertiary = Color(0xFFA12153), onTertiary = Color.White,
    tertiaryContainer = ImprovisadasSalmon, onTertiaryContainer = ImprovisadasCharcoal,
    background = Color(0xFFFFF8FA), onBackground = ImprovisadasCharcoal,
    surface = Color(0xFFFFF8FA), onSurface = ImprovisadasCharcoal, surfaceTint = Color(0xFFA7154A),
    surfaceContainerLow = Color(0xFFFFF0F4), surfaceContainer = Color(0xFFFCE6ED),
    surfaceContainerHigh = Color(0xFFF7DDE7), surfaceContainerHighest = Color(0xFFF0D5DF),
    surfaceVariant = Color(0xFFF7DBE6), onSurfaceVariant = ImprovisadasGraphite,
    inverseSurface = ImprovisadasCharcoal, inverseOnSurface = Color(0xFFFFF0F4), inversePrimary = ImprovisadasSalmon,
    outline = Color(0xFF826371), outlineVariant = Color(0xFFDEC2CE),
)

private val ImprovisadasDark = darkColorScheme(
    primary = ImprovisadasNeon, onPrimary = ImprovisadasCharcoal,
    primaryContainer = ImprovisadasNeon, onPrimaryContainer = ImprovisadasCharcoal,
    secondary = ImprovisadasSalmon, onSecondary = ImprovisadasCharcoal,
    secondaryContainer = Color(0xFF622A41), onSecondaryContainer = Color(0xFFFFD9E6),
    tertiary = ImprovisadasSalmon, onTertiary = ImprovisadasCharcoal,
    tertiaryContainer = ImprovisadasSalmon, onTertiaryContainer = ImprovisadasCharcoal,
    background = ImprovisadasCharcoal, onBackground = Color(0xFFFFF0F4),
    surface = ImprovisadasCharcoal, onSurface = Color(0xFFFFF0F4), surfaceTint = ImprovisadasNeon,
    surfaceContainerLow = Color(0xFF1D181D), surfaceContainer = Color(0xFF271D23),
    surfaceContainerHigh = ImprovisadasGraphite, surfaceContainerHighest = Color(0xFF36373C),
    surfaceVariant = ImprovisadasGraphite, onSurfaceVariant = Color(0xFFE3CCD7),
    inverseSurface = Color(0xFFFFF0F4), inverseOnSurface = ImprovisadasCharcoal, inversePrimary = Color(0xFFA7154A),
    outline = Color(0xFFAB8D9A), outlineVariant = Color(0xFF624955),
)

private val BenaconGold = Color(0xFFF9D62E)
private val BenaconNavy = Color(0xFF0E1A2B)
private val BenaconDeepBlack = Color(0xFF070D17)
private val BenaconSlate = Color(0xFF243B55)

private val BenaconLight = ShieldLight.copy(
    primary = BenaconNavy, onPrimary = Color.White,
    primaryContainer = BenaconGold, onPrimaryContainer = BenaconNavy,
    secondary = Color(0xFF625200), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFED9E), onSecondaryContainer = BenaconNavy,
    tertiary = Color(0xFF625200), onTertiary = Color.White,
    tertiaryContainer = BenaconGold, onTertiaryContainer = BenaconNavy,
    onBackground = BenaconNavy, onSurface = BenaconNavy, surfaceTint = BenaconNavy, onSurfaceVariant = BenaconSlate,
    inverseSurface = BenaconDeepBlack, inverseOnSurface = Color.White, inversePrimary = BenaconGold,
)

private val BenaconDark = ShieldDark.copy(
    primary = BenaconGold, onPrimary = BenaconNavy,
    primaryContainer = BenaconGold, onPrimaryContainer = BenaconNavy,
    secondary = Color(0xFFFFE47D), onSecondary = BenaconNavy,
    secondaryContainer = Color(0xFF4D420F), onSecondaryContainer = Color(0xFFFFED9E),
    tertiary = BenaconGold, onTertiary = BenaconNavy,
    tertiaryContainer = BenaconGold, onTertiaryContainer = BenaconNavy,
    background = BenaconDeepBlack, surface = BenaconNavy, surfaceTint = BenaconGold,
    surfaceContainerHigh = BenaconSlate, surfaceVariant = BenaconSlate,
    inverseOnSurface = BenaconNavy, inversePrimary = Color(0xFF625200),
)

private val HighContrast = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    primaryContainer = Color.White, onPrimaryContainer = Color.Black,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black, surfaceTint = Color.Black,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    secondary = Color.Black, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E4), onSecondaryContainer = Color.Black,
    tertiary = Color.Black, onTertiary = Color.White,
    tertiaryContainer = Color.Black, onTertiaryContainer = Color.White,
    surfaceVariant = Color(0xFFF0F0F0), onSurfaceVariant = Color.Black,
    outline = Color.Black, outlineVariant = Color.Black,
)

private fun ThemePalette.lightColors(): ColorScheme = when (this) {
    ThemePalette.SHIELD -> ShieldLight
    ThemePalette.IMPROVISADAS -> ImprovisadasLight
    ThemePalette.BENACON -> BenaconLight
}

internal fun ThemePalette.swatches(): List<Color> = when (this) {
    ThemePalette.SHIELD -> listOf(ShieldNavy, ShieldMagenta, ShieldYellow, ShieldPink)
    ThemePalette.IMPROVISADAS -> listOf(ImprovisadasNeon, ImprovisadasCharcoal, ImprovisadasSalmon, ImprovisadasGraphite)
    ThemePalette.BENACON -> listOf(BenaconGold, BenaconNavy, BenaconDeepBlack, BenaconSlate)
}

@Composable
internal fun leagueColors(preferences: Preferences, fullscreen: Boolean): ColorScheme {
    val light = preferences.palette.lightColors()
    if (fullscreen) return light.copy(background = Color.White, surface = Color.White)
    if (preferences.appearance == Appearance.CONTRAST) return HighContrast
    val dark = preferences.appearance == Appearance.DARK ||
        (preferences.appearance == Appearance.SYSTEM && isSystemInDarkTheme())
    if (!dark) return light
    return when (preferences.palette) {
        ThemePalette.SHIELD -> ShieldDark
        ThemePalette.IMPROVISADAS -> ImprovisadasDark
        ThemePalette.BENACON -> BenaconDark
    }
}
