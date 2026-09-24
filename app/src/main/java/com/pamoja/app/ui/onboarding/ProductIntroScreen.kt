package com.pamoja.app.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.ui.auth.AuthBackButton
import com.pamoja.app.ui.components.OnboardingProgressBar
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

/** First-run product story. There is deliberately no authentication here. */
@Composable
fun ProductIntroOneScreen(
    onContinue: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.onScreenViewed() }
    ProductIntroOneContent(onContinue = onContinue)
}

/** Explains the shared goal before the app asks the person to make an account. */
@Composable
fun ProductIntroTwoScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    ProductIntroTwoContent(
        onBack = onBack,
        onContinue = { viewModel.completeIntro(onContinue) },
    )
}

@Composable
internal fun ProductIntroOneContent(onContinue: () -> Unit) {
    val colors = LocalPamojaColors.current

    IntroColumn {
        IntroHeader(step = 1)

        Spacer(Modifier.height(Spacing.x7))
        PamojaMark(size = 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.x3))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.x2))
        Text(
            text = stringResource(R.string.intro_one_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.x7))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PamojaRadii.xl))
                .background(colors.surface1)
                .border(
                    width = 1.dp,
                    color = colors.borderSubtle,
                    shape = RoundedCornerShape(PamojaRadii.xl),
                )
                .padding(vertical = Spacing.x2),
        ) {
            IntroFeature(
                icon = PamojaIcons.Footprints,
                title = stringResource(R.string.welcome_feature_steps),
                body = stringResource(R.string.intro_feature_steps_body),
                iconBackground = colors.accentPrimarySubtle,
                iconColor = colors.accentPrimary,
            )
            IntroDivider()
            IntroFeature(
                icon = PamojaIcons.Users,
                title = stringResource(R.string.welcome_feature_goals),
                body = stringResource(R.string.intro_feature_goals_body),
                iconBackground = colors.accentTealSubtle,
                iconColor = colors.accentTeal,
            )
            IntroDivider()
            IntroFeature(
                icon = PamojaIcons.Trophy,
                title = stringResource(R.string.welcome_feature_leaderboard),
                body = stringResource(R.string.intro_feature_leaderboard_body),
                iconBackground = colors.accentAmberSubtle,
                iconColor = colors.accentAmber,
            )
        }

        Spacer(Modifier.height(Spacing.x6))
        IntroContinueButton(onClick = onContinue)
        Spacer(Modifier.height(Spacing.x5))
    }
}

@Composable
internal fun ProductIntroTwoContent(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalPamojaColors.current

    IntroColumn {
        IntroHeader(step = 2, onBack = onBack)

        Spacer(Modifier.height(Spacing.x6))
        Text(
            text = stringResource(R.string.intro_two_title),
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.x2))
        Text(
            text = stringResource(R.string.intro_two_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Spacing.x6))
        SharedWeekHero()

        Spacer(Modifier.height(Spacing.x5))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PamojaRadii.lg))
                .background(colors.accentTealSubtle)
                .padding(Spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Users),
                contentDescription = null,
                tint = colors.accentTeal,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Spacing.x3))
            Column {
                Text(
                    text = stringResource(R.string.intro_two_fair_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.intro_two_fair_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(Spacing.x6))
        IntroContinueButton(onClick = onContinue)
        Spacer(Modifier.height(Spacing.x5))
    }
}

@Composable
private fun IntroColumn(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPamojaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.x5),
        content = content,
    )
}

@Composable
private fun IntroHeader(
    step: Int,
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalPamojaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { AuthBackButton(onBack = it) }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_step_count, step, 2),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(Spacing.x2))
        OnboardingProgressBar(
            currentStep = step,
            totalSteps = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IntroFeature(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(PamojaRadii.md))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(23.dp),
            )
        }
        Spacer(Modifier.width(Spacing.x3))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun IntroDivider() {
    val colors = LocalPamojaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 74.dp, end = Spacing.x4)
            .height(1.dp)
            .background(colors.borderSubtle),
    )
}

@Composable
private fun IntroContinueButton(onClick: () -> Unit) {
    val colors = LocalPamojaColors.current
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentPrimary,
            contentColor = colors.textOnBrand,
        ),
    ) {
        Text(
            text = stringResource(R.string.common_continue),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
