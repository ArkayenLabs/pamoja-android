package com.pamoja.app.ui.onboarding

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
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CheckRow
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton

/**
 * The name step every new account passes through (REBUILD_PLAN.md §6).
 *
 * Opens prefilled with the name Google or the email sign-up form provided;
 * phone accounts start empty. It is also where the Terms' 18+ requirement is
 * asked, which is why no new account skips it any more.
 *
 * [onContinue] is told whether an invite is waiting: with one, Home opens its
 * preview; without, the next step is starting a group.
 */
@Composable
fun ProfileSetupScreen(
    onContinue: (hasPendingInvite: Boolean) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var isAdult by rememberSaveable { mutableStateOf(false) }

    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val nameError = when {
        name.isBlank() -> nameRequired
        name.trim().length > 50 -> nameTooLong
        else -> null
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.clearSuccess()
            onContinue(uiState.hasPendingInvite)
        }
    }
    LaunchedEffect(uiState.suggestedName) {
        if (name.isBlank() && uiState.suggestedName.isNotBlank()) {
            name = uiState.suggestedName
        }
    }
    LaunchedEffect(Unit) { viewModel.onProfileSetupStarted() }

    ProfileSetupContent(
        name = name,
        onNameChange = { name = it },
        nameError = nameError,
        isAdult = isAdult,
        onAdultChange = { isAdult = it },
        isLoading = uiState.isLoading,
        isOffline = uiState.isOffline,
        errorBody = uiState.error?.toErrorCopy()?.body(context),
        onSubmit = {
            viewModel.createProfile(name = name.trim(), age = null, height = null, weight = null)
        },
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun ProfileSetupContent(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    isAdult: Boolean,
    onAdultChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isOffline: Boolean,
    errorBody: String?,
    onSubmit: () -> Unit,
) {
    var touched by rememberSaveable { mutableStateOf(false) }
    KitScreen(
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.common_continue),
                onClick = onSubmit,
                enabled = nameError == null && isAdult && !isOffline,
                loading = isLoading,
            )
        },
    ) {
        Text(stringResource(R.string.name_step_title), style = Kit.type.headline)
        Text(stringResource(R.string.name_step_body), style = Kit.type.body, color = Kit.colors.muted)
        KitTextField(
            value = name,
            onValueChange = {
                touched = true
                onNameChange(it)
            },
            label = stringResource(R.string.profile_name_label),
            placeholder = stringResource(R.string.profile_name_placeholder),
            // Only after typing: a prefilled or untouched field is not an error yet.
            error = nameError.takeIf { touched },
            enabled = !isLoading,
        )
        CheckRow(
            checked = isAdult,
            onCheckedChange = onAdultChange,
            text = stringResource(R.string.name_step_adult),
            enabled = !isLoading,
        )
        if (isOffline) {
            KitNotice(
                title = stringResource(R.string.profile_offline_title),
                body = stringResource(R.string.profile_offline_body),
            )
        } else if (errorBody != null) {
            KitNotice(title = stringResource(R.string.profile_failed_title), body = errorBody, danger = true)
        }
    }
}
