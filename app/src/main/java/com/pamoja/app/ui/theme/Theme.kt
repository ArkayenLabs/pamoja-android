package com.pamoja.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Both M3 schemes are derived from the Pamoja Design System semantic tokens
// (see PamojaColors). No dynamic color, the brand hue is intentional and must
// not be overridden by the device wallpaper palette.

private val DarkColorScheme = darkColorScheme(
    primary              = PamojaDarkColors.accentPrimary,
    onPrimary            = PamojaDarkColors.textOnBrand,
    primaryContainer     = Brand800,
    onPrimaryContainer   = Brand100,
    secondary            = PamojaDarkColors.accentTeal,
    onSecondary          = PamojaDarkColors.surfaceSunken,
    secondaryContainer   = PamojaDarkColors.accentTealSubtle,
    onSecondaryContainer = Teal300,
    tertiary             = PamojaDarkColors.accentAmber,
    onTertiary           = PamojaDarkColors.surfaceSunken,
    tertiaryContainer    = PamojaDarkColors.accentAmberSubtle,
    onTertiaryContainer  = Amber300,
    background           = PamojaDarkColors.surfaceApp,
    onBackground         = PamojaDarkColors.textPrimary,
    surface              = PamojaDarkColors.surface1,
    onSurface            = PamojaDarkColors.textPrimary,
    surfaceVariant       = PamojaDarkColors.surface2,
    onSurfaceVariant     = PamojaDarkColors.textSecondary,
    surfaceContainerLow  = PamojaDarkColors.surfaceCanvas,
    surfaceContainer     = PamojaDarkColors.surface2,
    surfaceContainerHigh = PamojaDarkColors.surface3,
    outline              = PamojaDarkColors.borderStrong,
    outlineVariant       = PamojaDarkColors.borderDefault,
    error                = PamojaDarkColors.statusDanger,
    onError              = Gray0,
    errorContainer       = PamojaDarkColors.statusDangerSubtle,
    onErrorContainer     = Red300,
    scrim                = PamojaDarkColors.overlay,
)

private val LightColorScheme = lightColorScheme(
    primary              = PamojaLightColors.accentPrimary,
    onPrimary            = PamojaLightColors.textOnBrand,
    primaryContainer     = Brand100,
    onPrimaryContainer   = Brand900,
    secondary            = PamojaLightColors.accentTeal,
    onSecondary          = Gray0,
    secondaryContainer   = PamojaLightColors.accentTealSubtle,
    onSecondaryContainer = Teal700,
    tertiary             = PamojaLightColors.accentAmber,
    onTertiary           = Gray0,
    tertiaryContainer    = PamojaLightColors.accentAmberSubtle,
    onTertiaryContainer  = Amber700,
    background           = PamojaLightColors.surfaceApp,
    onBackground         = PamojaLightColors.textPrimary,
    surface              = PamojaLightColors.surface1,
    onSurface            = PamojaLightColors.textPrimary,
    surfaceVariant       = PamojaLightColors.surfaceSunken,
    onSurfaceVariant     = PamojaLightColors.textSecondary,
    surfaceContainerLow  = Gray50,
    surfaceContainer     = PamojaLightColors.surfaceSunken,
    surfaceContainerHigh = Gray100,
    outline              = PamojaLightColors.borderStrong,
    outlineVariant       = PamojaLightColors.borderDefault,
    error                = PamojaLightColors.statusDanger,
    onError              = Gray0,
    errorContainer       = PamojaLightColors.statusDangerSubtle,
    onErrorContainer     = Red600,
    scrim                = PamojaLightColors.overlay,
)

@Composable
fun PamojaTheme(
    // All screens are migrated onto LocalPamojaColors, so the app now follows the
    // system light/dark setting. Dark remains the brand's native mode.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val pamojaColors = if (darkTheme) PamojaDarkColors else PamojaLightColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPamojaColors provides pamojaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = PamojaTypography,
            shapes      = PamojaShapes,
            content     = content,
        )
    }
}
