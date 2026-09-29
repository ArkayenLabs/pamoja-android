package com.pamoja.app.ui.paywall

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.TrialPeriod
import com.pamoja.app.domain.repository.TrialUnit
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.MaxContentWidth
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * The paywall, on the Lilac kit (REBUILD_PLAN.md Phase 6, canvas board
 * B-Paywall): the group's faces, what Premium adds, the store's plans, and
 * one obvious way out.
 *
 * It opens only from a tap on something locked, or once for the admin when
 * the group's preview ends; never by itself, and never to someone who just
 * joined through an invite.
 *
 * Deliberately left out of the mockup: family check-ins and video recaps
 * (not in the bundle), an earned discount (offers come later), and any price
 * arithmetic such as "save 30%" or "a month each": the store formats prices
 * per country and they are shown exactly as given.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current

    // Nothing left to sell once the server confirms Premium for this group.
    LaunchedEffect(state.justUpgraded) { if (state.justUpgraded) onDismiss() }

    PaywallContent(
        state = state,
        onDismiss = onDismiss,
        onRetryContext = viewModel::loadGroupContext,
        onSelectPlan = viewModel::selectPlan,
        onPurchase = { confirmMove -> activity?.let { viewModel.purchase(it, confirmMove = confirmMove) } },
        onKeepBoth = { activity?.let(viewModel::keepBothGroupsPremium) },
        onMoveHere = viewModel::moveSponsorshipHere,
        onRestore = viewModel::restore,
        onRetryVerification = viewModel::retrySponsorshipVerification,
        onRefresh = {
            if (state.hasUnconfirmedPurchase) viewModel.retrySponsorshipVerification() else viewModel.loadGroupContext()
        },
        onManageInPlay = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/account/subscriptions".toUri()))
            }
        },
    )
}

/** Stateless rendering boundary, used by the debug preview and by tests. */
@Composable
internal fun PaywallContent(
    state: PaywallUiState,
    onDismiss: () -> Unit,
    onRetryContext: () -> Unit,
    onSelectPlan: (String) -> Unit,
    onPurchase: (confirmMove: Boolean) -> Unit,
    onKeepBoth: () -> Unit,
    onMoveHere: () -> Unit,
    onRestore: () -> Unit,
    onRetryVerification: () -> Unit,
    onRefresh: () -> Unit,
    onManageInPlay: () -> Unit,
) {
    var confirmMove by rememberSaveable { mutableStateOf(false) }
    val group = state.group
    val context = state.purchaseContext

    if (confirmMove && group != null) {
        val active = context?.subscriptionIsActive == true || state.hasUnconfirmedPurchase
        KitDialog(
            title = stringResource(R.string.paywall_move_title),
            body = stringResource(
                if (active) R.string.premium_move_named_body else R.string.premium_purchase_named_body,
                context?.sponsoredGroupName ?: stringResource(R.string.premium_another_group),
                group.name,
            ),
            confirmText = stringResource(if (active) R.string.paywall_move_confirm else R.string.premium_continue_checkout),
            onConfirm = {
                confirmMove = false
                if (state.hasUnconfirmedPurchase) onMoveHere() else onPurchase(true)
            },
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { confirmMove = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Kit.colors.page),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(
                title = group?.let { stringResource(R.string.paywall_hero_title, it.name) }
                    ?: stringResource(R.string.paywall_title),
                others = state.otherMembers,
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    group == null -> GroupContext(state, onRetryContext)
                    context != null && (context.groupIsPremium || context.subscriptionIsActive) ->
                        Manage(state, onSelectPlan, onKeepBoth, onMove = { confirmMove = true }, onPurchase, onRefresh, onManageInPlay)
                    else -> Sell(
                        state = state,
                        onSelectPlan = onSelectPlan,
                        onBuy = { if (context?.assignedElsewhere == true) confirmMove = true else onPurchase(false) },
                        onRestore = onRestore,
                        onDismiss = onDismiss,
                        onRetryVerification = onRetryVerification,
                        onMove = { confirmMove = true },
                    )
                }
            }
        }
    }
}

