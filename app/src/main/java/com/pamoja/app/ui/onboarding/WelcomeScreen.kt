package com.pamoja.app.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.OnboardingProgressBar
import com.pamoja.app.ui.components.PamojaRingMark
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    LaunchedEffect(Unit) {
        viewModel.onScreenViewed()
    }

    // Flat, like every screen in the design system. This carried a hand-written
    // indigo tint from the previous identity, so the first screen of the app
    // was still washed violet after everything behind it went terracotta. It
    // was also the last raw hex outside the theme file.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.x7),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top: progress bar + logo area ─────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.x6))

                OnboardingProgressBar(currentStep = 1, totalSteps = 3)

                Spacer(modifier = Modifier.height(Spacing.x12))

                // The Pamoja mark itself, the same two rings the sign-in screen
                // shows. A gradient tile with a glyph in it was a placeholder
                // for a logo rather than the logo.
                PamojaRingMark(size = 72.dp)

                Spacer(modifier = Modifier.height(Spacing.x6))

                Text(
                    text  = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                Text(
                    text      = stringResource(R.string.welcome_tagline),
                    style     = MaterialTheme.typography.bodyLarge.copy(
                        color     = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.x10))

                // Feature pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
                ) {
                    FeaturePill(
                        icon     = PamojaIcons.Footprints,
                        label    = stringResource(R.string.welcome_feature_steps),
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon     = PamojaIcons.Users,
                        label    = stringResource(R.string.welcome_feature_goals),
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon     = PamojaIcons.Trophy,
                        label    = stringResource(R.string.welcome_feature_leaderboard),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Bottom CTA ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.x6),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick  = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor   = colors.textOnBrand
                    )
                ) {
                    Text(
                        text  = stringResource(R.string.welcome_get_started),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x4))

                // Clickable sign-in link for returning users
                TextButton(onClick = onSignIn) {
                    Text(
                        text  = stringResource(R.string.welcome_have_account),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.accentPrimary
                        )
                    )
                }
            }
        }
    }
}

// ─── Feature Pill ─────────────────────────────────────────────────────────────
@Composable
private fun FeaturePill(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PamojaRadii.md))
            .background(colors.accentPrimarySubtle)
            .padding(vertical = Spacing.x4, horizontal = Spacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.x2)
    ) {
        Icon(
            painter            = painterResource(icon),
            contentDescription = null,
            tint               = colors.accentPrimary,
            modifier           = Modifier.size(20.dp)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color     = colors.accentPrimary,
                textAlign = TextAlign.Center,
                fontSize  = 10.sp
            )
        )
    }
}
