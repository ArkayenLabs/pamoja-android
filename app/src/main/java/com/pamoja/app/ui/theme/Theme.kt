package com.pamoja.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PamojaBlue,
    onPrimary = PamojaWhite,
    primaryContainer = PamojaBlueLight,
    onPrimaryContainer = PamojaBlueDark,
    background = PamojaBackground,
    onBackground = PamojaTextPrimary,
    surface = PamojaWhite,
    onSurface = PamojaTextPrimary,
    surfaceVariant = PamojaSurface,
    onSurfaceVariant = PamojaTextSecondary,
    outline = PamojaGrayLight,
    error = PamojaRed,
    onError = PamojaWhite,
    errorContainer = PamojaRedLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PamojaBlue,
    onPrimary = PamojaWhite,
    primaryContainer = PamojaBlueDark,
    onPrimaryContainer = PamojaBlueLight,
    background = PamojaDarkBackground,
    onBackground = PamojaWhite,
    surface = PamojaDarkSurface,
    onSurface = PamojaWhite,
    surfaceVariant = PamojaDarkSurfaceVariant,
    onSurfaceVariant = PamojaTextTertiary,
    outline = PamojaGray,
    error = PamojaRed,
    onError = PamojaWhite,
    errorContainer = PamojaRedLight
)

@Composable
fun PamojaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PamojaTypography,
        content = content
    )
}