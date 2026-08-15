package com.pamoja.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================================
// PAMOJA. SHAPE & RADII TOKENS
//
// Ported from design/pamoja-ui, the nine screen decks:
//   .sk   8px   skeletons
//   .ic  15px   icon chips
//   .fld 18px   inputs, and the squircle group avatars
//   .lr  20px   list rows
//   .card 22px  cards
//   .btn/.btn2/.av  999px  buttons and round avatars are always full pill
//
// Corners are never sharp.
// ============================================================================

/** Raw radius tokens, for direct use where an M3 Shapes role does not fit. */
object PamojaRadii {
    val xs = 8.dp    // skeletons
    val sm = 12.dp   // chips, small controls
    val md = 15.dp   // icon chips
    val lg = 18.dp   // inputs, squircle avatars
    val xl = 22.dp   // cards
    val xxl = 28.dp  // sheets, hero containers
    /** Buttons and avatars. Use [PillShape] rather than this dp value. */
    val pill = 999.dp
}

/** M3 shape scale mapped to Pamoja's rounded language. */
val PamojaShapes = Shapes(
    extraSmall = RoundedCornerShape(PamojaRadii.xs),  // skeletons, small chips
    small      = RoundedCornerShape(PamojaRadii.sm),
    medium     = RoundedCornerShape(PamojaRadii.lg),  // inputs
    large      = RoundedCornerShape(PamojaRadii.xl),  // cards (default)
    extraLarge = RoundedCornerShape(PamojaRadii.xxl), // sheets
)

/** Full-pill shape for buttons, avatars and CTAs. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Card shadows, approximating the design system's --sh and --sh2.
 *
 * Those are two-layer CSS shadows (a tight contact shadow plus a wide soft
 * one) and Compose's [androidx.compose.ui.draw.shadow] takes a single
 * elevation, so these are the nearest single-layer equivalents rather than a
 * literal port. Pair them with the theme's shadow colours, since a neutral
 * black shadow reads grey against the warm cream surface.
 */
object PamojaElevation {
    /** --sh. Resting cards, list rows. */
    val card = 6.dp
    /** --sh2. Dialogs, the floating bottom bar, anything lifted off the page. */
    val raised = 16.dp
}
