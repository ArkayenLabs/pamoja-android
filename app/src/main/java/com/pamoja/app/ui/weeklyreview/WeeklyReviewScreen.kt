package com.pamoja.app.ui.weeklyreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pamoja.app.R
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanStatus
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.ui.group.WeeklyGoalPicker
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.GroupAccessBadge
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaEmptyState
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.GroupAvatarGradients
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onOpenPremium: () -> Unit,
    viewModel: WeeklyReviewViewModel = hiltViewModel(),
    planningOnly: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyReviewContent(
        uiState = uiState,
        onBack = onBack,
        onContinue = onContinue,
        onRetry = viewModel::retry,
        onOpenPremium = onOpenPremium,
        onSaveNextWeekPlan = viewModel::saveNextWeekPlan,
        onSaveNextWeekResponse = viewModel::saveNextWeekResponse,
        planningOnly = planningOnly,
    )
}

/** Stateless so loading, empty, failure and populated states can be verified directly. */
@Composable
internal fun WeeklyReviewContent(
    uiState: WeeklyReviewUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenPremium: () -> Unit = {},
    onSaveNextWeekPlan: (NextWeekPlanChoice, Int?) -> Unit = { _, _ -> },
    onSaveNextWeekResponse: (NextWeekResponse) -> Unit = {},
    planningOnly: Boolean = false,
) {
    val colors = LocalPamojaColors.current
    val group = uiState.group

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Layout.screenMaxWidth)
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            WeeklyReviewTopBar(
                groupName = group?.name.orEmpty(),
                groupAccess = uiState.groupAccess,
                onBack = onBack,
                planningOnly = planningOnly,
            )

            OfflineBanner(isOffline = uiState.isOffline)

            when {
                planningOnly && uiState.dayOnePlanningEnabled &&
                    group != null && !uiState.isGroupUnavailable -> {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.x6),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
                    ) {
                        item {
                            NextWeekTogetherCard(uiState, onOpenPremium,
                                onSaveNextWeekPlan, onSaveNextWeekResponse, standalone = true)
                        }
                        if (uiState.nextWeekPlanError != null ||
                            uiState.groupAccess is GroupAccessState.Unavailable
                        ) item { TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.common_try_again))
                        } }
                    }
                }
                uiState.isLoading && !uiState.historyLoaded -> WeeklyReviewSkeleton()

                uiState.isGroupUnavailable -> PamojaEmptyState(
                    icon = PamojaIcons.Users,
                    title = stringResource(R.string.weekly_review_unavailable_title),
                    body = stringResource(R.string.weekly_review_unavailable_body),
                    actionLabel = stringResource(R.string.common_back_to_groups),
                    onAction = onBack,
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                uiState.accessGated &&
                    uiState.groupAccess is GroupAccessState.Unavailable -> PamojaErrorState(
                    error = uiState.groupAccess.error,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = Spacing.x8),
                )

                uiState.accessGated &&
                    uiState.groupAccess == GroupAccessState.Free -> PamojaEmptyState(
                    icon = PamojaIcons.Clock,
                    title = stringResource(R.string.weekly_review_preview_pending_title),
                    body = stringResource(R.string.weekly_review_preview_pending_body),
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                uiState.accessGated &&
                    uiState.groupAccess is GroupAccessState.PreviewExpired -> PamojaEmptyState(
                    icon = PamojaIcons.Clock,
                    title = stringResource(R.string.weekly_review_preview_ended_title),
                    body = stringResource(R.string.weekly_review_preview_ended_body),
                    actionLabel = stringResource(R.string.weekly_review_continue_action),
                    onAction = onContinue,
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                uiState.loadError != null && uiState.weeks.isEmpty() -> PamojaErrorState(
                    error = uiState.loadError,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = Spacing.x8),
                )

                uiState.isEmpty && !uiState.dayOnePlanningEnabled -> PamojaEmptyState(
                    icon = PamojaIcons.Clock,
                    title = stringResource(R.string.weekly_review_empty_title),
                    body = stringResource(R.string.weekly_review_empty_body),
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                else -> WeeklyReviewHistory(
                    uiState = uiState,
                    onRetry = onRetry,
                    onOpenPremium = onOpenPremium,
                    onSaveNextWeekPlan = onSaveNextWeekPlan,
                    onSaveNextWeekResponse = onSaveNextWeekResponse,
                )
            }
        }
    }
}

