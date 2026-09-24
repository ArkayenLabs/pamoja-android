package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
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
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

/**
 * The returning-user entry point.
 *
 * Email and password are the primary path on the page. Google and phone are
 * alternatives, while creating an account is a separate, explicit decision.
 */
@Composable
fun AuthLandingScreen(
    viewModel: AuthViewModel,
    onChoosePhone: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(Unit) { viewModel.clearError() }

    AuthEntryContent(
        busyWith = uiState.busyWith,
        error = uiState.error,
        onEmailSignIn = viewModel::signInWithEmail,
        onGoogle = { activity?.let(viewModel::signInWithGoogle) },
        onPhone = onChoosePhone,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
        onClearError = viewModel::clearError,
        onBack = onBack,
    )
}

/** Stateless action boundary used by visual previews and interaction tests. */
@Composable
internal fun AuthEntryContent(
    busyWith: AuthMethod?,
    error: AppError?,
    onEmailSignIn: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onPhone: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onClearError: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalPamojaColors.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val emailRequired = stringResource(R.string.email_required)
    val emailInvalid = stringResource(R.string.email_invalid)
    val emailErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> emailRequired
            !android.util.Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches() -> emailInvalid
            else -> null
        }
    }
    val canSignIn = emailErrorFor(email) == null && password.isNotBlank() && busyWith == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.x6),
    ) {
        Spacer(Modifier.height(Spacing.x2))
        onBack?.let {
            AuthBackButton(onBack = it)
            Spacer(Modifier.height(Spacing.x5))
        }

        PamojaMark(size = 52.dp)
        Spacer(Modifier.height(Spacing.x4))
        Text(
            text = stringResource(R.string.auth_landing_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.x2))
        Text(
            text = stringResource(R.string.auth_landing_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(Spacing.x6))
        PamojaTextField(
            value = email,
            onValueChange = {
                email = it
                onClearError()
            },
            label = stringResource(R.string.email_label),
            placeholder = stringResource(R.string.email_placeholder),
            keyboardType = KeyboardType.Email,
            validate = emailErrorFor,
        )

        Spacer(Modifier.height(Spacing.x4))
        PamojaTextField(
            value = password,
            onValueChange = {
                password = it
                onClearError()
            },
            label = stringResource(R.string.password_label),
            placeholder = stringResource(R.string.password_placeholder_existing),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = {
                if (canSignIn) onEmailSignIn(email.trim(), password)
            },
            isPassword = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onForgotPassword(email.trim()) }) {
                Text(
                    text = stringResource(R.string.email_forgot),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                )
            }
        }

        error?.let { message ->
            PamojaNotice(
                icon = PamojaIcons.AlertCircle,
                title = message.authErrorTitle(),
                body = message.authErrorBody(),
                tone = NoticeTone.Danger,
            )
            Spacer(Modifier.height(Spacing.x4))
        }

        Button(
            onClick = { onEmailSignIn(email.trim(), password) },
            enabled = canSignIn,
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
            if (busyWith == AuthMethod.Email) {
                CircularProgressIndicator(
                    color = colors.textOnBrand,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.common_sign_in),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(Spacing.x3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.auth_new_here),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            TextButton(onClick = onCreateAccount) {
                Text(
                    text = stringResource(R.string.auth_create_account),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                )
            }
        }

        Spacer(Modifier.height(Spacing.x3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.borderDefault)
            Text(
                text = stringResource(R.string.auth_or_continue),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.borderDefault)
        }

        Spacer(Modifier.height(Spacing.x4))
        AuthMethodButton(
            icon = PamojaIcons.Google,
            label = stringResource(R.string.auth_continue_google),
            loadingLabel = stringResource(R.string.auth_opening_google),
            onClick = onGoogle,
            isLoading = busyWith == AuthMethod.Google,
            enabled = busyWith == null,
            filled = false,
            isGoogleMark = true,
        )

        Spacer(Modifier.height(Spacing.x3))
        AuthMethodButton(
            icon = PamojaIcons.Smartphone,
            label = stringResource(R.string.auth_continue_phone),
            loadingLabel = stringResource(R.string.auth_continue_phone),
            onClick = onPhone,
            isLoading = false,
            enabled = busyWith == null,
            filled = false,
        )

        Spacer(Modifier.height(Spacing.x5))
        AuthLegalLine(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.x6))
    }
}
