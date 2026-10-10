package com.pamoja.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pamoja.app.R
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.ui.group.GroupUiState
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.theme.PamojaIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Someone in the celebration. Names are first names, as on Home. */
internal data class CelebrationPerson(
    val userId: String,
    val name: String,
    val photoUrl: String?,
    val isYou: Boolean,
)

/** What the goal-reached moment shows, worked out once from the group's state. */
internal data class CelebrationModel(
    val steps: Long,
    val target: Long,
    /** You first, then the rest by name: an order that never reads as a ranking. */
    val people: List<CelebrationPerson>,
    /** Weeks in a row including this one, which the finalizer will count. */
    val streakWeeks: Int,
    /** Whole days of the week still to come after today. */
    val daysToSpare: Int,
    /** Walked on every day of the week before today; empty below three days. */
    val everyDay: List<CelebrationPerson>,
) {
    val everyoneEveryDay: Boolean get() = people.size > 1 && everyDay.size == people.size
}

/**
 * The moment for [state]'s group, or null while it cannot be told honestly
 * (no group yet). [today] is the group's own date.
 */
internal fun celebrationModel(
    state: GroupUiState,
    today: LocalDate? = state.group?.let { WeekWindow.todayFor(it) },
): CelebrationModel? {
    val group = state.group ?: return null
    today ?: return null
    val daysLeft = WeekWindow.daysLeftIn(group.startDay, today)
    val daysBeforeToday = 7 - daysLeft
    val people = state.memberStepData
        .map {
            CelebrationPerson(
                userId = it.user.userId,
                name = it.user.name.trim().substringBefore(' ').ifBlank { it.user.name },
                photoUrl = it.user.photoUrl,
                isYou = it.user.userId == state.currentUserId,
            )
        }
        .sortedWith(compareByDescending<CelebrationPerson> { it.isYou }.thenBy { it.name.lowercase() })
    // Only a run long enough to mean something, and only from known dots:
    // a member whose dots are unknown is left out, never counted as missing.
    val everyDay = if (daysBeforeToday < 3) {
        emptyList()
    } else {
        val full = (1 shl daysBeforeToday) - 1
        val walkedAll = state.memberStepData
            .filter { member -> member.walkedDays?.let { it and full == full } == true }
            .map { it.user.userId }
            .toSet()
        people.filter { it.userId in walkedAll }
    }
    return CelebrationModel(
        steps = state.combinedWeeklySteps,
        target = group.weeklyTarget.toLong(),
        people = people,
        streakWeeks = group.streakFor(WeekWindow.startOf(group.startDay, today)) + 1,
        daysToSpare = (daysLeft - 1).coerceAtLeast(0),
        everyDay = everyDay,
    )
}

/**
 * Once per group per week, when the goal is met. Pamoja means "together", so
 * the moment is the group's faces coming together as the ring closes: one
 * firm haptic, one lime pulse, about two seconds, a tap skips it. No confetti.
 * With animations off in system settings Compose snaps every step to its end.
 */
@Composable
internal fun GoalCelebration(
    model: CelebrationModel,
    highFivesSent: Boolean,
    onHighFiveEveryone: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GoalCelebrationContent(model, highFivesSent, onHighFiveEveryone, onShare, onClose)
    }
}

/** Stateless boundary, for the debug preview and tests. [animate] false starts at the end. */
@Composable
internal fun GoalCelebrationContent(
    model: CelebrationModel,
    highFivesSent: Boolean,
    onHighFiveEveryone: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    animate: Boolean = true,
) {
    val start = if (animate) START else 1f
    val ring = remember { Animatable(start) }
    val gather = remember { Animatable(if (animate) 0f else 1f) }
    val pulse = remember { Animatable(if (animate) 0f else 1f) }
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(250)
        coroutineScope {
            launch { ring.animateTo(1f, tween(1_000, easing = FastOutSlowInEasing)) }
            launch { gather.animateTo(1f, tween(900, delayMillis = 300, easing = FastOutSlowInEasing)) }
        }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        coroutineScope {
            launch { pulse.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
            launch { reveal.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        }
    }
    var skipped by remember { mutableStateOf(false) }
    LaunchedEffect(skipped) {
        if (!skipped) return@LaunchedEffect
        ring.snapTo(1f)
        gather.snapTo(1f)
        pulse.snapTo(1f)
        reveal.snapTo(1f)
    }

    val colors = Kit.colors
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The dialog draws edge to edge: keep clear of the system bars.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CircleIconButton(
                    icon = PamojaIcons.Close,
                    contentDescription = stringResource(R.string.celebration_close),
                    onClick = onClose,
                    modifier = Modifier.graphicsLayer { alpha = reveal.value },
                )
            }
            Stage(model.people, ring.value, gather.value, pulse.value)

            val shown = if (ring.value >= 1f) {
                model.steps
            } else {
                (model.steps * ring.value).roundToLong()
            }
            Text(
                "%,d".format(shown),
                style = Kit.type.bigNumber.copy(fontSize = 52.sp, lineHeight = 56.sp),
                color = colors.ink,
            )
            Text(
                stringResource(R.string.group_celebration_progress, "%,d".format(model.target)),
                style = Kit.type.label,
                color = colors.muted,
            )

            Column(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .graphicsLayer {
                        alpha = reveal.value
                        translationY = (1f - reveal.value) * 16.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.group_celebration_title),
                    style = Kit.type.headline,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (model.daysToSpare > 0) {
                        pluralStringResource(R.plurals.celebration_days_spare, model.daysToSpare, model.daysToSpare)
                    } else {
                        stringResource(R.string.celebration_last_day)
                    },
                    style = Kit.type.body,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
                Highlights(model, Modifier.padding(top = 8.dp))
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val canHighFive = model.people.any { !it.isYou }
                    if (canHighFive) {
                        PrimaryButton(
                            text = stringResource(
                                if (highFivesSent) R.string.celebration_high_fives_sent else R.string.celebration_high_five_all,
                            ),
                            onClick = onHighFiveEveryone,
                            enabled = !highFivesSent,
                            icon = if (highFivesSent) PamojaIcons.HighFiveSent else PamojaIcons.HighFive,
                        )
                        SecondaryButton(stringResource(R.string.celebration_share), onClick = onShare, icon = PamojaIcons.Share)
                    } else {
                        PrimaryButton(stringResource(R.string.celebration_share), onClick = onShare, icon = PamojaIcons.Share)
                    }
                }
            }
        }
        // Until the words arrive, a tap anywhere skips to them. Nothing below
        // is tappable before then, so an early tap cannot press a hidden button.
        if (reveal.value < 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {}
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        skipped = true
                    },
            )
        }
    }
}

