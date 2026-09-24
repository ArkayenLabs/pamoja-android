package com.pamoja.app.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import androidx.compose.ui.res.stringResource

/**
 * One compact chooser for creation and management.
 *
 * Presets keep the common path fast. Custom lives behind one deliberate tap so
 * a family that wants 300K is not blocked, while a first-time organizer is not
 * confronted with a blank number field before they understand the product.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyGoalPicker(
    selectedGoal: Int,
    onGoalSelected: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var customAttempted by rememberSaveable { mutableStateOf(false) }

    val isCustom = !StepGoal.isPresetWeeklyTotal(selectedGoal)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        StepGoal.PRESETS_WEEKLY_TOTAL.forEach { goal ->
            WeeklyGoalChoice(
                label = compactGoalLabel(goal),
                selected = selectedGoal == goal,
                enabled = enabled,
                onClick = { onGoalSelected(goal) },
            )
        }
        WeeklyGoalChoice(
            label = stringResource(R.string.weekly_goal_custom),
            selected = isCustom,
            enabled = enabled,
            onClick = {
                customText = if (isCustom) selectedGoal.toString() else ""
                customAttempted = false
                showCustomDialog = true
            },
        )
    }

    if (showCustomDialog) {
        val customGoal = customText.toIntOrNull()
        val isValid = customGoal != null && StepGoal.isSelectableWeeklyTotal(customGoal)
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.weekly_goal_custom_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.weekly_goal_custom_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.x3))
                    PamojaTextField(
                        value = customText,
                        onValueChange = { entered ->
                            customText = entered.filter(Char::isDigit).take(7)
                            customAttempted = false
                        },
                        label = stringResource(R.string.weekly_goal_custom_label),
                        placeholder = stringResource(R.string.weekly_goal_custom_placeholder),
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        supportingText = stringResource(R.string.weekly_goal_custom_helper),
                        error = if (customAttempted && !isValid) {
                            stringResource(R.string.weekly_goal_custom_error)
                        } else null,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isValid) {
                            onGoalSelected(checkNotNull(customGoal))
                            showCustomDialog = false
                        } else {
                            customAttempted = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.weekly_goal_custom_use))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = colors.surface1,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

@Composable
private fun WeeklyGoalChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val border = if (selected) colors.accentPrimary else colors.borderDefault
    val background = if (selected) colors.accentPrimarySubtle else colors.surface2

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .background(background)
            .border(BorderStroke(1.dp, border), PillShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> colors.textTertiary
                selected -> colors.accentPrimary
                else -> colors.textSecondary
            },
        )
    }
}

private fun compactGoalLabel(value: Int): String = when {
    value >= 1_000_000 -> {
        val tenths = value / 100_000
        if (tenths % 10 == 0) "${tenths / 10}m" else "${tenths / 10}.${tenths % 10}m"
    }
    else -> "${value / 1_000}k"
}