@Composable
private fun WeeklyReviewTopBar(
    groupName: String,
    groupAccess: GroupAccessState,
    onBack: () -> Unit,
    planningOnly: Boolean = false,
) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.x3),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.ArrowLeft),
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(if (planningOnly) R.string.plan_next_week_title
                    else R.string.weekly_review_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        if (groupName.isNotBlank()) {
            Row(
                modifier = Modifier.padding(start = 56.dp, end = Spacing.x6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                Text(
                    // The stored group name is shown exactly as entered. No
                    // descriptive suffix is generated by this screen.
                    text = groupName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                GroupAccessBadge(
                    access = groupAccess,
                    showContainer = false,
                )
            }
        }
    }
}

@Composable
private fun WeeklyReviewHistory(
    uiState: WeeklyReviewUiState,
    onRetry: () -> Unit,
    onOpenPremium: () -> Unit,
    onSaveNextWeekPlan: (NextWeekPlanChoice, Int?) -> Unit,
    onSaveNextWeekResponse: (NextWeekResponse) -> Unit,
) {
    val groupCreatedAt = uiState.group?.createdAt ?: 0L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.x6,
            end = Spacing.x6,
            top = Spacing.x3,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        val preview = (uiState.groupAccess as? GroupAccessState.Preview)?.preview
        if (uiState.accessGated && preview != null) {
            item(key = "circle_preview") {
                CompactPreviewNotice(validUntilMillis = preview.validUntilMillis)
            }
        }

        if (uiState.loadError != null) {
            item(key = "history_error") {
                val context = LocalContext.current
                val copy = uiState.loadError.toErrorCopy()
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = stringResource(copy.title),
                    body = copy.body(context),
                    tone = NoticeTone.Warning,
                    actionLabel = stringResource(R.string.common_try_again),
                    onAction = onRetry,
                )
            }
        }

        val latestWeek = uiState.weeks.firstOrNull()
        if (uiState.isEmpty) {
            item(key = "empty_history") {
                PamojaEmptyState(
                    icon = PamojaIcons.Clock,
                    title = stringResource(R.string.weekly_review_empty_title),
                    body = stringResource(R.string.weekly_review_empty_body),
                )
            }
        }
        if (uiState.dayOnePlanningEnabled) {
            item(key = "day_one_planning") {
                NextWeekTogetherCard(uiState, onOpenPremium,
                    onSaveNextWeekPlan, onSaveNextWeekResponse)
            }
        }
        if (latestWeek != null) {
            item(key = "latest_week") {
                LatestWeekCard(
                    latestWeek = latestWeek,
                    previousWeek = uiState.weeks.getOrNull(1),
                    currentMembers = uiState.currentMembers,
                )
            }
            if (uiState.accessGated && !uiState.dayOnePlanningEnabled) {
                item(key = "next_week_together") {
                    NextWeekTogetherCard(
                        uiState = uiState,
                        onOpenPremium = onOpenPremium,
                        onSavePlan = onSaveNextWeekPlan,
                        onSaveResponse = onSaveNextWeekResponse,
                    )
                }
            }
        }

        if (uiState.weeks.size > 1) {
            item(key = "trend") {
                WeeklyTrendCard(weeks = uiState.weeks)
            }
            item(key = "earlier_weeks_label") {
                Text(
                    text = stringResource(R.string.weekly_review_earlier_weeks),
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalPamojaColors.current.textSecondary,
                    modifier = Modifier.padding(top = Spacing.x2),
                )
            }
        }

        items(uiState.weeks.drop(1), key = { it.weekStart }) { week ->
            WeekSummaryCard(
                week = week,
                currentMembers = uiState.currentMembers,
            )
        }

        if (groupCreatedAt > 0L) {
            item(key = "beginning") {
                GroupBeginningCard(groupCreatedAt)
            }
        }

        item(key = "bottom_space") {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(Spacing.x8),
            )
        }
    }
}

