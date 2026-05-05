package com.pamoja.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Pamoja is dark-mode only.
// Every Material3 role is mapped to our design system tokens.
private val PamojaDarkColorScheme = darkColorScheme(
    // ── Brand ──────────────────────────────────────────────────────────────
    primary                = PamojaIndigo,
    onPrimary              = PamojaWhite,
    primaryContainer       = PamojaIndigoSubtle,
    onPrimaryContainer     = PamojaIndigoLight,

    // ── Backgrounds ────────────────────────────────────────────────────────
    background             = PamojaBackground,
    onBackground           = PamojaTextPrimary,

    // ── Surfaces (cards, sheets, dialogs) ──────────────────────────────────
    surface                = PamojaSurface,
    onSurface              = PamojaTextPrimary,
    surfaceVariant         = PamojaSurfaceVariant,
    onSurfaceVariant       = PamojaTextSecondary,

    // ── Outlines / dividers ────────────────────────────────────────────────
    outline                = PamojaBorder,
    outlineVariant         = PamojaDivider,

    // ── Secondary (used for success / achieve states) ─────────────────────
    secondary              = PamojaGreen,
    onSecondary            = PamojaBackground,
    secondaryContainer     = PamojaGreenSubtle,
    onSecondaryContainer   = PamojaGreen,

    // ── Tertiary (used for energy / amber states) ─────────────────────────
    tertiary               = PamojaAmber,
    onTertiary             = PamojaBackground,
    tertiaryContainer      = PamojaAmberSubtle,
    onTertiaryContainer    = PamojaAmber,

    // ── Errors ─────────────────────────────────────────────────────────────
    error                  = PamojaRed,
    onError                = PamojaWhite,
    errorContainer         = PamojaRedSubtle,
    onErrorContainer       = PamojaRed,

    // ── Scrim (modals/drawers backdrop) ────────────────────────────────────
    scrim                  = Color(0x99000000)  // 60% black — strong enough to isolate content
)

@Composable
fun PamojaTheme(content: @Composable () -> Unit) {
    // Always dark — no system theme toggle.
    val colorScheme = PamojaDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Make status bar fully transparent so our dark background shows through edge-to-edge
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()

            // Light icons = false means we want light/white icons on our dark background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PamojaTypography,
        content     = content
    )
}