package com.pamoja.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// ============================================================================
// PAMOJA. MOTION TOKENS  (from tokens/motion.css)
// Spring-based, never linear. These mirror the design system's documented
// Compose spring() params 1:1 for native handoff.
// ============================================================================

object PamojaMotion {
    // Durations (ms), for tween-based transitions where a spring does not fit.
    const val durationInstant = 100
    const val durationFast = 160
    const val durationBase = 240
    const val durationSlow = 360
    const val durationDeliberate = 520

    // snappy: slight overshoot, quick settle, presses, toggles, small UI.
    fun <T> snappy() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

    // bouncy: pronounced overshoot, streak/badge pops, celebratory motion.
    fun <T> bouncy() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)

    // gentle: smooth deceleration, no overshoot, content reveals, list items.
    fun <T> gentle() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
}
