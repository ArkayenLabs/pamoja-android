package com.pamoja.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * What a card is made of. There is deliberately no outlined card: a screen of
 * uniform bordered boxes is the generated look this rebuild is leaving behind.
 */
enum class CardTone {
    /** White (dark: deep violet) on the page. Most cards. */
    Surface,

    /** Lilac. At most one per screen: it marks the one number that matters. */
    Hero,

    /** Lime. Moments from people, e.g. a cheer received. */
    Pop,
}

@Composable
fun KitCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Surface,
    padding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Kit.colors
    val (background, onBackground) = when (tone) {
        CardTone.Surface -> colors.surface to colors.ink
        CardTone.Hero -> colors.hero to colors.onHero
        CardTone.Pop -> colors.pop to colors.onPop
    }
    CompositionLocalProvider(LocalContentColor provides onBackground, LocalCardTone provides tone) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(if (tone == CardTone.Hero) HeroShape else CardShape)
                .background(background)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A card's heading row: a title, and an optional quiet detail on the right. */
@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = Kit.type.title, modifier = Modifier.weight(1f))
        detail?.let {
            Text(it, style = Kit.type.caption, color = mutedContentColor())
        }
    }
}

/** The lime chip, e.g. "6 weeks in a row". The only bright colour in the kit. */
@Composable
fun PopChip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(PillShape)
            .background(Kit.colors.pop)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = Kit.colors.onPop,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(text, style = Kit.type.label.copy(fontSize = Kit.type.caption.fontSize), color = Kit.colors.onPop)
    }
}

/**
 * Progress towards a goal. On a hero card pass [onHero]; the track colours
 * differ so the bar stays visible on lilac.
 *
 * [description] is what TalkBack reads, e.g. "48,320 of 70,000 steps".
 */
@Composable
fun GoalBar(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    onHero: Boolean = false,
    thickness: Dp = if (onHero) 12.dp else 6.dp,
) {
    val colors = Kit.colors
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .clip(PillShape)
            .background(if (onHero) colors.heroTrack else colors.track)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .clip(PillShape)
                .background(if (onHero) colors.heroFill else colors.bar),
        )
    }
}

/**
 * One dot per day of the week, as drawn on the canvas: dark if the person
 * walked, a pale disc for a day gone by without a walk, and a hollow ring for
 * the days still ahead ([elapsed] onwards). Dots alone say nothing to
 * TalkBack, so [description] is required, e.g. "Walked 3 of 4 days so far".
 */
@Composable
fun DayDots(
    days: List<Boolean>,
    description: String,
    modifier: Modifier = Modifier,
    elapsed: Int = days.size,
) {
    val colors = Kit.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        days.forEachIndexed { index, walked ->
            val fill = when {
                index >= elapsed -> Color.Transparent
                walked -> colors.dotOn
                else -> colors.dotOff
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.5.dp, if (walked && index < elapsed) colors.dotOn else colors.dotOff, CircleShape),
            )
        }
    }
}

/**
 * Where the user is in a short flow, as segments rather than a counter.
 * [description] is read aloud, e.g. "Step 1 of 4".
 */
@Composable
fun StepSegments(current: Int, total: Int, description: String, modifier: Modifier = Modifier) {
    val colors = Kit.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(PillShape)
                    .background(if (index < current) colors.button else colors.track),
            )
        }
    }
}

/** A top bar row: [start] (usually Back) left, [end] actions right. */
@Composable
fun TopRow(
    modifier: Modifier = Modifier,
    start: @Composable RowScope.() -> Unit = {},
    end: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        start()
        Box(Modifier.weight(1f))
        end()
    }
}

/** The tone of the card a component sits in, so its secondary text can match. */
internal val LocalCardTone = staticCompositionLocalOf { CardTone.Surface }

/**
 * Secondary text for the card it sits in: muted on a surface, the hero's own
 * muted on lilac, and plain ink on lime, where a grey would lose contrast.
 */
@Composable
fun mutedContentColor(): Color {
    val colors = Kit.colors
    return when (LocalCardTone.current) {
        CardTone.Surface -> colors.muted
        CardTone.Hero -> colors.heroMuted
        CardTone.Pop -> colors.onPop
    }
}

/**
 * A message about the screen's state, e.g. offline or a failed save. Plain,
 * not alarming: the icon and title carry it, and [danger] is for real failures.
 * [actions] go under the text, e.g. "Connect" or "Open".
 */
@Composable
fun KitNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Kit.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(com.pamoja.app.ui.theme.PamojaIcons.AlertCircle),
            contentDescription = null,
            tint = if (danger) colors.danger else colors.ink,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Kit.type.bodyStrong, color = if (danger) colors.danger else colors.ink)
            Text(body, style = Kit.type.caption, color = colors.muted)
            actions?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                    content = it,
                )
            }
        }
    }
}

/**
 * A group's picture: its photo, or two linked rings on lilac when it has
 * none. Rounded square, so a group never reads as a person. [highlighted]
 * is the current group in a list; the others sit on a quieter tint.
 */
@Composable
fun GroupTile(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    highlighted: Boolean = true,
) {
    val colors = Kit.colors
    val (background, mark) = if (highlighted) colors.hero to colors.onHero else colors.iconBackground to colors.ink
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background),
    ) {
        Canvas(Modifier.size(size * 0.54f)) {
            // Two rings of radius 5 at x = 9 and 15 on a 24 grid, as drawn.
            val unit = this.size.minDimension / 24f
            val stroke = Stroke(width = 2f * unit)
            drawCircle(mark, radius = 5f * unit, center = Offset(9f * unit, 12f * unit), style = stroke)
            drawCircle(mark, radius = 5f * unit, center = Offset(15f * unit, 12f * unit), style = stroke)
        }
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * A placeholder shaped like the content on its way, so nothing jumps when it
 * lands. A slow sweep rather than a pulse, which at card size reads as
 * blinking.
 */
@Composable
fun KitSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = CardShape,
) {
    val colors = Kit.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200), RepeatMode.Restart),
        label = "sweep",
    )
    val start = -400f + progress * 1_400f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(colors.surface, colors.line, colors.surface),
                    startX = start,
                    endX = start + 400f,
                ),
            ),
    )
}
