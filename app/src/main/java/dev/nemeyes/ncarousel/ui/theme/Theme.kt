package dev.nemeyes.ncarousel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Parses Nextcloud OCS theming hex (`#0082c9` or `0082c9`). */
fun parseThemingHexToColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val t = hex.trim()
    val normalized = if (t.startsWith("#")) t else "#$t"
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

private val FallbackLight = lightColorScheme(
    primary = NcBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8DDF2),
    onPrimaryContainer = Color(0xFF00344E),
    secondary = Color(0xFF51606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F5),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF67587A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECDCFF),
    onTertiaryContainer = Color(0xFF221438),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7EEF2),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color.Black,
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF89C9EB),
    onPrimary = Color(0xFF00344E),
    primaryContainer = Color(0xFF004E6E),
    onPrimaryContainer = Color(0xFFB8DDF2),
    secondary = Color(0xFFB8C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF394857),
    onSecondaryContainer = Color(0xFFD3E4F5),
    tertiary = Color(0xFFD2C1E8),
    onTertiary = Color(0xFF382C4C),
    tertiaryContainer = Color(0xFF4F4264),
    onTertiaryContainer = Color(0xFFECDCFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9298),
    outlineVariant = Color(0xFF41484D),
    scrim = Color.Black,
)

@Composable
fun NCarouselTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** OCS `theming.color` from the active instance; null → default Nextcloud blue. */
    instancePrimaryHex: String? = null,
    /** OCS `theming.color-text` for icons/text on primary (e.g. top app bar). */
    instanceOnPrimaryHex: String? = null,
    content: @Composable () -> Unit,
) {
    val seedPrimary = remember(instancePrimaryHex) {
        parseThemingHexToColor(instancePrimaryHex) ?: NcBlue
    }
    val onPrimaryOverride = remember(instanceOnPrimaryHex) {
        parseThemingHexToColor(instanceOnPrimaryHex)
    }
    val colorScheme = remember(seedPrimary, darkTheme, onPrimaryOverride) {
        val dynamic = runCatching {
            if (darkTheme) dynamicDarkColorScheme(seedPrimary) else dynamicLightColorScheme(seedPrimary)
        }.getOrNull()
        val base = dynamic ?: if (darkTheme) FallbackDark else FallbackLight
        if (onPrimaryOverride != null) {
            base.copy(onPrimary = onPrimaryOverride)
        } else {
            base
        }
    }
    val view = LocalView.current
    val primaryLuminance = colorScheme.primary.luminance()
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = primaryLuminance > 0.5f
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
