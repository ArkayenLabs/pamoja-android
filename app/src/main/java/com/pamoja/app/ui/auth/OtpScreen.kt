package com.pamoja.app.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun OtpScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity

    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Submitting the moment the last digit lands saves a tap, and SMS autofill
    // delivers all six at once anyway.
    LaunchedEffect(code) {
        if (code.length == OTP_LENGTH) viewModel.verifyCode(code)
    }

    // A wrong or expired code never wipes what was typed. Clearing the field is
    // the most common and most infuriating failure in OTP design.
    val isVerifying = uiState.busyWith == AuthMethod.Phone

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

            AuthBackButton(onBack = onBack)

            Spacer(modifier = Modifier.height(Spacing.x6))

            Text(
                text = stringResource(R.string.otp_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            // The number is restated in full so a mistyped digit is caught here
            // rather than after a code that could never have arrived.
            Text(
                text = stringResource(R.string.otp_sent_to, uiState.country.dialCode, uiState.phoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x2))

            TextButton(
                onClick = {
                    viewModel.restartPhoneEntry()
                    onBack()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.otp_wrong_number),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            OtpBoxes(
                code = code,
                onCodeChange = { if (!isVerifying) code = it },
                hasError = uiState.otpFailure != null,
                focusRequester = focusRequester,
            )

            Spacer(modifier = Modifier.height(Spacing.x4))

            when (uiState.otpFailure) {
                OtpFailure.WrongCode -> PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = stringResource(R.string.otp_wrong_code_title),
                    body = stringResource(R.string.otp_wrong_code_body),
                    tone = NoticeTone.Danger,
                )

                OtpFailure.Expired -> PamojaNotice(
                    icon = PamojaIcons.Clock,
                    title = stringResource(R.string.otp_expired_title),
                    body = stringResource(R.string.otp_expired_body),
                    tone = NoticeTone.Warning,
                )

                OtpFailure.RateLimited -> PamojaNotice(
                    icon = PamojaIcons.Lock,
                    title = stringResource(R.string.otp_rate_limited_title),
                    body = stringResource(R.string.otp_rate_limited_body),
                    tone = NoticeTone.Danger,
                )

                null -> uiState.error?.let {
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.auth_failed_title),
                        body = it,
                        tone = NoticeTone.Danger,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            // Disabled with a live countdown rather than swapped out, which is
            // the honest version of an inactive control: it says when it works.
            val secondsLeft = uiState.resendSecondsLeft
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.otp_no_code),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.width(Spacing.x2))
                if (secondsLeft > 0) {
                    Icon(
                        painter = painterResource(PamojaIcons.Clock),
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.x1))
                    Text(
                        text = stringResource(R.string.otp_resend_in, secondsLeft),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                } else {
                    TextButton(
                        onClick = {
                            code = ""
                            viewModel.sendCode(activity)
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.otp_send_new),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x8))

            Button(
                onClick = { viewModel.verifyCode(code) },
                enabled = code.length == OTP_LENGTH && !isVerifying,
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
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(text = stringResource(R.string.otp_verify), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x4))

            Text(
                text = stringResource(R.string.otp_autofill_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )

            Spacer(modifier = Modifier.height(Spacing.x10))
        }
    }
}

