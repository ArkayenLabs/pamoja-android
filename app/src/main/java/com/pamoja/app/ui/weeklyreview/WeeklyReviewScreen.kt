package com.pamoja.app.ui.weeklyreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.WalkedDays
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.DayDots
import com.pamoja.app.ui.kit.GoalBar
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.HeroShape
import com.pamoja.app.ui.kit.PersonRow
import com.pamoja.app.ui.kit.PopChip
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.kit.mutedContentColor
import com.pamoja.app.ui.theme.PamojaIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The week in review, on the Lilac kit: the last finished week for everyone,
 * every earlier one with Premium (REBUILD_PLAN.md Phase 6). Planning, which
 * used to live here, is gone from the app.
 */
@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
    viewModel: WeeklyReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyReviewContent(
        state = state,
        onBack = onBack,
        onOpenPremium = onOpenPremium,
        onSelectWeek = viewModel::selectWeek,
        onRetry = viewModel::retry,
    )
}

/** Stateless rendering boundary, used by the debug preview and by tests. */
@Composable
internal fun WeeklyReviewContent(
    state: WeeklyReviewUiState,
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onRetry: () -> Unit,
) = KitInAppTheme {
    val context = LocalContext.current
    KitScreen(
        gutter = 20.dp,
        spacing = 16.dp,
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
    ) {
        Text(stringResource(R.string.weekly_review_title), style = Kit.type.headline)
        when {
            state.isGroupUnavailable -> KitNotice(
                title = stringResource(R.string.group_unavailable_title),
                body = stringResource(R.string.group_unavailable_body),
            )

            state.group == null && state.loadError != null -> {
                val copy = state.loadError.toErrorCopy()
                KitNotice(
                    title = stringResource(copy.title),
                    body = copy.body(context),
                    danger = true,
                    actions = copy.retryLabel?.let { label ->
                        { PrimaryButton(stringResource(label), onClick = onRetry) }
                    },
                )
            }

            state.group == null || !state.lastWeekLoaded -> {
                KitSkeleton(height = 220.dp, shape = HeroShape)
                KitSkeleton(height = 260.dp)
            }

            else -> {
                val week = state.selectedWeek
                if (week == null) {
                    KitNotice(
                        title = stringResource(R.string.review_first_week_title),
                        body = stringResource(R.string.review_first_week_body),
                    )
                } else {
                    WeekCard(week, isLast = week.weekStart == state.lastWeek?.weekStart)
                    Members(week, state.members)
                }
                History(state, onSelectWeek, onOpenPremium)
            }
        }
    }
}

private val dayMonth: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Composable
private fun weekRange(week: GroupWeekSummary): String {
    val start = runCatching { LocalDate.parse(week.weekStart) }.getOrNull() ?: return week.weekStart
    return stringResource(R.string.home_week_range, start.format(dayMonth), start.plusDays(6).format(dayMonth))
}

/** The one number that matters for that week, and how it ended. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCard(week: GroupWeekSummary, isLast: Boolean) {
    val total = "%,d".format(week.totalSteps)
    val target = "%,d".format(week.targetSteps)
    KitCard(tone = CardTone.Hero, padding = 20.dp, spacing = 12.dp) {
        CardHeader(
            title = if (isLast) {
                stringResource(R.string.review_last_week)
            } else {
                stringResource(R.string.review_week_of, runCatching { LocalDate.parse(week.weekStart).format(dayMonth) }.getOrDefault(week.weekStart))
            },
            detail = weekRange(week),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(total, style = Kit.type.bigNumber.copy(fontSize = 52.sp, lineHeight = 52.sp), modifier = Modifier.alignByBaseline())
            Text(
                stringResource(R.string.group_of_target, target),
                style = Kit.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = mutedContentColor(),
                modifier = Modifier.alignByBaseline(),
            )
        }
        GoalBar(
            progress = week.completionFraction,
            description = stringResource(R.string.home_week_progress_desc, total, target),
            onHero = true,
        )
        when {
            week.goalHit -> PopChip(stringResource(R.string.review_goal_met), icon = PamojaIcons.Check)
            week.shielded -> PopChip(stringResource(R.string.review_shield_used), icon = PamojaIcons.ShieldCheck)
            else -> Text(
                stringResource(R.string.review_short, "%,d".format((week.targetSteps - week.totalSteps).coerceAtLeast(0L))),
                style = Kit.type.label,
                color = mutedContentColor(),
            )
        }
    }
}

/**
 * Who walked what, with their days. Only people still in the group are
 * named; a former member's contribution stays in the total above, and a user
 * ID is never shown in place of a name.
 */
