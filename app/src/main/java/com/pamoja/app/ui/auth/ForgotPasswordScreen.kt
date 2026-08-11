package com.pamoja.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    prefilledEmail: String,
    onBack: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()

    // Carried forward so nothing is retyped.
    var email by remember { mutableStateOf(prefilledEmail) }

    // Hoisted: validate runs on focus change, outside composable scope.
    val emailRequired = stringResource(R.string.email_required)

    val sentTo = uiState.resetEmailSentTo

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.x6)
        ) {
            Spacer(modifier = Modifier.height(Spacing.x2))

            AuthBackButton(
                onBack = {
                    viewModel.clearResetConfirmation()
                    onBack()
                }
            )

            Spacer(modifier = Modifier.height(Spacing.x6))

            if (sentTo == null) {
                Text(
                    text = stringResource(R.string.reset_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                Text(
                    text = stringResource(R.string.reset_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x8))

                PamojaTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.email_label),
                    placeholder = stringResource(R.string.email_placeholder),
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    validate = { input ->
                        if (input.isBlank()) emailRequired else null
                    },
                )

                uiState.error?.let { message ->
                    Spacer(modifier = Modifier.height(Spacing.x5))
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = message.authErrorTitle(),
                        body = message.authErrorBody(),
                        tone = NoticeTone.Danger,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x8))

                Button(
                    onClick = { viewModel.sendPasswordReset(email) },
                    enabled = email.isNotBlank() && uiState.busyWith == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(PamojaRadii.md),
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
                        Text(text = stringResource(R.string.reset_send), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.reset_sent_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                Text(
                    text = stringResource(R.string.reset_sent_body, sentTo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x6))

                // Specific about what to do when the mail does not arrive, since
                // that is the only situation in which this screen is read twice.
                PamojaNotice(
                    icon = PamojaIcons.Info,
                    title = stringResource(R.string.reset_nothing_title),
                    body = stringResource(R.string.reset_nothing_body),
                    tone = NoticeTone.Info,
                )

                Spacer(modifier = Modifier.height(Spacing.x6))

                Button(
                    onClick = { viewModel.sendPasswordReset(sentTo) },
                    enabled = uiState.busyWith == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(PamojaRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.textOnBrand,
                        disabledContainerColor = colors.accentPrimarySubtle,
                    ),
                ) {
                    Text(text = stringResource(R.string.reset_resend), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(Spacing.x2))

                TextButton(
                    onClick = {
                        viewModel.clearResetConfirmation()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.reset_back_to_signin),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x10))
        }
    }
}
