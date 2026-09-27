package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
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
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * One screen for new and returning people alike (REBUILD_PLAN.md §6): the
 * same three options either way, and AuthRouting decides afterwards whether
 * this account needs a profile.
 */
@Composable
fun AuthLandingScreen(
    viewModel: AuthViewModel,
    onChoosePhone: () -> Unit,
    onChooseEmail: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(Unit) { viewModel.clearError() }

    AuthEntryContent(
        busyWith = uiState.busyWith,
        error = uiState.error,
        onGoogle = { activity?.let(viewModel::signInWithGoogle) },
        onPhone = onChoosePhone,
        onEmail = onChooseEmail,
        onBack = onBack,
    )
}

/** Stateless action boundary used by visual previews and interaction tests. */
@Composable
internal fun AuthEntryContent(
    busyWith: AuthMethod?,
    error: AppError?,
    onGoogle: () -> Unit,
    onPhone: () -> Unit,
    onEmail: () -> Unit,
    onBack: (() -> Unit)? = null,
) = KitInAppTheme {
    KitScreen(
        top = if (onBack != null) {
            {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            }
        } else {
            null
        },
        bottom = {
            AuthLegalLine(
                modifier = Modifier.fillMaxWidth(),
                textColor = Kit.colors.muted,
                linkColor = Kit.colors.ink,
                style = Kit.type.caption.copy(textAlign = TextAlign.Center),
            )
        },
    ) {
        Text(stringResource(R.string.signin_title), style = Kit.type.headline)
        Text(stringResource(R.string.signin_body), style = Kit.type.body, color = Kit.colors.muted)

        // Google's own mark in its own colours, on a neutral button: what its
        // branding rules allow for a "Continue with Google" control.
        SecondaryButton(
            text = stringResource(
                if (busyWith == AuthMethod.Google) R.string.auth_opening_google else R.string.auth_continue_google,
            ),
            onClick = onGoogle,
            icon = PamojaIcons.Google,
            tintIcon = false,
            loading = busyWith == AuthMethod.Google,
            enabled = busyWith == null,
        )
        SecondaryButton(
            text = stringResource(R.string.auth_continue_phone),
            onClick = onPhone,
            icon = PamojaIcons.Smartphone,
            enabled = busyWith == null,
        )
        SecondaryButton(
            text = stringResource(R.string.signin_continue_email),
            onClick = onEmail,
            icon = PamojaIcons.Mail,
            enabled = busyWith == null,
        )

        error?.let {
            KitNotice(title = it.authErrorTitle(), body = it.authErrorBody(), danger = true)
        }
    }
}

/**
 * Email and password for an existing account. Creating one is a separate,
 * explicit step (EmailAuthScreen), as it was before the rebuild.
 */
@Composable
fun EmailSignInScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearError() }

    EmailSignInContent(
        busy = uiState.busyWith == AuthMethod.Email,
        error = uiState.error,
        onSignIn = viewModel::signInWithEmail,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
        onClearError = viewModel::clearError,
        onBack = onBack,
    )
}

@Composable
internal fun EmailSignInContent(
    busy: Boolean,
    error: AppError?,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onClearError: () -> Unit = {},
    onBack: () -> Unit,
) = KitInAppTheme {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var emailTouched by rememberSaveable { mutableStateOf(false) }

    val emailRequired = stringResource(R.string.email_required)
    val emailInvalid = stringResource(R.string.email_invalid)
    val emailError = when {
        email.isBlank() -> emailRequired
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> emailInvalid
        else -> null
    }
    val canSignIn = emailError == null && password.isNotBlank() && !busy
    val signIn = { if (canSignIn) onSignIn(email.trim(), password) }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.common_sign_in),
                onClick = signIn,
                enabled = canSignIn,
                loading = busy,
            )
            QuietButton(
                text = "${stringResource(R.string.auth_new_here)} ${stringResource(R.string.auth_create_account)}",
                onClick = onCreateAccount,
            )
        },
    ) {
        Text(stringResource(R.string.email_signin_title), style = Kit.type.headline)
        KitTextField(
            value = email,
            onValueChange = {
                email = it
                emailTouched = true
                onClearError()
            },
            label = stringResource(R.string.email_label),
            placeholder = stringResource(R.string.email_placeholder),
            // Shown only once they have typed, so an empty form is not a wall of red.
            error = emailError.takeIf { emailTouched && email.isNotEmpty() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        KitTextField(
            value = password,
            onValueChange = {
                password = it
                onClearError()
            },
            label = stringResource(R.string.password_label),
            placeholder = stringResource(R.string.password_placeholder_existing),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { signIn() }),
        )
        QuietButton(
            text = stringResource(R.string.email_forgot),
            onClick = { onForgotPassword(email.trim()) },
        )
        error?.let {
            KitNotice(title = it.authErrorTitle(), body = it.authErrorBody(), danger = true)
        }
    }
}
