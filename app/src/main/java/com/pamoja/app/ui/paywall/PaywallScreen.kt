package com.pamoja.app.ui.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.domain.repository.TrialPeriod
import com.pamoja.app.domain.repository.TrialUnit
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaConfirmDialog
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The upgrade screen.
 *
 * It advertises no caps or counts at all, and that is deliberate. This screen
 * used to headline "up to 20 members" against 8 on free; see `PlanLimits` for
 * why that model was dropped. A pleasant side effect is that there is no longer
 * any number here that could drift out of step with a gate, which is the bug
 * the old version had to actively guard against.
 *
 * Prices come from the store as pre-formatted strings, never computed here,
 * because Play prices are set per country and change without a release.
 *
 * Placed after the aha moment, never before it. For Pamoja that moment is
 * seeing your first synced steps land on the group leaderboard, and paywalling
 * ahead of it converts several times worse.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var showMoveConfirmation by rememberSaveable { mutableStateOf(false) }

    // Nothing left to sell them once they are Premium.
    LaunchedEffect(uiState.justUpgraded) {
        if (uiState.justUpgraded) onDismiss()
    }

    val group = uiState.group
    if (group == null) {
        PaywallGroupContextState(
            isLoading = uiState.isGroupLoading,
            error = uiState.groupError,
            onRetry = viewModel::loadGroupContext,
            onDismiss = onDismiss,
        )
        return
    }

    if (showMoveConfirmation) {
        PamojaConfirmDialog(
            title = stringResource(R.string.paywall_move_title),
            body = stringResource(if (uiState.purchaseContext?.subscriptionIsActive == true || uiState.hasUnconfirmedPurchase)
                R.string.premium_move_named_body else R.string.premium_purchase_named_body,
                uiState.purchaseContext?.sponsoredGroupName ?: stringResource(R.string.premium_another_group), group.name),
            confirmLabel = stringResource(if (uiState.purchaseContext?.subscriptionIsActive == true || uiState.hasUnconfirmedPurchase)
                R.string.paywall_move_confirm else R.string.premium_continue_checkout),
            onConfirm = {
                showMoveConfirmation = false
                if (uiState.hasUnconfirmedPurchase) viewModel.moveSponsorshipHere()
                else activity?.let { viewModel.purchase(it, confirmMove = true) }
            },
            onDismiss = { showMoveConfirmation = false },
            isDestructive = false,
        )
    }

    val purchaseContext = uiState.purchaseContext
    if (purchaseContext != null && (purchaseContext.groupIsPremium || purchaseContext.subscriptionIsActive)) {
        val needsCapacityUpgrade = purchaseContext.assignedElsewhere &&
            !purchaseContext.hasAvailableGroupSlot
        Column(modifier = Modifier.fillMaxSize().background(colors.surfaceApp)
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(Spacing.x6), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.paywall_close)) }
            Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary)
            Text(group.name, style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
            PamojaNotice(icon = PamojaIcons.Star,
                title = stringResource(when {
                    purchaseContext.groupIsPremium -> R.string.premium_group_active
                    needsCapacityUpgrade -> R.string.premium_choose_group_title
                    else -> R.string.premium_subscription_found
                }),
                body = stringResource(when {
                    purchaseContext.groupIsPremium -> R.string.premium_group_active_body
                    needsCapacityUpgrade -> R.string.premium_choose_group_body
                    else -> R.string.premium_apply_existing_body
                }),
                tone = NoticeTone.Neutral)
            if (!purchaseContext.groupIsPremium) {
                if (purchaseContext.assignedElsewhere) Text(stringResource(R.string.premium_previous_group,
                    purchaseContext.sponsoredGroupName ?: stringResource(R.string.premium_another_group)),
                    color = colors.textSecondary)

                if (needsCapacityUpgrade) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (uiState.plans.isEmpty()) {
                        PamojaNotice(
                            icon = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.premium_more_groups_unavailable_title),
                            body = stringResource(R.string.premium_more_groups_unavailable_body),
                            tone = NoticeTone.Warning,
                        )
                    } else {
                        uiState.plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = plan.id == uiState.selectedPlanId,
                                onSelect = { viewModel.selectPlan(plan.id) },
                                modifier = Modifier.fillMaxWidth().height(112.dp),
                            )
                        }
                        uiState.selectedPlan?.let { plan ->
                            Text(
                                text = renewalDisclosure(plan),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Button(
                            enabled = uiState.selectedPlan != null && !uiState.isBusy &&
                                !uiState.hasUnconfirmedPurchase,
                            onClick = { activity?.let(viewModel::keepBothGroupsPremium) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.premium_keep_both_action))
                        }
                    }
                    TextButton(
                        enabled = !uiState.isBusy && !uiState.hasUnconfirmedPurchase,
                        onClick = { showMoveConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.paywall_move_action))
                    }
                } else {
                    Button(
                        enabled = !uiState.isBusy && !uiState.hasUnconfirmedPurchase,
                        onClick = { activity?.let { viewModel.purchase(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.premium_apply_existing))
                    }
                }
            }
            if (purchaseContext.isGroupSponsor || purchaseContext.subscriptionIsActive) {
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/account/subscriptions"))) }) {
                    Text(stringResource(R.string.premium_manage_play))
                }
            }
            if (uiState.isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            (uiState.sponsorshipError ?: uiState.error)?.let { error ->
                val copy = error.toErrorCopy()
                PamojaNotice(icon = PamojaIcons.AlertCircle, title = stringResource(copy.title),
                    body = copy.body(context), tone = NoticeTone.Warning)
            }
            TextButton(onClick = {
                if (uiState.hasUnconfirmedPurchase) viewModel.retrySponsorshipVerification()
                else viewModel.loadGroupContext()
            }, enabled = !uiState.isBusy) { Text(stringResource(R.string.premium_refresh_status)) }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Closing must always be one obvious tap. A paywall that traps
            // people is both a policy problem and a refund generator.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(PamojaIcons.Close),
                        contentDescription = stringResource(R.string.paywall_close),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {

                Text(
                    text = stringResource(R.string.paywall_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(Spacing.x2))

                Text(
                    text = stringResource(R.string.paywall_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                SponsoringGroupCard(group = group)

                Spacer(modifier = Modifier.height(Spacing.x4))

                PremiumIncludesCard(planningAvailable = purchaseContext?.planningAvailable == true,
                    trailAvailable = com.pamoja.app.BuildConfig.TOGETHER_TRAIL_ENABLED && purchaseContext?.trailAvailable == true)

                if (purchaseContext?.assignedElsewhere == true) {
                    Spacer(modifier = Modifier.height(Spacing.x3))
                    Text(stringResource(if (purchaseContext.subscriptionIsActive)
                        R.string.premium_previous_group else R.string.premium_previous_group_inactive,
                        purchaseContext.sponsoredGroupName ?: stringResource(R.string.premium_another_group)),
                        color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(Spacing.x4))

                // ── Plans ───────────────────────────────────────────────────
                when {
                    uiState.isLoading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.05f),
                            )
                            SkeletonBlock(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.05f),
                            )
                        }
                    }

                    uiState.plansUnavailable -> PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.paywall_unavailable_title),
                        body = stringResource(R.string.paywall_unavailable_body),
                        tone = NoticeTone.Neutral,
                    )

                    uiState.plans.size == 1 -> {
                        val plan = uiState.plans.single()
                        PlanCard(
                            plan = plan,
                            isSelected = plan.id == uiState.selectedPlanId,
                            onSelect = { viewModel.selectPlan(plan.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp),
                        )
                    }

                    else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                        uiState.plans.take(2).forEach { plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = plan.id == uiState.selectedPlanId,
                                onSelect = { viewModel.selectPlan(plan.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.05f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x4))

                // ── The disclosure, before the button, never after ──────────
                //
                // Play requires price, billing period, renewal and trial terms
                // to be stated before purchase. Putting it under the button
                // technically complies and practically hides it.
                uiState.selectedPlan?.let { plan ->
                    Text(
                        text = renewalDisclosure(plan),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                }

                uiState.error?.let { error ->
                    val copy = error.toErrorCopy()
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(copy.title),
                        body = copy.body(context),
                        tone = NoticeTone.Danger,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                }

                if (uiState.isVerifyingSponsorship || uiState.sponsorshipError != null) {
                    SponsorshipVerificationNotice(
                        groupName = group.name,
                        isVerifying = uiState.isVerifyingSponsorship,
                        hasUnconfirmedPurchase = uiState.hasUnconfirmedPurchase,
                        error = uiState.sponsorshipError,
                        onRetry = viewModel::retrySponsorshipVerification,
                        onMove = { showMoveConfirmation = true },
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                }

                Button(
                    onClick = {
                        if (purchaseContext?.assignedElsewhere == true) showMoveConfirmation = true
                        else activity?.let { viewModel.purchase(it) }
                    },
                    enabled = uiState.selectedPlan != null &&
                        !uiState.isBusy &&
                        !uiState.hasUnconfirmedPurchase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(PamojaRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.textOnBrand,
                        disabledContainerColor = colors.accentPrimarySubtle,
                        disabledContentColor = colors.textTertiary,
                    ),
                ) {
                    if (uiState.isPurchasing || uiState.isVerifyingSponsorship) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.textOnBrand,
                            strokeWidth = 2.dp,
                        )
                        if (uiState.isVerifyingSponsorship) {
                            Spacer(modifier = Modifier.width(Spacing.x2))
                            Text(
                                text = stringResource(R.string.paywall_verifying_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(
                                if (uiState.selectedPlan?.trial != null) {
                                    R.string.paywall_start_trial
                                } else {
                                    R.string.paywall_subscribe
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x2))

                // Required by Play, and the thing a returning user on a new
                // phone reaches for first.
                TextButton(
                    onClick = { viewModel.restore() },
                    enabled = !uiState.isBusy && !uiState.hasUnconfirmedPurchase,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.isRestoring) R.string.paywall_restoring
                            else R.string.paywall_restore
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }

                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(Spacing.x8)
                )
            }
        }
    }
}

@Composable
private fun PaywallGroupContextState(
    isLoading: Boolean,
    error: AppError?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPamojaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.Close),
                    contentDescription = stringResource(R.string.paywall_close),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    color = colors.accentPrimary,
                    strokeWidth = 2.dp,
                )

                error != null -> PamojaErrorState(
                    error = error,
                    onRetry = onRetry.takeIf { error.isRetryable },
                )
            }
        }
    }
}

