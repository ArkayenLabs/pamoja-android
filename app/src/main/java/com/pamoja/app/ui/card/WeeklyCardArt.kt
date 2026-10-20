package com.pamoja.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.R
import com.pamoja.app.domain.model.WeeklyAward
import com.pamoja.app.ui.components.PamojaQrCode
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.CardArtColors
import com.pamoja.app.ui.kit.DarkCardArt
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.LightCardArt
import com.pamoja.app.ui.theme.PamojaIcons
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The card is drawn at 360x640dp at exactly 3px per dp: a 1080x1920 story-sized image. */
private val CardWidth = 360.dp
private val CardHeight = 640.dp
private const val CARD_PX_PER_DP = 3f
private const val LINEUP_ROWS = 6

/**
 * Shows the card scaled to the available width while recording it at full
 * size into [layer], which is what gets shared. The card itself never follows
 * the phone's font size: it is a picture, and a picture has one layout.
 */
@Composable
internal fun ScaledWeeklyCard(state: WeeklyCardUiState, layer: GraphicsLayer, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CardWidth.value / CardHeight.value),
        contentAlignment = Alignment.Center,
    ) {
        val scale = constraints.maxWidth / (CardWidth.value * CARD_PX_PER_DP)
        CompositionLocalProvider(LocalDensity provides Density(CARD_PX_PER_DP, fontScale = 1f)) {
            Box(
                modifier = Modifier
                    .requiredSize(CardWidth, CardHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    // Inside the scale, so the recording is the unscaled card.
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                WeeklyCardArt(state)
            }
        }
    }
}

@Composable
private fun WeeklyCardArt(state: WeeklyCardUiState) {
    val group = state.group ?: return
    val c = if (Kit.colors.isDark) DarkCardArt else LightCardArt
    val display = Kit.type.display
    val appName = stringResource(R.string.app_name)
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val range = state.weekStart?.let {
        stringResource(R.string.home_week_range, it.format(dayMonth), it.plusDays(6).format(dayMonth))
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.ground)
            .padding(27.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                appName.lowercase(Locale.getDefault()),
                style = TextStyle(fontFamily = Kit.type.bodyStrong.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                color = c.onGround,
                modifier = Modifier.weight(1f),
            )
            Text(range, style = cardText(11.sp, FontWeight.Bold), color = c.mutedOnGround)
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val total = "%,d".format(state.total)
            val goal = "%,d".format(group.weeklyTarget)
            if (state.reached) {
                // One line in every language: shrinks rather than wraps.
                BasicText(
                    buildAnnotatedString {
                        append(stringResource(R.string.weekly_card_done_lead))
                        append(' ')
                        withStyle(SpanStyle(color = c.emphasis)) { append(stringResource(R.string.weekly_card_done_emph)) }
                    },
                    style = display.copy(color = c.onGround, lineHeight = 50.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 50.sp),
                )
                Text(
                    stringResource(R.string.weekly_card_done_body, group.name, total, goal),
                    style = cardText(14.sp, FontWeight.SemiBold, lineHeight = 19.sp),
                    color = c.onGround,
                )
            } else {
                BasicText(
                    stringResource(R.string.weekly_card_so_far_headline, total),
                    style = display.copy(color = c.onGround, lineHeight = 44.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 44.sp),
                )
                Text(
                    if (state.finished) {
                        stringResource(
                            R.string.weekly_card_last_body,
                            group.name,
                            state.weekStart?.format(dayMonth).orEmpty(),
                            goal,
                        )
                    } else {
                        stringResource(R.string.weekly_card_so_far_body, group.name, goal)
                    },
                    style = cardText(14.sp, FontWeight.SemiBold, lineHeight = 19.sp),
                    color = c.onGround,
                )
            }
        }

        if (state.awards.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                state.awards.forEachIndexed { index, (member, award) ->
                    AwardFace(member, award, tilt = FaceTilts[index % FaceTilts.size], c = c, modifier = Modifier.weight(1f))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.streak >= 2) {
                Tile(
                    icon = PamojaIcons.Flame,
                    value = pluralStringResource(R.plurals.weekly_card_streak_weeks, state.streak, state.streak),
                    label = stringResource(R.string.weekly_card_in_a_row),
                    container = c.surface, content = c.onSurface, muted = c.muted,
                    modifier = Modifier.weight(1f),
                )
            }
            Tile(
                icon = PamojaIcons.Footprints,
                value = pluralStringResource(R.plurals.weekly_card_walks, state.walks, state.walks),
                label = stringResource(
                    if (state.finished) R.string.weekly_card_walks_label_finished else R.string.weekly_card_walks_label,
                ),
                container = c.accent, content = c.onAccent, muted = c.onAccent,
                modifier = Modifier.weight(1f),
            )
        }

        // Whatever height is left, so the footer below is never pushed off
        // the card: a long name, a wrapped award or a longer language costs
        // lineup rows, never the QR code.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rows = ((maxHeight - LineupChrome + LineupGap) / (LineupRow + LineupGap)).toInt()
            Lineup(state, c, maxRows = rows.coerceIn(0, LINEUP_ROWS))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Always white, in both palettes: scanners expect dark on light.
            PamojaQrCode(content = WeeklyCardShare.appLink(), size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.weekly_card_footer_title), style = cardText(12.sp, FontWeight.ExtraBold), color = c.onGround)
                Text(
                    stringResource(R.string.weekly_card_footer_body, appName),
                    style = cardText(10.sp, FontWeight.Medium),
                    color = c.mutedOnGround,
                )
            }
        }
    }
}