@Composable
private fun Members(week: GroupWeekSummary, members: Map<String, WeeklyReviewMember>) {
    val named = week.contributions
        .mapNotNull { contribution -> members[contribution.userId]?.let { it to contribution } }
        .sortedByDescending { (_, contribution) -> contribution.stepCount }
    if (named.isEmpty()) return
    KitCard(spacing = 4.dp) {
        named.forEach { (member, contribution) ->
            val mask = contribution.walkedDays
            PersonRow(
                name = member.displayName,
                photoUrl = member.photoUrl,
                seed = contribution.userId,
                below = if (mask == null) null else {
                    {
                        DayDots(
                            days = List(7) { WalkedDays.walked(mask, it) },
                            description = pluralStringResource(
                                R.plurals.home_days_walked_desc, 7, WalkedDays.count(mask), 7,
                            ),
                        )
                    }
                },
            ) {
                Text("%,d".format(contribution.stepCount), style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

/** Earlier weeks with Premium; the locked promise of them without. */
@Composable
private fun History(state: WeeklyReviewUiState, onSelectWeek: (String) -> Unit, onOpenPremium: () -> Unit) {
    val access = state.access
    when {
        access is GroupAccessState.Loading -> Unit
        access.hasCircleAccess -> {
            val weeks = state.earlierWeeks
            if (weeks.isEmpty() && access !is GroupAccessState.Preview) return
            KitCard(spacing = 2.dp) {
                CardHeader(stringResource(R.string.review_earlier), Modifier.padding(bottom = 6.dp))
                if (access is GroupAccessState.Preview) {
                    val until = Instant.ofEpochMilli(access.preview.validUntilMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        stringResource(R.string.premium_preview_until, until.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                        style = Kit.type.caption,
                        color = Kit.colors.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                // The last week is always a row too, so a selected earlier week
                // can be put back.
                (listOfNotNull(state.lastWeek) + weeks).forEach { week ->
                    WeekRow(week, selected = week.weekStart == state.selectedWeek?.weekStart, onClick = { onSelectWeek(week.weekStart) })
                }
            }
        }
        else -> KitCard(spacing = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(PamojaIcons.Lock), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.review_locked_title), style = Kit.type.title)
            }
            Text(stringResource(R.string.review_locked_body), style = Kit.type.body, color = Kit.colors.muted)
            SecondaryButton(stringResource(R.string.premium_see), onClick = onOpenPremium)
        }
    }
}

@Composable
private fun WeekRow(week: GroupWeekSummary, selected: Boolean, onClick: () -> Unit) {
    val range = weekRange(week)
    val steps = "%,d".format(week.totalSteps)
    val description = stringResource(
        when {
            week.goalHit -> R.string.review_row_met_desc
            week.shielded -> R.string.review_row_shielded_desc
            else -> R.string.review_row_missed_desc
        },
        range,
        steps,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(
                when {
                    week.goalHit -> PamojaIcons.Check
                    week.shielded -> PamojaIcons.ShieldCheck
                    else -> PamojaIcons.Minus
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                range,
                style = Kit.type.bodyStrong.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold),
            )
        }
        Text(steps, style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.ExtraBold))
    }
}
