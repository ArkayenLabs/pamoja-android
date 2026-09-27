package com.pamoja.app.ui.kit

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * The rebuild's theme. Screens read [Kit.colors] and [Kit.type].
 *
 * MaterialTheme is set underneath only as plumbing, so ripples, text
 * selection and the default text style match the kit. Kit screens do not use
 * Material components for anything visible (REBUILD_PLAN.md §5).
 *
 * Never sets bar colours: the activity is edge to edge, so the bars are
 * transparent and only their icon colour follows the theme. The deprecated
 * Window.setStatusBarColor is what Play Console flagged on 1.0.12.
 */
@Composable
fun KitTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkKitColors else LightKitColors
    val devanagari = LocalConfiguration.current.locales[0].language in DevanagariLanguages
    val type = remember(devanagari) { kitType(devanagari) }

    // LocalActivity, never a cast of a Context: that throws on a wrapped one.
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.button, onPrimary = colors.onButton,
            background = colors.page, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.fieldBorder, outlineVariant = colors.line,
            error = colors.danger, scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.button, onPrimary = colors.onButton,
            background = colors.page, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.fieldBorder, outlineVariant = colors.line,
            error = colors.danger, scrim = colors.scrim,
        )
    }

    CompositionLocalProvider(
        LocalKitColors provides colors,
        LocalKitType provides type,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            // bodyLarge is what MaterialTheme hands to every Text by default.
            typography = Typography(
                bodyLarge = type.body,
                bodyMedium = type.body,
                labelLarge = type.buttonSmall,
            ),
        ) {
            // Material's default content colour is black, which vanished on
            // the dark page in the first gallery run. Ink is the default here.
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

/** Accessors, in the style of MaterialTheme.colorScheme. */
object Kit {
    val colors: KitColors
        @Composable @ReadOnlyComposable get() = LocalKitColors.current

    val type: KitType
        @Composable @ReadOnlyComposable get() = LocalKitType.current
}

/** Corner radii as drawn. Everything round is a pill or one of these. */
object KitRadius {
    /** The lilac week card and welcome header. */
    val hero = 28.dp

    /** Every other card. */
    val card = 24.dp

    /** Text fields. */
    val field = 16.dp
}

internal val PillShape = RoundedCornerShape(percent = 50)
internal val CardShape = RoundedCornerShape(KitRadius.card)
internal val HeroShape = RoundedCornerShape(KitRadius.hero)
internal val FieldShape = RoundedCornerShape(KitRadius.field)