private val FaceTilts = listOf(-2f, 2f, -1f)

@Composable
private fun AwardFace(member: CardMember, award: WeeklyAward, tilt: Float, c: CardArtColors, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        Avatar(
            name = member.name,
            photoUrl = member.photoUrl,
            seed = member.userId,
            size = 69.dp,
            modifier = Modifier
                .rotate(tilt)
                .border(3.dp, c.photoRing, CircleShape),
        )
        Text(
            member.name,
            style = Kit.type.display.copy(fontSize = 16.sp, lineHeight = 17.sp),
            color = c.onGround,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                when (award) {
                    WeeklyAward.MOST_STEPS -> R.string.weekly_card_award_most
                    WeeklyAward.EVERY_DAY -> R.string.weekly_card_award_every_day
                    WeeklyAward.COMEBACK -> R.string.weekly_card_award_comeback
                },
            ),
            style = cardText(9.sp, FontWeight.Bold, lineHeight = 11.sp),
            color = c.onTag,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(c.tag)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Tile(
    icon: Int,
    value: String,
    label: String,
    container: Color,
    content: Color,
    muted: Color,
    modifier: Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(container)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value, style = Kit.type.display.copy(fontSize = 17.sp, lineHeight = 18.sp), color = content, maxLines = 1)
            Text(label, style = cardText(9.sp, FontWeight.SemiBold), color = muted, maxLines = 1)
        }
    }
}

private val LineupRow = 23.dp
private val LineupGap = 5.dp

/** The list's padding plus room for its "and N more" line. */
private val LineupChrome = 22.dp + 16.dp

@Composable
private fun Lineup(state: WeeklyCardUiState, c: CardArtColors, maxRows: Int) {
    val shown = state.members.take(maxRows)
    if (shown.isEmpty()) return
    val top = shown.maxOf { it.weeklySteps }.coerceAtLeast(1L)
    Column(
        verticalArrangement = Arrangement.spacedBy(LineupGap),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .padding(11.dp),
    ) {
        shown.forEach { member ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(LineupRow),
            ) {
                Avatar(name = member.name, photoUrl = member.photoUrl, seed = member.userId, size = 21.dp)
                Text(
                    member.name,
                    style = cardText(10.sp, FontWeight.Bold),
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(52.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(member.weeklySteps.toFloat() / top)
                            .clip(RoundedCornerShape(50))
                            .background(c.bar),
                    )
                }
                Text(
                    "%,d".format(member.weeklySteps),
                    style = cardText(10.sp, FontWeight.ExtraBold),
                    color = c.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
        val more = state.members.size - shown.size
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.weekly_card_more, more, more),
                style = cardText(9.sp, FontWeight.SemiBold),
                color = c.muted,
            )
        }
    }
}

@Composable
private fun cardText(size: TextUnit, weight: FontWeight, lineHeight: TextUnit = TextUnit.Unspecified) =
    Kit.type.body.copy(fontSize = size, fontWeight = weight, lineHeight = lineHeight)
