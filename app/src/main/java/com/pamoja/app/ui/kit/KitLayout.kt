package com.pamoja.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Width classes at Material's breakpoints (600dp, 840dp).
 *
 * Measured from this window, not the physical screen, so split screen and
 * freeform windows get the right answer.
 */
enum class WindowWidth { Compact, Medium, Expanded }

@Composable
fun currentWindowWidth(): WindowWidth {
    val width = windowSizeDp().width
    return when {
        width < 600.dp -> WindowWidth.Compact
        width < 840.dp -> WindowWidth.Medium
        else -> WindowWidth.Expanded
    }
}

@Composable
private fun windowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

/**
 * Widest a single column of content gets. The screens are designed for a
 * phone; on a tablet, a foldable or in landscape they sit centred at this
 * width rather than stretching lines and buttons across the glass.
 */
val MaxContentWidth = 560.dp

/** Below this height a pinned footer would crowd out the content. */
private val ShortWindowHeight = 480.dp

/**
 * The frame every kit screen sits in.
 *
 * - Edge to edge: draws the page colour under the bars, then pads the
 *   content by the safe-drawing insets (bars, cutout and keyboard), so
 *   nothing lands under a bar or behind the keyboard.
 * - Always scrollable: at 200% font size or on a 360x640 phone, no screen may
 *   cut off its content. The old app had three that could not scroll.
 * - [bottom] stays pinned below the scrolling part, for a screen's main
 *   action, so "Continue" is reachable however long the content gets. On a
 *   short window (a phone in landscape) it scrolls with the content instead:
 *   pinned, it left about 50dp of visible content in the gallery run.
 * - Centred at [MaxContentWidth] on wide windows.
 */
@Composable
fun KitScreen(
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    spacing: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pinBottom = windowSizeDp().height >= ShortWindowHeight
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Kit.colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
        ) {
            top?.let {
                Box(Modifier.padding(horizontal = gutter).padding(top = 12.dp)) { it() }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                content()
                if (!pinBottom && bottom != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { bottom() }
                }
            }
            if (pinBottom) bottom?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = it,
                )
            }
        }
    }
}