/**
 * Names the exact group before any price or purchase action appears.
 *
 * This is intentionally not inferred from the previous screen. The ViewModel
 * has already reloaded both this group and the viewer's membership, so the
 * label remains trustworthy after process recreation and stale navigation.
 */
@Composable
internal fun SponsoringGroupCard(group: Group) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(PamojaRadii.sm))
                .background(colors.accentPrimarySubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Users),
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.paywall_sponsoring_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.paywall_sponsoring_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** Payment and server assignment are separate states, stated without jargon. */
@Composable
internal fun SponsorshipVerificationNotice(
    groupName: String,
    isVerifying: Boolean,
    hasUnconfirmedPurchase: Boolean,
    error: AppError?,
    onRetry: () -> Unit,
    onMove: () -> Unit,
) {
    val context = LocalContext.current
    if (isVerifying) {
        PamojaNotice(
            icon = PamojaIcons.Clock,
            title = stringResource(R.string.paywall_verifying_title),
            body = stringResource(R.string.paywall_verifying_body, groupName),
            tone = NoticeTone.Info,
        )
        return
    }

    val visibleError = error ?: return
    val waitingForRevenueCat =
        (visibleError as? AppError.Sponsorship)?.reason ==
            SponsorshipFailure.NoActiveSubscription &&
            hasUnconfirmedPurchase
    val assignedElsewhere =
        (visibleError as? AppError.Sponsorship)?.reason ==
            SponsorshipFailure.SubscriptionAssignedElsewhere &&
            hasUnconfirmedPurchase
    val targetChangedAfterPurchase =
        (visibleError as? AppError.Sponsorship)?.reason in setOf(
            SponsorshipFailure.GroupUnavailable,
            SponsorshipFailure.NotCurrentMember,
            SponsorshipFailure.GroupAlreadySponsored,
        ) && hasUnconfirmedPurchase
    val copy = visibleError.toErrorCopy()
    PamojaNotice(
        icon = PamojaIcons.AlertCircle,
        title = if (waitingForRevenueCat) {
            stringResource(R.string.paywall_verify_failed_title)
        } else if (targetChangedAfterPurchase) {
            stringResource(R.string.paywall_unassigned_title)
        } else {
            stringResource(copy.title)
        },
        body = if (waitingForRevenueCat) {
            stringResource(R.string.paywall_verify_failed_body, groupName)
        } else if (targetChangedAfterPurchase) {
            stringResource(R.string.paywall_unassigned_body, groupName)
        } else {
            copy.body(context)
        },
        tone = if (waitingForRevenueCat || assignedElsewhere || targetChangedAfterPurchase) {
            NoticeTone.Warning
        } else {
            NoticeTone.Danger
        },
        actionLabel = when {
            assignedElsewhere -> stringResource(R.string.paywall_move_action)
            visibleError.isRetryable -> stringResource(R.string.paywall_verify_retry)
            else -> null
        },
        onAction = when {
            assignedElsewhere -> onMove
            visibleError.isRetryable -> onRetry
            else -> null
        },
    )
}

