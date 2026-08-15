package com.pamoja.app.ui.theme

import androidx.compose.ui.unit.dp

// ============================================================================
// PAMOJA. SPACING & LAYOUT TOKENS  (from design/pamoja-ui, the screen decks)
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
    /** Standard horizontal screen gutter. `.sc { padding: 0 20px }`. */
    val screenGutter = 20.dp
    /** Mobile app content canvas cap (keeps wide screens readable). */
    val screenMaxWidth = 480.dp
    val strokeHairline = 1.dp
    val strokeThick = 1.5.dp
    /** `.btn` / `.btn2`. */
    val buttonHeight = 56.dp
    /** `.fld`. Taller than the button on purpose, that is the design. */
    val fieldHeight = 58.dp
    /** `.av` on a group card, the 54px squircle. */
    val groupAvatar = 54.dp
}
