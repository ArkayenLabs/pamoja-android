package com.pamoja.app.ui.theme

import androidx.compose.ui.unit.dp

// ============================================================================
// PAMOJA. SPACING & LAYOUT TOKENS  (from tokens/spacing.css)
// 4dp base unit. Prefer these over ad-hoc dp literals in screens.
// ============================================================================

object Spacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x7 = 28.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp
    val x20 = 80.dp
    val x24 = 96.dp
}

object Layout {
    /** Standard horizontal screen gutter. */
    val screenGutter = 20.dp
    /** Mobile app content canvas cap (keeps wide screens readable). */
    val screenMaxWidth = 480.dp
    val strokeHairline = 1.dp
    val strokeThick = 1.5.dp
}
