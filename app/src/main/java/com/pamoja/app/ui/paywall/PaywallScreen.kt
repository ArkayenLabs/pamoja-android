package com.pamoja.app.ui.paywall

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.model.BillingPeriod
import com.pamoja.app.domain.model.PlanLimits
import com.pamoja.app.domain.repository.SubscriptionPlan
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The upgrade screen.
 *
 * Every number it advertises comes from `PlanLimits`, the same object the gates
 * read, so the screen cannot promise twenty members while a slider stops at
 * fifteen. Prices come from the store as pre-formatted strings, never computed
 * here, because Play prices are set per country and change without a release.
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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Nothing left to sell them once they are Premium.
    LaunchedEffect(uiState.justUpgraded) {
        if (uiState.justUpgraded) onDismiss()
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

                Spacer(modifier = Modifier.height(Spacing.x7))

                // ── What you get ────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                    Benefit(
                        icon = PamojaIcons.Users,
                        title = pluralStringResource(
                            R.plurals.paywall_benefit_members,
                            PlanLimits.PREMIUM_MEMBER_CAP,
                            PlanLimits.PREMIUM_MEMBER_CAP,
                        ),
                        body = stringResource(
                            R.string.paywall_benefit_members_sub,
                            PlanLimits.FREE_MEMBER_CAP,
                        ),
                    )
                    Benefit(
                        icon = PamojaIcons.Add,
                        title = stringResource(R.string.paywall_benefit_groups),
                        body = stringResource(
                            R.string.paywall_benefit_groups_sub,
                            PlanLimits.FREE_CREATED_GROUPS,
                        ),
                    )
                    Benefit(
                        icon = PamojaIcons.Clock,
                        title = stringResource(R.string.paywall_benefit_history),
                        body = stringResource(R.string.paywall_benefit_history_sub),
                    )
                    Benefit(
                        icon = PamojaIcons.Trophy,
                        title = stringResource(R.string.paywall_benefit_stats),
                        body = stringResource(R.string.paywall_benefit_stats_sub),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x4))

                // Says plainly what is not behind the wall. Reassurance, and
                // also true: paywalling step tracking would break the groups
                // free users are in.
                Text(
                    text = stringResource(R.string.paywall_free_forever),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )

                Spacer(modifier = Modifier.height(Spacing.x7))

                // ── Plans ───────────────────────────────────────────────────
                when {
                    uiState.isLoading -> {
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 76.dp)
                        Spacer(modifier = Modifier.height(Spacing.x3))
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 76.dp)
                    }

                    uiState.plansUnavailable -> PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.paywall_unavailable_title),
                        body = stringResource(R.string.paywall_unavailable_body),
                        tone = NoticeTone.Neutral,
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                        uiState.plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                isSelected = plan.id == uiState.selectedPlanId,
                                onSelect = { viewModel.selectPlan(plan.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x5))

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
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.paywall_failed_title),
                        body = error.toErrorCopy().body(context),
                        tone = NoticeTone.Danger,
                    )
                    Spacer(modifier = Modifier.height(Spacing.x4))
                }

                Button(
                    onClick = { activity?.let(viewModel::purchase) },
                    enabled = uiState.selectedPlan != null &&
                        !uiState.isPurchasing &&
                        !uiState.isRestoring,
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
                    if (uiState.isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.textOnBrand,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if ((uiState.selectedPlan?.trialDays ?: 0) > 0) {
                                    R.string.paywall_start_trial
                                } else {
                                    R.string.paywall_subscribe
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x3))

                Text(
                    text = stringResource(R.string.paywall_cancel_anytime),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(Spacing.x2))

                // Required by Play, and the thing a returning user on a new
                // phone reaches for first.
                TextButton(
                    onClick = { viewModel.restore() },
                    enabled = !uiState.isPurchasing && !uiState.isRestoring,
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
private fun Benefit(
    @DrawableRes icon: Int,
    title: String,
    body: String,
) {
    val colors = LocalPamojaColors.current

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(PamojaRadii.xs))
                .background(colors.accentPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // Names the free limit next to the premium one. A benefit list
                // that never says what you have today is asking people to
                // guess whether they need it.
                text = body,
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
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) colors.accentPrimarySubtle else colors.surface1)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colors.accentPrimary else colors.borderSubtle,
                shape = shape,
            )
            .clickable(onClick = onSelect)
            .padding(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (plan.period) {
                        BillingPeriod.Annual -> R.string.paywall_plan_annual
                        BillingPeriod.Monthly -> R.string.paywall_plan_monthly
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = plan.price,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        // Selection is carried by the tick as well as by colour and border, so
        // it survives being read without colour perception.
        if (isSelected) {
            Icon(
                painter = painterResource(PamojaIcons.Check),
                contentDescription = stringResource(R.string.common_selected),
                tint = colors.accentPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
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
    return if (plan.trialDays > 0) {
        pluralStringResource(
            R.plurals.paywall_disclosure_trial,
            plan.trialDays,
            plan.trialDays,
            plan.price,
            periodLabel,
        )
    } else {
        stringResource(R.string.paywall_disclosure, plan.price, periodLabel)
    }
}
