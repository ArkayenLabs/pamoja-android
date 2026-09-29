package com.pamoja.app.ui.auth

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    prefilledEmail: String,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Shared ViewModel: a failure from an earlier auth screen would otherwise
    // still be showing here, unrelated to resetting a password.
    LaunchedEffect(Unit) { viewModel.clearError() }

    ForgotPasswordContent(
        prefilledEmail = prefilledEmail,
        sentTo = uiState.resetEmailSentTo,
        busy = uiState.busyWith == AuthMethod.Email,
        blocked = uiState.busyWith != null,
        error = uiState.error,
        onSend = viewModel::sendPasswordReset,
        onBack = {
            viewModel.clearResetConfirmation()
            onBack()
        },
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun ForgotPasswordContent(
    prefilledEmail: String,
    sentTo: String?,
    busy: Boolean,
    blocked: Boolean,
    error: AppError?,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) = KitInAppTheme {
    // Carried forward from sign-in so nothing is retyped.
    var email by rememberSaveable { mutableStateOf(prefilledEmail) }
    val send = { if (email.isNotBlank() && !blocked) onSend(email) }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            if (sentTo == null) {
                PrimaryButton(
                    text = stringResource(R.string.reset_send),
                    onClick = send,
                    enabled = email.isNotBlank() && !blocked,
                    loading = busy,
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.reset_back_to_signin),
                    onClick = onBack,
                )
                QuietButton(
                    text = stringResource(R.string.reset_resend),
                    onClick = { onSend(sentTo) },
                    enabled = !blocked,
                )
            }
        },
    ) {
        if (sentTo == null) {
            Text(stringResource(R.string.reset_title), style = Kit.type.headline)
            Text(stringResource(R.string.reset_subtitle), style = Kit.type.body, color = Kit.colors.muted)
            KitTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email_label),
                placeholder = stringResource(R.string.email_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
            )
            error?.let {
                KitNotice(title = it.authErrorTitle(), body = it.authErrorBody(), danger = true)
            }
        } else {
            Text(stringResource(R.string.reset_sent_title), style = Kit.type.headline)
            Text(stringResource(R.string.reset_sent_body, sentTo), style = Kit.type.body, color = Kit.colors.muted)
            // Specific about what to do when the mail does not arrive, since
            // that is the only situation in which this screen is read twice.
            KitNotice(
                title = stringResource(R.string.reset_nothing_title),
                body = stringResource(R.string.reset_nothing_body),
            )
        }
    }
}
