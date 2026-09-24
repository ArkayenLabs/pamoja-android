package com.pamoja.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

/**
 * The one conditional identity step.
 *
 * Google and email sign-up already provide a name and never reach this screen.
 * It exists for a new phone account, or a provider account whose name is empty.
 */
@Composable
fun ProfileSetupScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }

    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val nameErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> nameRequired
            input.trim().length > 50 -> nameTooLong
            else -> null
        }
    }
    val nameError = nameErrorFor(name)

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.clearSuccess()
            onContinue()
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
        validateName = nameErrorFor,
        nameError = nameError,
        isLoading = uiState.isLoading,
        isOffline = uiState.isOffline,
        errorBody = uiState.error?.toErrorCopy()?.body(context),
        onSubmit = {
            viewModel.createProfile(
                name = name.trim(),
                age = null,
                height = null,
                weight = null,
            )
        },
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun ProfileSetupContent(
    name: String,
    onNameChange: (String) -> Unit,
    validateName: (String) -> String?,
    nameError: String?,
    isLoading: Boolean,
    isOffline: Boolean,
    errorBody: String?,
    onSubmit: () -> Unit,
) {
    val colors = LocalPamojaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding(),
    ) {
        OfflineBanner(isOffline = isOffline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.x5),
        ) {
            Spacer(Modifier.height(Spacing.x5))
            PamojaMark()

            Spacer(Modifier.height(Spacing.x8))
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.x2))
            Text(
                text = stringResource(R.string.profile_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(Spacing.x7))
            PamojaTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.profile_name_label),
                placeholder = stringResource(R.string.profile_name_placeholder),
                enabled = !isLoading,
                validate = validateName,
            )

            if (isOffline) {
                Spacer(Modifier.height(Spacing.x4))
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = stringResource(R.string.profile_offline_title),
                    body = stringResource(R.string.profile_offline_body),
                    tone = NoticeTone.Warning,
                )
            } else if (errorBody != null) {
                Spacer(Modifier.height(Spacing.x4))
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = stringResource(R.string.profile_failed_title),
                    body = errorBody,
                    tone = NoticeTone.Danger,
                )
            }

            Spacer(Modifier.height(Spacing.x7))
            Button(
                onClick = onSubmit,
                enabled = nameError == null && !isLoading && !isOffline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = colors.textTertiary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.common_continue),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.x8))
        }
    }
}
