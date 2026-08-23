package com.pamoja.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Pamoja mark: two tapered soles mid-stride, ember in front and jade behind.
 *
 * Replaces the two overlapping rings this file used to draw. The rings said
 * nothing about walking; the soles carry the original brand's idea, steps taken
 * together, without the tread detail that made the original unusable at icon
 * sizes.
 *
 * Drawn rather than shipped as a drawable so both soles follow the theme. The
 * launcher icon and the splash cannot do that, since an adaptive icon layer is
 * a static asset, so those ship a light-tuned PNG generated from this exact
 * geometry. **If the shape below changes, regenerate those too**, or the icon
 * and the in-app mark drift apart.
 *
 * ### The geometry
 *
 * Each sole is the union of [SOLE_STEPS] circles whose radius shrinks from
 * [HEEL_RADIUS] to [TOE_RADIUS] along a line of length [SOLE_LENGTH], rotated
 * by [SOLE_ANGLE]. A union of circles rather than a hand-drawn outline so the
 * silhouette is exactly reproducible in Compose, in the PNG generator, and in
 * the monochrome icon layer, rather than three drawings that merely resemble
 * each other.
 *
 * Everything is expressed in a 100-unit design space and fitted to whatever
 * [size] the caller asks for, so the mark is resolution independent.
 */
@Composable
fun PamojaMark(modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val colors = LocalPamojaColors.current
    val ember = colors.accentPrimary
    val jade = colors.accentTeal

    // The surface the mark sits on, used for the tread marks so they read as
    // the background showing through rather than as painted-on white. Theme
    // aware for the same reason the soles are.
    val surface = colors.surfaceApp

    // Lightened by lerping toward white rather than by reaching for a second
    // palette token, because the ramp's "hover" shades are not reliably
    // lighter than their base in both themes, and this only has to be a
    // highlight.
    val emberLight = remember(ember) { lerp(ember, Color.White, HIGHLIGHT_MIX) }
    val jadeLight = remember(jade) { lerp(jade, Color.White, HIGHLIGHT_MIX) }

    val back = remember { soleCircles(BACK_OFFSET_X, BACK_OFFSET_Y) }
    val front = remember { soleCircles(FRONT_OFFSET_X, FRONT_OFFSET_Y) }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Fit the design's own bounding box, not the 100-unit space, so the
        // mark fills the requested size instead of floating inside slack the
        // design never used.
        val scale = min(w / DESIGN_WIDTH, h / DESIGN_HEIGHT) * FIT_MARGIN
        fun px(dx: Float, dy: Float) = Offset(
            w / 2f + (dx - DESIGN_CENTER_X) * scale,
            h / 2f + (dy - DESIGN_CENTER_Y) * scale,
        )

        // One brush per sole, in absolute canvas coordinates, so the gradient
        // runs continuously across the whole silhouette rather than restarting
        // inside every circle of the union.
        fun soleBrush(light: Color, dark: Color) = Brush.linearGradient(
            colors = listOf(light, dark),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )

        val jadeBrush = soleBrush(jadeLight, jade)
        val emberBrush = soleBrush(emberLight, ember)

        for ((dx, dy, r) in back) {
            drawCircle(brush = jadeBrush, radius = r * scale, center = px(dx, dy))
        }
        for ((dx, dy, r) in front) {
            drawCircle(brush = emberBrush, radius = r * scale, center = px(dx, dy))
        }

        // Tread marks, two per sole, placed on each sole's own exposed end.
        // Deliberately away from the overlap: across it they read as a grid
        // rather than as tread, which is what the first attempt looked like.
        val angle = Math.toRadians(SOLE_ANGLE.toDouble())
        val alongX = cos(angle).toFloat()
        val alongY = sin(angle).toFloat()
        val acrossX = -alongY
        val acrossY = alongX

        fun treadMarks(offsetX: Float, offsetY: Float, positions: List<Float>) {
            for (t in positions) {
                val (localX, radius) = soleAt(t)
                val cx = DESIGN_ORIGIN + localX * alongX + offsetX
                val cy = DESIGN_ORIGIN + localX * alongY + offsetY
                val half = radius * TREAD_LENGTH
                drawLine(
                    color = surface,
                    start = px(cx - acrossX * half, cy - acrossY * half),
                    end = px(cx + acrossX * half, cy + acrossY * half),
                    strokeWidth = TREAD_WIDTH * scale,
                    cap = StrokeCap.Round,
                )
            }
        }

        treadMarks(BACK_OFFSET_X, BACK_OFFSET_Y, BACK_TREAD_POSITIONS)
        treadMarks(FRONT_OFFSET_X, FRONT_OFFSET_Y, FRONT_TREAD_POSITIONS)
    }
}

/** One circle of a sole: its centre in design space, and its radius. */
private data class SoleCircle(val x: Float, val y: Float, val r: Float)

/** The local x and radius at [t] along the sole, where t runs 0 (heel) to 1 (toe). */
private fun soleAt(t: Float): Pair<Float, Float> {
    val x0 = -SOLE_LENGTH / 2f + HEEL_RADIUS
    val x1 = SOLE_LENGTH / 2f - TOE_RADIUS
    return (x0 + (x1 - x0) * t) to (HEEL_RADIUS + (TOE_RADIUS - HEEL_RADIUS) * t)
}

/** One sole's circles, rotated by [SOLE_ANGLE] and shifted by the given offset. */
private fun soleCircles(offsetX: Float, offsetY: Float): List<SoleCircle> {
    val angle = Math.toRadians(SOLE_ANGLE.toDouble())
    val cosA = cos(angle).toFloat()
    val sinA = sin(angle).toFloat()
    return (0..SOLE_STEPS).map { i ->
        val (localX, radius) = soleAt(i / SOLE_STEPS.toFloat())
        SoleCircle(
            x = DESIGN_ORIGIN + localX * cosA + offsetX,
            y = DESIGN_ORIGIN + localX * sinA + offsetY,
            r = radius,
        )
    }
}

// ── The design, in a 100-unit space ─────────────────────────────────────────

private const val DESIGN_ORIGIN = 50f
private const val SOLE_LENGTH = 60f
private const val HEEL_RADIUS = 15.5f
private const val TOE_RADIUS = 8.5f
private const val SOLE_ANGLE = -30f
private const val SOLE_STEPS = 40

private const val BACK_OFFSET_X = 6f
private const val BACK_OFFSET_Y = -7f
private const val FRONT_OFFSET_X = -6f
private const val FRONT_OFFSET_Y = 9f

/**
 * The bounding box the two soles actually occupy, measured from the geometry
 * above rather than assumed: x spans 15.94 to 83.12 and y spans 23.75 to 81.75.
 */
private const val DESIGN_CENTER_X = 49.53f
private const val DESIGN_CENTER_Y = 52.75f
private const val DESIGN_WIDTH = 67.18f
private const val DESIGN_HEIGHT = 58.00f

/** A hair of breathing room so the silhouette never touches the bounds. */
private const val FIT_MARGIN = 0.98f

private const val HIGHLIGHT_MIX = 0.22f
private const val TREAD_LENGTH = 0.68f
private const val TREAD_WIDTH = 1.9f

private val BACK_TREAD_POSITIONS = listOf(0.62f, 0.78f)
private val FRONT_TREAD_POSITIONS = listOf(0.22f, 0.38f)