@Composable
private fun NextWeekTogetherCard(
    uiState: WeeklyReviewUiState,
    onOpenPremium: () -> Unit,
    onSavePlan: (NextWeekPlanChoice, Int?) -> Unit,
    onSaveResponse: (NextWeekResponse) -> Unit,
    standalone: Boolean = false,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.xl)
    Column(
        modifier = if (standalone) Modifier.fillMaxWidth() else Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        if (!standalone) Text(
            text = stringResource(R.string.next_week_together_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.next_week_together_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        if (!uiState.groupAccess.hasNextWeekTogether) uiState.scheduledGoal?.let { goal ->
            Text(stringResource(when (goal.status) {
                "applied" -> R.string.planning_summary_applied
                "missed" -> R.string.planning_summary_missed
                else -> R.string.planning_summary_saved
            }, goal.targetSteps, goal.weekStart, goal.timeZone),
                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        when (uiState.groupAccess) {
            GroupAccessState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            is GroupAccessState.Unavailable -> Text(
                stringResource(R.string.planning_access_unavailable), color = colors.textSecondary,
            )
            is GroupAccessState.Preview, GroupAccessState.Free,
            is GroupAccessState.PreviewExpired -> NextWeekPremiumTeaser(onOpenPremium)
            is GroupAccessState.Premium -> NextWeekPremiumContent(
                uiState = uiState,
                onSavePlan = onSavePlan,
                onSaveResponse = onSaveResponse,
            )
        }
    }
}

@Composable
private fun NextWeekPremiumTeaser(onOpenPremium: () -> Unit) {
    val colors = LocalPamojaColors.current
    Text(
        text = stringResource(R.string.next_week_together_premium_body),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
    )
    Button(
        onClick = onOpenPremium,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentPrimary,
            contentColor = colors.textOnBrand,
        ),
    ) {
        Text(stringResource(R.string.next_week_together_unlock))
    }
}

@Composable
private fun NextWeekPremiumContent(
    uiState: WeeklyReviewUiState,
    onSavePlan: (NextWeekPlanChoice, Int?) -> Unit,
    onSaveResponse: (NextWeekResponse) -> Unit,
) {
    val plan = uiState.nextWeekPlan
    var editing by rememberSaveable(plan?.updatedAtMillis) { mutableStateOf(plan == null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    if (uiState.nextWeekStart.isNotBlank()) {
        Text(
            text = formatWeekRange(uiState.nextWeekStart,
                LocalDate.parse(uiState.nextWeekStart).plusDays(6).toString()),
            color = LocalPamojaColors.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (uiState.planningTimeZone.isNotBlank()) {
        Text(stringResource(R.string.planning_calendar_label, uiState.planningTimeZone),
            color = LocalPamojaColors.current.textTertiary,
            style = MaterialTheme.typography.labelSmall)
    }
    if (uiState.dayOnePlanningEnabled && uiState.weeks.isEmpty()) {
        Text(stringResource(R.string.planning_current_goal_basis),
            color = LocalPamojaColors.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium)
    }


    if (!uiState.nextWeekPlanLoaded && uiState.nextWeekPlanError == null) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    } else if (uiState.nextWeekPlanError != null && !uiState.nextWeekPlanLoaded) {
        // The notice below owns this state; a failed request is not loading.
    } else if (uiState.isOrganizer && (plan == null || editing)) {
        NextWeekPlanEditor(
            currentTarget = uiState.group?.weeklyTarget ?: 0,
            planChoice = plan?.choice,
            planTarget = plan?.targetSteps,
            saving = uiState.isSavingNextWeekPlan,
            enabled = !uiState.isOffline && uiState.nextWeekPlanError == null,
            onSave = { choice, target ->
                onSavePlan(choice, target)
                editing = false
            },
        )
    } else if (plan == null) {
        Text(
            text = stringResource(R.string.next_week_together_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalPamojaColors.current.textTertiary,
        )
    } else {
        NextWeekPlanSummary(
            uiState = uiState,
            canEdit = uiState.isOrganizer && plan.status == NextWeekPlanStatus.Scheduled,
            onEdit = { editing = true },
            onSaveResponse = onSaveResponse,
        )
    }

    TextButton(onClick = { showDetails = true }) {
        Text(stringResource(R.string.planning_details_action))
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(stringResource(R.string.planning_details_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                Text(stringResource(R.string.planning_details_body))
                if (uiState.planningTimeZone.isNotBlank()) {
                    Text(stringResource(R.string.planning_time_zone, uiState.planningTimeZone))
                }
                if (plan != null) Text(
                    if (plan.sourceWeekStart == null || plan.basisTotalSteps == null)
                        stringResource(R.string.planning_saved_current_basis, formatSteps(plan.basisTargetSteps))
                    else stringResource(R.string.next_week_together_basis,
                        formatSteps(plan.basisTotalSteps), formatSteps(plan.basisTargetSteps)))
            }
        },
        confirmButton = { TextButton(onClick = { showDetails = false }) {
            Text(stringResource(R.string.common_dismiss))
        } },
    )

    uiState.nextWeekPlanError?.let { error ->
        val context = LocalContext.current
        val copy = error.toErrorCopy()
        PamojaNotice(
            icon = PamojaIcons.AlertCircle,
            title = stringResource(copy.title),
            body = copy.body(context),
            tone = NoticeTone.Warning,
        )
    }
}

@Composable
private fun NextWeekPlanEditor(
    currentTarget: Int,
    planChoice: NextWeekPlanChoice?,
    planTarget: Int?,
    saving: Boolean,
    enabled: Boolean = true,
    onSave: (NextWeekPlanChoice, Int?) -> Unit,
) {
    val colors = LocalPamojaColors.current
    var choice by rememberSaveable(planChoice) {
        mutableStateOf(planChoice ?: NextWeekPlanChoice.Repeat)
    }
    var customTarget by rememberSaveable(planTarget, currentTarget) {
        mutableIntStateOf(planTarget ?: currentTarget)
    }

    Text(
        text = stringResource(R.string.next_week_together_organizer_prompt),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textPrimary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        PlanChoiceChip(
            label = stringResource(R.string.next_week_together_repeat),
            selected = choice == NextWeekPlanChoice.Repeat,
            onClick = { choice = NextWeekPlanChoice.Repeat },
            modifier = Modifier.weight(1f),
        )
        PlanChoiceChip(
            label = stringResource(R.string.next_week_together_gentler),
            selected = choice == NextWeekPlanChoice.Gentler,
            onClick = { choice = NextWeekPlanChoice.Gentler },
            modifier = Modifier.weight(1f),
        )
        PlanChoiceChip(
            label = stringResource(R.string.next_week_together_custom),
            selected = choice == NextWeekPlanChoice.Custom,
            onClick = { choice = NextWeekPlanChoice.Custom },
            modifier = Modifier.weight(1f),
        )
    }
    if (choice == NextWeekPlanChoice.Custom) {
        WeeklyGoalPicker(
            selectedGoal = customTarget,
            onGoalSelected = { customTarget = it },
            enabled = enabled && !saving,
        )
    }
    val chosenTarget = com.pamoja.app.domain.usecase.targetForChoice(currentTarget, choice, customTarget)
    val gentlerUnavailable = choice == NextWeekPlanChoice.Gentler && chosenTarget == currentTarget
    Text(
        text = if (gentlerUnavailable) stringResource(R.string.planning_gentler_minimum)
            else stringResource(R.string.planning_selected_target,
                formatSteps((chosenTarget ?: currentTarget).toLong())),
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (choice == NextWeekPlanChoice.Gentler && !gentlerUnavailable) {
        Text(stringResource(R.string.planning_gentler_explanation),
            color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.planning_current_week_unchanged),
        color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = {
            onSave(choice, customTarget.takeIf { choice == NextWeekPlanChoice.Custom })
        },
        enabled = enabled && !saving && chosenTarget != null && !gentlerUnavailable,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentPrimary,
            contentColor = colors.textOnBrand,
        ),
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.textOnBrand,
            )
        } else {
            Text(stringResource(R.string.next_week_together_schedule))
        }
    }
}

@Composable
private fun PlanChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun NextWeekPlanSummary(
    uiState: WeeklyReviewUiState,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onSaveResponse: (NextWeekResponse) -> Unit,
) {
    val plan = requireNotNull(uiState.nextWeekPlan)
    val colors = LocalPamojaColors.current
    val counts = uiState.nextWeekResponseCounts
    Text(
        text = stringResource(
            R.string.next_week_together_scheduled,
            formatSteps(plan.targetSteps.toLong()),
            formatSingleDate(plan.weekStart),
        ),
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
    )
    if (canEdit) {
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.next_week_together_change))
        }
    }

    Text(
        text = stringResource(R.string.next_week_together_poll_prompt),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
    )
    Text(stringResource(R.string.planning_response_explanation),
        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    NextWeekResponseRow(
        selected = uiState.myNextWeekResponse,
        enabled = !uiState.isOffline && !uiState.isSavingNextWeekResponse &&
            uiState.groupAccess.hasNextWeekTogether && plan.status == NextWeekPlanStatus.Scheduled,
        onSelected = onSaveResponse,
    )
    Text(
        text = stringResource(
            R.string.next_week_together_counts,
            counts.inCount,
            counts.preferGentlerCount,
            counts.restingCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
    )
}

@Composable
private fun NextWeekResponseRow(
    selected: NextWeekResponse?,
    enabled: Boolean,
    onSelected: (NextWeekResponse) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        NextWeekResponse.entries.forEach { response ->
            FilterChip(
                selected = selected == response,
                enabled = enabled,
                onClick = { onSelected(response) },
                label = {
                    Text(
                        stringResource(
                            when (response) {
                                NextWeekResponse.In -> R.string.next_week_together_response_in
                                NextWeekResponse.PreferGentler -> R.string.next_week_together_response_gentler
                                NextWeekResponse.Resting -> R.string.next_week_together_response_resting
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactPreviewNotice(validUntilMillis: Long) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accentPrimarySubtle)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Icon(
            painter = painterResource(PamojaIcons.Clock),
            contentDescription = null,
            tint = colors.accentPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(
                R.string.weekly_review_preview_until,
                formatEpochDate(validUntilMillis),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LatestWeekCard(
    latestWeek: GroupWeekSummary,
    previousWeek: GroupWeekSummary?,
    currentMembers: Map<String, WeeklyReviewMember>,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.xl)
    val accent = if (latestWeek.goalHit) colors.accentTeal else colors.accentAmber
    val completionPercent = latestWeek.completionPercent()
    val contributors = latestWeek.contributions
        .asSequence()
        .sortedByDescending { it.stepCount }
        .mapNotNull { currentMembers[it.userId] }
        .take(3)
        .toList()
    val change = previousWeek?.let { latestWeek.totalSteps - it.totalSteps }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.weekly_review_last_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                )
                Text(
                    text = formatWeekRange(latestWeek.weekStart, latestWeek.weekEnd),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = stringResource(
                    if (latestWeek.goalHit) R.string.weekly_review_goal_reached_short
                    else R.string.weekly_review_goal_missed_short,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }

        Text(
            text = formatSteps(latestWeek.totalSteps),
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.weekly_review_steps),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(latestWeek.completionFraction)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.weekly_review_goal_progress,
                    if (completionPercent >= MAX_VISIBLE_PERCENT) "999%+" else "$completionPercent%",
                    formatSteps(latestWeek.targetSteps),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (contributors.isNotEmpty()) {
                ContributorStack(contributors)
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.weekly_review_active_members,
                        latestWeek.activeMemberCount,
                        latestWeek.activeMemberCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }

        if (change != null) {
            Text(
                text = when {
                    change > 0L -> stringResource(
                        R.string.weekly_review_more_than_previous,
                        formatSteps(change),
                    )
                    change < 0L -> stringResource(
                        R.string.weekly_review_less_than_previous,
                        formatSteps(-change),
                    )
                    else -> stringResource(R.string.weekly_review_same_as_previous)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (change >= 0L) colors.accentTeal else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun WeeklyTrendCard(weeks: List<GroupWeekSummary>) {
    val colors = LocalPamojaColors.current
    val visibleWeeks = weeks.take(6).reversed()
    val maximum = visibleWeeks.maxOfOrNull { it.totalSteps }?.coerceAtLeast(1L) ?: 1L
    val bestWeek = weeks.maxByOrNull { it.totalSteps }
    val shape = RoundedCornerShape(PamojaRadii.xl)
    var selectedWeekStart by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x4),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.weekly_review_recent_progress),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            bestWeek?.let {
                Text(
                    text = stringResource(
                        R.string.weekly_review_best_short,
                        formatSteps(it.totalSteps),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
            verticalAlignment = Alignment.Bottom,
        ) {
            visibleWeeks.forEach { week ->
                val fraction = (week.totalSteps.toFloat() / maximum.toFloat())
                    .coerceIn(0.08f, 1f)
                val isSelected = selectedWeekStart == week.weekStart
                val barDescription = stringResource(
                    R.string.weekly_review_chart_bar_description,
                    formatSingleDate(week.weekStart),
                    formatSteps(week.totalSteps),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = barDescription }
                        .clickable { selectedWeekStart = week.weekStart },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(PamojaRadii.xs))
                                .background(
                                    when {
                                        isSelected -> colors.accentPrimary
                                        week.goalHit -> colors.accentTeal
                                        else -> colors.accentAmber
                                    },
                                ),
                        )
                        if (isSelected) {
                            Text(
                                text = stringResource(
                                    R.string.weekly_review_chart_tooltip,
                                    formatSteps(week.totalSteps),
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = colors.textOnBrand,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .clip(RoundedCornerShape(PamojaRadii.xs))
                                    .background(colors.accentPrimary)
                                    .padding(horizontal = Spacing.x2, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.x2))
                    Text(
                        text = formatSingleDate(week.weekStart),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isSelected) colors.accentPrimary else colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSummaryCard(
    week: GroupWeekSummary,
    currentMembers: Map<String, WeeklyReviewMember>,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.xl)
    val accent = if (week.goalHit) colors.accentTeal else colors.accentAmber
    val namedContributors = week.contributions
        .asSequence()
        .sortedByDescending { it.stepCount }
        .mapNotNull { contribution -> currentMembers[contribution.userId] }
        .take(3)
        .toList()
    val completionPercent = week.completionPercent()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(
                width = 1.dp,
                color = if (week.goalHit) colors.accentTeal else colors.borderSubtle,
                shape = shape,
            )
            .padding(Spacing.x4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (week.goalHit) colors.accentTealSubtle else colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (week.goalHit) PamojaIcons.Check else PamojaIcons.Clock,
                    ),
                    contentDescription = null,
                    tint = if (week.goalHit) colors.accentTeal else colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(
                    if (week.goalHit) R.string.weekly_review_row_hit
                    else R.string.weekly_review_row_missed,
                    formatWeekRange(week.weekStart, week.weekEnd),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = if (week.goalHit) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (completionPercent >= MAX_VISIBLE_PERCENT) {
                    stringResource(R.string.weekly_review_percent_overflow)
                } else {
                    stringResource(R.string.weekly_review_percent, completionPercent)
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (week.goalHit) colors.accentTeal else colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x3))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(if (week.goalHit) colors.accentTealSubtle else colors.surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(week.completionFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.weekly_review_total_of_target,
                    formatSteps(week.totalSteps),
                    formatSteps(week.targetSteps),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textSecondary,
            )
            if (namedContributors.isNotEmpty()) {
                ContributorStack(namedContributors)
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.weekly_review_active_members,
                        week.activeMemberCount,
                        week.activeMemberCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun ContributorStack(contributors: List<WeeklyReviewMember>) {
    val description = stringResource(
        R.string.weekly_review_top_contributors,
        contributors.joinToString { it.displayName },
    )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        contributors.forEach { member -> SmallMemberAvatar(member) }
    }
}

@Composable
private fun SmallMemberAvatar(member: WeeklyReviewMember) {
    val gradient = GroupAvatarGradients[
        (member.displayName.firstOrNull()?.code ?: 0) % GroupAvatarGradients.size
    ]
    val colors = LocalPamojaColors.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .border(2.dp, colors.surface1, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = member.displayName.take(2).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = androidx.compose.ui.graphics.Color.White,
        )
        if (!member.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = member.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun GroupBeginningCard(createdAt: Long) {
    val colors = LocalPamojaColors.current
    Text(
        text = stringResource(
            R.string.weekly_review_group_created,
            formatEpochDate(createdAt),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val radius = PamojaRadii.xl.toPx()
                drawRoundRect(
                    color = colors.borderDefault,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth,
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            }
            .padding(Spacing.x4),
    )
}

@Composable
private fun WeeklyReviewSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6, vertical = Spacing.x3),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 80.dp)
        repeat(3) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 112.dp)
        }
    }
}

private fun GroupWeekSummary.completionPercent(): Int {
    if (targetSteps <= 0L) return 0
    return ((totalSteps.toDouble() / targetSteps.toDouble()) * 100.0)
        .roundToInt()
        .coerceAtLeast(0)
}

private fun formatEpochDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
    )

private fun formatSingleDate(isoDate: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(LocalDate.parse(isoDate))
    }.getOrDefault(isoDate)

private fun formatWeekRange(startIso: String, endIso: String): String = runCatching {
    val start = LocalDate.parse(startIso)
    val end = LocalDate.parse(endIso)
    val currentYear = LocalDate.now().year
    val spansYears = start.year != end.year
    val startPattern = if (spansYears || start.year != currentYear) "MMM d, yyyy" else "MMM d"
    val endPattern = if (spansYears || end.year != currentYear) "MMM d, yyyy" else "MMM d"
    val startText = DateTimeFormatter.ofPattern(startPattern, Locale.getDefault()).format(start)
    val endText = DateTimeFormatter.ofPattern(endPattern, Locale.getDefault()).format(end)
    "$startText – $endText"
}.getOrDefault("$startIso – $endIso")

private fun formatSteps(steps: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(steps)

private const val MAX_VISIBLE_PERCENT = 1_000

