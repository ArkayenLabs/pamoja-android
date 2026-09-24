package com.pamoja.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.domain.model.PasswordPolicy
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

/** A dedicated account-creation screen. Returning users sign in on the entry page. */
@Composable
fun EmailAuthScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    val emailRequired = stringResource(R.string.email_required)
    val emailInvalid = stringResource(R.string.email_invalid)
    val passwordTooShort = stringResource(R.string.password_too_short)
    val passwordMismatch = stringResource(R.string.password_mismatch)
    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)

    val emailErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> emailRequired
            !android.util.Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches() -> emailInvalid
            else -> null
        }
    }
    val passwordErrorFor: (String) -> String? = { input ->
        if (PasswordPolicy.isAccepted(input)) null else passwordTooShort
    }
    val confirmationErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> passwordMismatch
            input != password -> passwordMismatch
            else -> null
        }
    }
    val isValid = name.isNotBlank() &&
        name.trim().length <= 50 &&
        emailErrorFor(email) == null &&
        passwordErrorFor(password) == null &&
        confirmationErrorFor(confirmation) == null

    LaunchedEffect(Unit) { viewModel.clearError() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.x6),
        ) {
            Spacer(modifier = Modifier.height(Spacing.x2))
            AuthTopBar(
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(Spacing.x5))
            Text(
                text = stringResource(R.string.email_signup_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.x6))
            PamojaTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.profile_name_label),
                placeholder = stringResource(R.string.profile_name_placeholder),
                validate = { input ->
                    when {
                        input.isBlank() -> nameRequired
                        input.trim().length > 50 -> nameTooLong
                        else -> null
                    }
                },
            )

            Spacer(modifier = Modifier.height(Spacing.x4))
            PamojaTextField(
                value = email,
                onValueChange = {
                    email = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.email_label),
                placeholder = stringResource(R.string.email_placeholder),
                keyboardType = KeyboardType.Email,
                validate = emailErrorFor,
            )

            Spacer(modifier = Modifier.height(Spacing.x4))
            PamojaTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.password_label),
                placeholder = stringResource(R.string.password_placeholder_new),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                validate = passwordErrorFor,
            )

            Spacer(modifier = Modifier.height(Spacing.x4))
            PamojaTextField(
                value = confirmation,
                onValueChange = {
                    confirmation = it
                    viewModel.clearError()
                },
                label = stringResource(R.string.password_confirm_label),
                placeholder = stringResource(R.string.password_confirm_placeholder),
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (isValid) viewModel.signUpWithEmail(name, email, password)
                },
                isPassword = true,
                validate = confirmationErrorFor,
            )

            uiState.error?.let { message ->
                Spacer(modifier = Modifier.height(Spacing.x4))
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = message.authErrorTitle(),
                    body = message.authErrorBody(),
                    tone = NoticeTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))
            Button(
                onClick = { viewModel.signUpWithEmail(name, email, password) },
                enabled = isValid && uiState.busyWith == null,
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
                if (uiState.busyWith == AuthMethod.Email) {
                    CircularProgressIndicator(
                        color = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.email_create_account),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.email_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(R.string.common_sign_in),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x2))
            AuthLegalLine(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(Spacing.x8))
        }
    }
}