@Composable
private fun PremiumIncludesCard(planningAvailable: Boolean = false, trailAvailable: Boolean = false) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(PamojaRadii.xs))
                .background(colors.accentPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Clock),
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(when {
                    trailAvailable -> R.string.premium_trail_title
                    planningAvailable -> R.string.paywall_benefit_compact_title
                    else -> R.string.premium_history_title
                }),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(when {
                    trailAvailable -> R.string.premium_trail_body
                    planningAvailable -> R.string.paywall_benefit_compact_body
                    else -> R.string.premium_history_body
                }),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) colors.accentPrimarySubtle else colors.surface1)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colors.accentPrimary else colors.borderSubtle,
                shape = shape,
            )
            .clickable(onClick = onSelect)
            .padding(Spacing.x4),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when (plan.period) {
                        BillingPeriod.Annual -> R.string.paywall_plan_annual
                        BillingPeriod.Monthly -> R.string.paywall_plan_monthly
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    painter = painterResource(PamojaIcons.Check),
                    contentDescription = stringResource(R.string.common_selected),
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = plan.price,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(
                when (plan.period) {
                    BillingPeriod.Annual -> R.string.paywall_per_year
                    BillingPeriod.Monthly -> R.string.paywall_per_month
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

/**
 * The sentence Play requires before purchase: what you pay, how often, that it
 * renews, and what a trial turns into.
 */
@Composable
private fun renewalDisclosure(plan: SubscriptionPlan): String {
    val periodLabel = stringResource(
        when (plan.period) {
            BillingPeriod.Annual -> R.string.paywall_period_year
            BillingPeriod.Monthly -> R.string.paywall_period_month
        }
    )
    return plan.trial?.let { trial ->
        stringResource(
            R.string.paywall_disclosure_trial,
            trialLabel(trial),
            plan.price,
            periodLabel,
        )
    } ?: run {
        stringResource(R.string.paywall_disclosure, plan.price, periodLabel)
    }
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
