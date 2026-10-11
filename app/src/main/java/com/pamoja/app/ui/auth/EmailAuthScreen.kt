package com.pamoja.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.PasswordPolicy
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

/** A dedicated account-creation screen. Returning users sign in on the entry page. */
@Composable
fun EmailAuthScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearError() }

    EmailSignUpContent(
        busy = uiState.busyWith == AuthMethod.Email,
        blocked = uiState.busyWith != null,
        error = uiState.error,
        onSignUp = viewModel::signUpWithEmail,
        onClearError = viewModel::clearError,
        onBack = onBack,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun EmailSignUpContent(
    busy: Boolean,
    blocked: Boolean,
    error: AppError?,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val emailInvalid = stringResource(R.string.email_invalid)
    val passwordTooShort = stringResource(R.string.password_too_short)
    val passwordMismatch = stringResource(R.string.password_mismatch)

    // Each problem shows once there is something typed to be wrong, so an
    // empty form is not a wall of red; the button stays off until all is well.
    val nameError = nameTooLong.takeIf { name.trim().length > com.pamoja.app.domain.model.User.MAX_NAME_LENGTH }
    val emailError = emailInvalid.takeIf {
        email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
    val passwordError = passwordTooShort.takeIf { password.isNotEmpty() && !PasswordPolicy.isAccepted(password) }
    val confirmationError = passwordMismatch.takeIf { confirmation.isNotEmpty() && confirmation != password }
    val isValid = name.isNotBlank() && nameError == null &&
        email.isNotBlank() && emailError == null &&
        PasswordPolicy.isAccepted(password) && confirmation == password
    val signUp = { if (isValid && !blocked) onSignUp(name, email, password) }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.email_create_account),
                onClick = signUp,
                enabled = isValid && !blocked,
                loading = busy,
            )
            QuietButton(
                text = "${stringResource(R.string.email_have_account)} ${stringResource(R.string.common_sign_in)}",
                onClick = onBack,
            )
            AuthLegalLine(
                modifier = Modifier.fillMaxWidth(),
                textColor = Kit.colors.muted,
                linkColor = Kit.colors.ink,
                style = Kit.type.caption.copy(textAlign = TextAlign.Center),
            )
        },
    ) {
        Text(stringResource(R.string.email_signup_title), style = Kit.type.headline)
        KitTextField(
            value = name,
            onValueChange = {
                name = it
                onClearError()
            },
            label = stringResource(R.string.profile_name_label),
            placeholder = stringResource(R.string.profile_name_placeholder),
            error = nameError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        KitTextField(
            value = email,
            onValueChange = {
                email = it
                onClearError()
            },
            label = stringResource(R.string.email_label),
            placeholder = stringResource(R.string.email_placeholder),
            error = emailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        KitTextField(
            value = password,
            onValueChange = {
                password = it
                onClearError()
            },
            label = stringResource(R.string.password_label),
            placeholder = stringResource(R.string.password_placeholder_new),
            error = passwordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        KitTextField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                onClearError()
            },
            label = stringResource(R.string.password_confirm_label),
            placeholder = stringResource(R.string.password_confirm_placeholder),
            error = confirmationError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { signUp() }),
        )
        error?.let {
            KitNotice(title = it.authErrorTitle(), body = it.authErrorBody(), danger = true)
        }
    }
}