/** Lilac block with the group's faces: Premium is for these people. */
@Composable
private fun Hero(title: String, others: List<Membership>, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Kit.colors.hero)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CircleIconButton(PamojaIcons.Close, stringResource(R.string.paywall_close), onClick = onClose)
        }
        if (others.isNotEmpty()) Faces(others.take(6))
        Text(
            title,
            style = Kit.type.headline.copy(fontSize = 32.sp, lineHeight = 34.sp),
            color = Kit.colors.onHero,
            textAlign = TextAlign.Center,
        )
        Text(
            heroLine(others),
            style = Kit.type.body.copy(fontWeight = FontWeight.Medium),
            color = Kit.colors.heroMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Faces(people: List<Membership>) {
    val size = 60.dp
    val step = 44.dp
    Box(
        Modifier
            .width(size + step * (people.size - 1))
            .height(size)
            .clearAndSetSemantics {},
    ) {
        people.forEachIndexed { index, person ->
            Avatar(
                name = person.displayName,
                photoUrl = person.photoUrl.takeIf { it.isNotBlank() },
                seed = person.userId,
                size = size,
                modifier = Modifier
                    .offset(x = step * index)
                    .border(4.dp, Kit.colors.hero, CircleShape),
            )
        }
    }
}

@Composable
private fun heroLine(others: List<Membership>): String {
    val names = others.map { it.displayName.trim().substringBefore(' ') }.filter { it.isNotBlank() }
    return when (names.size) {
        0 -> stringResource(R.string.paywall_hero_alone)
        1 -> stringResource(R.string.paywall_hero_one, names[0])
        2 -> stringResource(R.string.paywall_hero_two, names[0], names[1])
        else -> pluralStringResource(R.plurals.paywall_hero_many, names.size - 2, names[0], names[1], names.size - 2)
    }
}

@Composable
private fun GroupContext(state: PaywallUiState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val error = state.groupError
    if (state.isGroupLoading || error == null) {
        KitSkeleton(height = 120.dp)
        KitSkeleton(height = 160.dp)
        return
    }
    val copy = error.toErrorCopy()
    KitNotice(
        title = stringResource(copy.title),
        body = copy.body(context),
        danger = true,
        actions = if (error.isRetryable) {
            { PrimaryButton(stringResource(R.string.common_try_again), onClick = onRetry) }
        } else {
            null
        },
    )
}

@Composable
private fun ColumnScope.Sell(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    onRetryVerification: () -> Unit,
    onMove: () -> Unit,
) {
    val group = requireNotNull(state.group)
    val purchaseContext = state.purchaseContext

    Feature(PamojaIcons.ShieldCheck, stringResource(R.string.premium_shields), stringResource(R.string.paywall_feature_shields_body))
    Feature(PamojaIcons.Clock, stringResource(R.string.paywall_feature_history), stringResource(R.string.paywall_feature_history_body))

    if (purchaseContext?.assignedElsewhere == true) {
        Text(
            stringResource(
                if (purchaseContext.subscriptionIsActive) R.string.premium_previous_group else R.string.premium_previous_group_inactive,
                purchaseContext.sponsoredGroupName ?: stringResource(R.string.premium_another_group),
            ),
            style = Kit.type.caption,
            color = Kit.colors.muted,
        )
    }

    Plans(state, onSelectPlan)

    val plan = state.selectedPlan
    plan?.trial?.let { trial -> trialDays(trial)?.let { days -> TrialTimeline(days) } }

    // Before the button, never after: Play requires the price, period,
    // renewal and trial terms to be stated before purchase, and putting them
    // under the button complies on paper and hides them in practice.
    plan?.let { Text(renewalDisclosure(it), style = Kit.type.caption, color = Kit.colors.muted) }

    state.error?.let { error ->
        val copy = error.toErrorCopy()
        KitNotice(title = stringResource(copy.title), body = copy.body(LocalContext.current), danger = true)
    }
    SponsorshipVerificationNotice(
        groupName = group.name,
        isVerifying = state.isVerifyingSponsorship,
        hasUnconfirmedPurchase = state.hasUnconfirmedPurchase,
        error = state.sponsorshipError,
        onRetry = onRetryVerification,
        onMove = onMove,
    )

    PrimaryButton(
        text = when {
            state.isVerifyingSponsorship -> stringResource(R.string.paywall_verifying_button)
            plan?.trial != null -> trialDays(plan.trial)?.let { pluralStringResource(R.plurals.paywall_try_free, it, it) }
                ?: stringResource(R.string.paywall_start_trial)
            else -> stringResource(R.string.paywall_get_premium_for, group.name)
        },
        onClick = onBuy,
        enabled = plan != null && !state.isBusy && !state.hasUnconfirmedPurchase,
        loading = state.isPurchasing,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        QuietButton(stringResource(R.string.paywall_maybe_later), onClick = onDismiss)
        // Required by Play, and the first thing a returning user on a new phone reaches for.
        QuietButton(
            stringResource(if (state.isRestoring) R.string.paywall_restoring else R.string.paywall_restore),
            onClick = onRestore,
            enabled = !state.isBusy && !state.hasUnconfirmedPurchase,
        )
    }
}

@Composable
private fun Feature(icon: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Kit.colors.iconBackground),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Kit.colors.ink, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Kit.type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
            Text(body, style = Kit.type.caption.copy(fontSize = 14.sp), color = Kit.colors.muted)
        }
    }
}

