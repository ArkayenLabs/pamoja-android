package com.pamoja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.Spacing

/**
 * Segmented progress pill for the onboarding flow.
 *
 * Lived in ProfileSetupScreen while three other screens imported it from there,
 * which made an onboarding screen the de facto home of a shared component.
 */
@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current

    Row(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = currentStep.toFloat(),
                range = 0f..totalSteps.toFloat(),
                steps = totalSteps,
            )
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < currentStep) colors.accentPrimary else colors.borderStrong
                    )
            )
        }
    }
}
