package com.pamoja.app.ui.kit

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * One dot per day of the week: filled if the person walked, hollow if not.
 * Dots alone say nothing to TalkBack, so [description] is required, e.g.
 * "Walked 5 of 7 days".
 */
@Composable
fun DayDots(days: List<Boolean>, description: String, modifier: Modifier = Modifier) {
    val colors = Kit.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        days.forEach { walked ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (walked) colors.dotOn else Color.Transparent)
                    .border(1.5.dp, if (walked) colors.dotOn else colors.dotOff, CircleShape),
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