/** The ring, the lime pulse as it closes, and the faces moving in to meet inside it. */
@Composable
private fun Stage(people: List<CelebrationPerson>, ring: Float, gather: Float, pulse: Float) {
    val colors = Kit.colors
    val shown = if (people.size > MAX_FACES) people.take(MAX_FACES - 1) else people
    val extra = people.size - shown.size
    val slots = shown.size + if (extra > 0) 1 else 0

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(272.dp),
        contentAlignment = Alignment.Center,
    ) {
        val spreadX = (maxWidth / 2 - FACE / 2).coerceAtMost(150.dp)
        if (pulse > 0f && pulse < 1f) {
            Box(
                Modifier
                    .size(RING)
                    .graphicsLayer {
                        scaleX = 1f + 0.3f * pulse
                        scaleY = 1f + 0.3f * pulse
                        alpha = (1f - pulse) * 0.8f
                    }
                    .background(colors.pop, CircleShape),
            )
        }
        Box(
            Modifier
                .size(RING)
                .graphicsLayer { alpha = gather }
                .background(colors.hero, CircleShape),
        )
        Canvas(Modifier.size(RING)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                colors.bar, -90f, 360f * ring, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = if (ring < 1f) StrokeCap.Round else StrokeCap.Butt),
            )
        }
        for (i in 0 until slots) {
            // Apart: spread round an oval outside the ring. Together: one
            // overlapping row in its middle.
            val angle = -PI / 2 + 2 * PI * i / slots + PI / 7
            val apartX = spreadX * cos(angle).toFloat()
            val apartY = 112.dp * sin(angle).toFloat()
            val togetherX = OVERLAP * (i - (slots - 1) / 2f)
            val x = lerp(apartX, togetherX, gather)
            val y = lerp(apartY, 0.dp, gather)
            Box(Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }) {
                if (i < shown.size) {
                    val person = shown[i]
                    Avatar(
                        name = person.name,
                        photoUrl = person.photoUrl,
                        seed = person.userId,
                        size = FACE,
                        modifier = Modifier.background(colors.page, CircleShape).padding(2.dp),
                    )
                } else {
                    Box(
                        Modifier.size(FACE).background(colors.page, CircleShape).padding(2.dp)
                            .background(colors.iconBackground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+$extra", style = Kit.type.label, color = colors.ink)
                    }
                }
            }
        }
    }
}

/** The streak, and the one kind of person worth naming here: who walked every day. */
@Composable
private fun Highlights(model: CelebrationModel, modifier: Modifier = Modifier) {
    val streak = when {
        model.streakWeeks >= 2 -> pluralStringResource(R.plurals.home_streak_weeks, model.streakWeeks, model.streakWeeks)
        else -> stringResource(R.string.celebration_first_week)
    }
    val you = stringResource(R.string.home_you)
    val names = model.everyDay.map { if (it.isYou) you else it.name }
    val everyDay = when {
        model.everyoneEveryDay -> stringResource(R.string.celebration_every_day_all)
        names.isEmpty() -> null
        names.size == 1 && model.everyDay.first().isYou -> stringResource(R.string.celebration_every_day_you)
        names.size == 1 -> stringResource(R.string.celebration_every_day_one, names[0])
        names.size == 2 -> stringResource(R.string.celebration_every_day_two, names[0], names[1])
        else -> pluralStringResource(R.plurals.celebration_every_day_many, names.size - 1, names[0], names.size - 1)
    }
    KitCard(tone = CardTone.Hero, spacing = 10.dp, modifier = modifier.fillMaxWidth()) {
        HighlightRow(PamojaIcons.Flame, streak)
        everyDay?.let { HighlightRow(PamojaIcons.Footprints, it) }
    }
}

@Composable
private fun HighlightRow(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = Kit.type.bodyStrong, color = Kit.colors.onHero)
    }
}

private fun lerp(from: Dp, to: Dp, fraction: Float): Dp = from + (to - from) * fraction

/** Where the ring starts: close, so the closing is what you watch. */
private const val START = 0.9f
private const val MAX_FACES = 5
private val FACE = 44.dp
private val OVERLAP = 34.dp
private val RING = 220.dp
