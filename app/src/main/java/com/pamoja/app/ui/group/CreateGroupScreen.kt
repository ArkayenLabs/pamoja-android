package com.pamoja.app.ui.group

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * Start a group with just a name (REBUILD_PLAN.md §4). The goal, member limit
 * and week start take the defaults the old form started from, and the admin
 * changes them later in group settings, when there is a group to judge by.
 */
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let {
            viewModel.clearCreatedGroupId()
            onGroupCreated(it)
        }
    }

    CreateGroupContent(
        isLoading = uiState.isLoading,
        isOffline = uiState.isOffline,
        errorBody = uiState.error?.toErrorCopy()?.body(context),
        onCreate = { name ->
            viewModel.createGroup(
                name = name,
                weeklyTarget = StepGoal.DEFAULT_WEEKLY_TOTAL,
                maxMemberCap = DEFAULT_MEMBER_CAP,
                // Monday across most of Europe and Asia, Sunday in the US,
                // Canada and Japan: the device locale's own answer.
                weekStartDay = WeekWindow.localeDefault(),
            )
        },
        onBack = onBack,
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun CreateGroupContent(
    isLoading: Boolean,
    isOffline: Boolean,
    errorBody: String?,
    onCreate: (String) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val max = UpdateGroupSettingsUseCase.MAX_GROUP_NAME_LENGTH
    // One rule for the field and the button: a name of only spaces must not
    // reach Firestore trimmed to "".
    val nameError = when {
        name.isBlank() -> stringResource(R.string.create_group_name_required)
        name.trim().length > max -> stringResource(R.string.create_group_name_too_long)
        else -> null
    }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.create_group_submit),
                onClick = { onCreate(name.trim()) },
                enabled = nameError == null && !isOffline,
                loading = isLoading,
            )
        },
    ) {
        Text(stringResource(R.string.start_group_title), style = Kit.type.headline)
        Text(stringResource(R.string.start_group_body), style = Kit.type.body, color = Kit.colors.muted)
        KitTextField(
            value = name,
            onValueChange = {
                touched = true
                name = it.take(max)
            },
            label = stringResource(R.string.create_group_name_label),
            placeholder = stringResource(R.string.create_group_name_placeholder),
            helper = stringResource(R.string.group_name_character_count, name.length, max),
            error = nameError.takeIf { touched },
            enabled = !isLoading,
        )
        if (isOffline) {
            KitNotice(
                title = stringResource(R.string.create_group_offline_title),
                body = stringResource(R.string.create_group_offline_body),
            )
        } else if (errorBody != null) {
            KitNotice(title = stringResource(R.string.create_group_failed_title), body = errorBody, danger = true)
        }
    }
}

/** The member limit a new group starts with; the admin can raise it later. */
private const val DEFAULT_MEMBER_CAP = 10