@Composable
private fun Plans(state: PaywallUiState, onSelectPlan: (String) -> Unit) {
    when {
        state.isLoading -> {
            KitSkeleton(height = 72.dp)
            KitSkeleton(height = 72.dp)
        }
        state.plansUnavailable -> KitNotice(
            title = stringResource(R.string.paywall_unavailable_title),
            body = stringResource(R.string.paywall_unavailable_body),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Yearly first, as drawn; which one starts selected is the
            // ViewModel's (tested) decision, not this layout's.
            state.plans.sortedBy { if (it.period == BillingPeriod.Annual) 0 else 1 }.forEach { plan ->
                PlanOption(plan, selected = plan.id == state.selectedPlanId, onSelect = { onSelectPlan(plan.id) })
            }
        }
    }
}

@Composable
private fun PlanOption(plan: SubscriptionPlan, selected: Boolean, onSelect: () -> Unit) {
    val colors = Kit.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .background(if (selected) colors.iconBackground else colors.surface)
            .border(2.dp, if (selected) colors.button else colors.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) colors.button else colors.line, CircleShape),
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.button))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (plan.period == BillingPeriod.Annual) R.string.paywall_plan_annual else R.string.paywall_plan_monthly),
                style = Kit.type.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                stringResource(if (plan.period == BillingPeriod.Annual) R.string.paywall_per_year else R.string.paywall_per_month),
                style = Kit.type.caption,
                color = colors.muted,
            )
        }
        Text(plan.price, style = Kit.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * Today, the reminder, the first charge. The middle step is a promise the
 * app keeps (data/local/billing/TrialReminder), so it is only drawn when
 * there is room for it: a trial shorter than three days has none.
 */
@Composable
private fun TrialTimeline(days: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TimelineStep(stringResource(R.string.paywall_timeline_today), stringResource(R.string.paywall_timeline_access), active = true)
        if (days >= 3) {
            TimelineStep(
                stringResource(R.string.paywall_timeline_day, days - 2),
                stringResource(R.string.paywall_timeline_remind),
                active = false,
            )
        }
        TimelineStep(stringResource(R.string.paywall_timeline_day, days), stringResource(R.string.paywall_timeline_charge), active = false)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TimelineStep(title: String, body: String, active: Boolean) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) Kit.colors.button else Kit.colors.track),
        )
        Text(title, style = Kit.type.caption.copy(fontWeight = FontWeight.ExtraBold))
        Text(body, style = Kit.type.caption, color = Kit.colors.muted)
    }
}

/** Existing subscription or Premium group: status and management, no selling. */
@Composable
private fun ColumnScope.Manage(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onKeepBoth: () -> Unit,
    onMove: () -> Unit,
    onPurchase: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onManageInPlay: () -> Unit,
) {
    val context = requireNotNull(state.purchaseContext)
    val needsCapacity = context.assignedElsewhere && !context.hasAvailableGroupSlot
    KitNotice(
        title = stringResource(
            when {
                context.groupIsPremium -> R.string.premium_group_active
                needsCapacity -> R.string.premium_choose_group_title
                else -> R.string.premium_subscription_found
            },
        ),
        body = stringResource(
            when {
                context.groupIsPremium -> R.string.premium_group_active_body
                needsCapacity -> R.string.premium_choose_group_body
                else -> R.string.premium_apply_existing_body
            },
        ),
    )
    if (!context.groupIsPremium) {
        if (context.assignedElsewhere) {
            Text(
                stringResource(R.string.premium_previous_group, context.sponsoredGroupName ?: stringResource(R.string.premium_another_group)),
                style = Kit.type.caption,
                color = Kit.colors.muted,
            )
        }
        if (needsCapacity) {
            when {
                state.isLoading -> KitSkeleton(height = 72.dp)
                state.plans.isEmpty() -> KitNotice(
                    title = stringResource(R.string.premium_more_groups_unavailable_title),
                    body = stringResource(R.string.premium_more_groups_unavailable_body),
                )
                else -> {
                    state.plans.forEach { plan ->
                        PlanOption(plan, selected = plan.id == state.selectedPlanId, onSelect = { onSelectPlan(plan.id) })
                    }
                    state.selectedPlan?.let { Text(renewalDisclosure(it), style = Kit.type.caption, color = Kit.colors.muted) }
                    PrimaryButton(
                        stringResource(R.string.premium_keep_both_action),
                        onClick = onKeepBoth,
                        enabled = state.selectedPlan != null && !state.isBusy && !state.hasUnconfirmedPurchase,
                        loading = state.isPurchasing,
                    )
                }
            }
            SecondaryButton(
                stringResource(R.string.paywall_move_action),
                onClick = onMove,
                enabled = !state.isBusy && !state.hasUnconfirmedPurchase,
            )
        } else {
            PrimaryButton(
                stringResource(R.string.premium_apply_existing),
                onClick = { onPurchase(false) },
                enabled = !state.isBusy && !state.hasUnconfirmedPurchase,
                loading = state.isBusy,
            )
        }
    }
    (state.sponsorshipError ?: state.error)?.let { error ->
        val copy = error.toErrorCopy()
        KitNotice(title = stringResource(copy.title), body = copy.body(LocalContext.current), danger = true)
    }
    if (context.isGroupSponsor || context.subscriptionIsActive) {
        QuietButton(stringResource(R.string.premium_manage_play), onClick = onManageInPlay)
    }
    QuietButton(stringResource(R.string.premium_refresh_status), onClick = onRefresh, enabled = !state.isBusy)
}

