package com.pamoja.app.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pamoja.app.R

/**
 * First-run coach marks: the screen dims, one thing at a time is lit through
 * a cut-out, and a short tip beside it says what it is, with "1 of 3", Skip
 * and Next. Run once by the caller, which decides when and remembers that it
 * has.
 *
 * Targets mark themselves with [coachMarkTarget] anywhere under a
 * [CoachMarkHost]; a target that is not on screen yet is waited for, not
 * guessed at.
 */
class CoachMarkState {
    internal val targets = mutableStateMapOf<String, Rect>()
}

/** One step: the [key] a target registered with, and what to say about it. */
data class CoachMark(val key: String, val title: String, val body: String)

private val LocalCoachMarks = staticCompositionLocalOf<CoachMarkState?> { null }

@Composable
fun rememberCoachMarkState(): CoachMarkState = remember { CoachMarkState() }

/** Makes [state] available to [coachMarkTarget] below it. */
@Composable
fun CoachMarkHost(state: CoachMarkState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoachMarks provides state, content = content)
}

/** Records where this element is, in window coordinates, under [key]. */
fun Modifier.coachMarkTarget(key: String): Modifier = composed {
    val state = LocalCoachMarks.current ?: return@composed this
    onGloballyPositioned { state.targets[key] = it.boundsInWindow() }
}

/**
 * The overlay. Place it last in a Box that fills the screen, so it draws over
 * everything. Swallows every touch underneath while it runs; back skips it.
 */
@Composable
fun CoachMarkOverlay(state: CoachMarkState, marks: List<CoachMark>, onDone: () -> Unit) {
    if (marks.isEmpty()) return
    var step by rememberSaveable { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val mark = marks[step.coerceIn(marks.indices)]
    val target = state.targets[mark.key]?.translate(-origin) ?: return
    val last = step == marks.lastIndex
    val scrim = Kit.colors.scrim
    val padding = with(LocalDensity.current) { 8.dp.toPx() }
    val radius = with(LocalDensity.current) { 20.dp.toPx() }

    BackHandler(onBack = onDone)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            // Nothing underneath reacts while the tour runs.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(scrim)
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(target.left - padding, target.top - padding),
                size = Size(target.width + padding * 2, target.height + padding * 2),
                cornerRadius = CornerRadius(radius),
                blendMode = BlendMode.Clear,
            )
        }
        TipLayout(target = target, gap = padding * 2) {
            KitCard(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                spacing = 10.dp,
            ) {
                Text(mark.title, style = Kit.type.title)
                Text(mark.body, style = Kit.type.body, color = Kit.colors.muted)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.coach_step_of, step + 1, marks.size),
                        style = Kit.type.caption,
                        color = Kit.colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (!last) SecondaryButton(stringResource(R.string.coach_skip), onClick = onDone, size = ButtonSize.Small)
                    PrimaryButton(
                        stringResource(if (last) R.string.coach_done else R.string.coach_next),
                        onClick = { if (last) onDone() else step++ },
                        size = ButtonSize.Small,
                    )
                }
            }
        }
    }
}

/** Puts the tip under the target when it fits, otherwise above it, 16dp from the sides. */
@Composable
private fun TipLayout(target: Rect, gap: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val side = 16.dp.roundToPx()
        val width = (constraints.maxWidth - side * 2).coerceAtLeast(0)
        val tip = measurables.first().measure(Constraints(maxWidth = width))
        val below = target.bottom + gap
        val y = if (below + tip.height <= constraints.maxHeight - side) {
            below
        } else {
            (target.top - gap - tip.height).coerceAtLeast(side.toFloat())
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            tip.place((constraints.maxWidth - tip.width) / 2, y.toInt())
        }
    }
}
