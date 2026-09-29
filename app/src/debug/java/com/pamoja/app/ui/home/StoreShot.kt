package com.pamoja.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.kit.Kit
import kotlin.math.roundToInt

/**
 * A Play Store screenshot around a preview screen: the headline on the hero
 * lilac, and the real screen laid out at phone size, scaled into a frame that
 * runs off the bottom edge. `--es caption "..."` on GroupPreviewActivity turns
 * it on; capture at 1080x1920 (`wm size 1080x1920`) for Play's 9:16.
 */
@Composable
fun StoreShot(caption: String?, content: @Composable () -> Unit) {
    if (caption == null) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().background(Kit.colors.hero).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            caption,
            style = Kit.type.display,
            color = Kit.colors.onHero,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(36.dp))
        val bezel = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
        Box(
            Modifier
                .clip(bezel)
                .background(Kit.colors.onHero)
                .padding(start = 7.dp, end = 7.dp, top = 7.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 33.dp, topEnd = 33.dp))
                    .scaledPhone(SCALE),
            ) { content() }
        }
    }
}

/** Lays [this] out at a phone's size and draws it at [scale], clipped to what fits. */
private fun Modifier.scaledPhone(scale: Float) = layout { measurable, constraints ->
    val phone = measurable.measure(Constraints.fixed(PHONE_WIDTH.roundToPx(), PHONE_HEIGHT.roundToPx()))
    val width = (phone.width * scale).roundToInt()
    val height = (phone.height * scale).roundToInt().coerceAtMost(constraints.maxHeight)
    layout(width, height) {
        phone.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

private val PHONE_WIDTH = 392.dp
private val PHONE_HEIGHT = 850.dp
private const val SCALE = 0.62f