/** Payment and the server's assignment are separate states, stated without jargon. */
@Composable
internal fun SponsorshipVerificationNotice(
    groupName: String,
    isVerifying: Boolean,
    hasUnconfirmedPurchase: Boolean,
    error: AppError?,
    onRetry: () -> Unit,
    onMove: () -> Unit,
) {
    if (isVerifying) {
        KitNotice(
            title = stringResource(R.string.paywall_verifying_title),
            body = stringResource(R.string.paywall_verifying_body, groupName),
        )
        return
    }
    val visible = error ?: return
    val reason = (visible as? AppError.Sponsorship)?.reason
    val waitingForStore = reason == SponsorshipFailure.NoActiveSubscription && hasUnconfirmedPurchase
    val assignedElsewhere = reason == SponsorshipFailure.SubscriptionAssignedElsewhere && hasUnconfirmedPurchase
    val targetChanged = reason in setOf(
        SponsorshipFailure.GroupUnavailable,
        SponsorshipFailure.NotCurrentMember,
        SponsorshipFailure.GroupAlreadySponsored,
    ) && hasUnconfirmedPurchase
    val copy = visible.toErrorCopy()
    KitNotice(
        title = when {
            waitingForStore -> stringResource(R.string.paywall_verify_failed_title)
            targetChanged -> stringResource(R.string.paywall_unassigned_title)
            else -> stringResource(copy.title)
        },
        body = when {
            waitingForStore -> stringResource(R.string.paywall_verify_failed_body, groupName)
            targetChanged -> stringResource(R.string.paywall_unassigned_body, groupName)
            else -> copy.body(LocalContext.current)
        },
        danger = !(waitingForStore || assignedElsewhere || targetChanged),
        actions = when {
            assignedElsewhere -> { { SecondaryButton(stringResource(R.string.paywall_move_action), onClick = onMove) } }
            visible.isRetryable -> { { SecondaryButton(stringResource(R.string.paywall_verify_retry), onClick = onRetry) } }
            else -> null
        },
    )
}

/** Days in a trial, for the timeline; null for units a day count would misstate. */
private fun trialDays(trial: TrialPeriod): Int? = when (trial.unit) {
    TrialUnit.Day -> trial.value
    TrialUnit.Week -> trial.value * 7
    TrialUnit.Month, TrialUnit.Year -> null
}

/**
 * The sentence Play requires before purchase: what you pay, how often, that it
 * renews, and what a trial turns into.
 */
@Composable
private fun renewalDisclosure(plan: SubscriptionPlan): String {
    val period = stringResource(
        if (plan.period == BillingPeriod.Annual) R.string.paywall_period_year else R.string.paywall_period_month,
    )
    return plan.trial?.let { trial ->
        stringResource(R.string.paywall_disclosure_trial, trialLabel(trial), plan.price, period)
    } ?: stringResource(R.string.paywall_disclosure, plan.price, period)
}

@Composable
private fun trialLabel(trial: TrialPeriod): String {
    val resource = when (trial.unit) {
        TrialUnit.Day -> R.plurals.paywall_trial_days
        TrialUnit.Week -> R.plurals.paywall_trial_weeks
        TrialUnit.Month -> R.plurals.paywall_trial_months
        TrialUnit.Year -> R.plurals.paywall_trial_years
    }
    return pluralStringResource(resource, trial.value, trial.value)
}
