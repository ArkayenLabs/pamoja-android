package com.pamoja.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors

/**
 * The Pamoja mark: two overlapping rings, ember and jade.
 *
 * Drawn rather than shipped as a drawable so both rings follow the theme, and
 * because it is two circles.
 *
 * Lives here rather than beside the auth screens because the welcome screen
 * shows it too, and a brand mark used by more than one feature is exactly what
 * `ui/components/` is for.
 */
@Composable
fun PamojaRingMark(modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val colors = LocalPamojaColors.current
    val ember = colors.accentPrimary
    val jade = colors.accentTeal
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.minDimension
        val radius = w * (16.5f / 64f)
        val stroke = w * (6.5f / 64f)
        val cy = w * (32f / 64f)
        drawCircle(
            color = ember,
            radius = radius,
            center = Offset(w * (24.5f / 64f), cy),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = jade,
            radius = radius,
            center = Offset(w * (39.5f / 64f), cy),
            style = Stroke(width = stroke),
        )
    }
}
