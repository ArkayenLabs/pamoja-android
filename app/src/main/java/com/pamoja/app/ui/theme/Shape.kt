package com.pamoja.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================================
// PAMOJA. SHAPE & RADII TOKENS  (from tokens/spacing.css)
// Radii run generous and pill-leaning; corners are never sharp.
// ============================================================================

/** Raw radius tokens, for direct use where an M3 Shapes role does not fit. */
object PamojaRadii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 28.dp
    // Full pill for buttons / CTAs, use RoundedCornerShape(percent = 50) or a large dp.
    val pill = 999.dp
}

/** M3 shape scale mapped to Pamoja's rounded, pill-leaning language. */
val PamojaShapes = Shapes(
    extraSmall = RoundedCornerShape(PamojaRadii.xs),   // chips, small controls
    small      = RoundedCornerShape(PamojaRadii.sm),   // inputs (secondary)
    medium     = RoundedCornerShape(PamojaRadii.md),   // inputs, small cards
    large      = RoundedCornerShape(PamojaRadii.lg),   // cards (default)
    extraLarge = RoundedCornerShape(PamojaRadii.xxl),  // sheets, hero containers
)

/** Full-pill shape for buttons and CTAs (always pill in this brand). */
val PillShape = RoundedCornerShape(percent = 50)
