package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

@Composable
fun OtpScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    OtpContent(
        dialCode = uiState.country.dialCode,
        number = uiState.phoneNumber,
        verifying = uiState.busyWith == AuthMethod.Phone,
        failure = uiState.otpFailure,
        error = uiState.error,
        resendSecondsLeft = uiState.resendSecondsLeft,
        onVerify = viewModel::verifyCode,
        onResend = { activity?.let(viewModel::sendCode) },
        onWrongNumber = {
            viewModel.restartPhoneEntry()
            onBack()
        },
        onBack = onBack,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun OtpContent(
    dialCode: String,
    number: String,
    verifying: Boolean,
    failure: OtpFailure?,
    error: AppError?,
    resendSecondsLeft: Int,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onWrongNumber: () -> Unit,
    onBack: () -> Unit,
) = KitInAppTheme {
    var code by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Submitting the moment the last digit lands saves a tap, and SMS autofill
    // delivers all six at once anyway.
    LaunchedEffect(code) {
        if (code.length == OTP_LENGTH) onVerify(code)
    }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.otp_verify),
                onClick = { onVerify(code) },
                enabled = code.length == OTP_LENGTH && !verifying,
                loading = verifying,
            )
            // Disabled with a live countdown rather than swapped out, which is
            // the honest version of an inactive control: it says when it works.
            QuietButton(
                text = if (resendSecondsLeft > 0) {
                    "${stringResource(R.string.otp_no_code)} ${stringResource(R.string.otp_resend_in, resendSecondsLeft)}"
                } else {
                    "${stringResource(R.string.otp_no_code)} ${stringResource(R.string.otp_send_new)}"
                },
                onClick = {
                    code = ""
                    onResend()
                },
                enabled = resendSecondsLeft <= 0 && !verifying,
            )
        },
    ) {
        Text(stringResource(R.string.otp_title), style = Kit.type.headline)
        // The number is restated in full so a mistyped digit is caught here
        // rather than after a code that could never have arrived.
        Text(stringResource(R.string.otp_sent_to, dialCode, number), style = Kit.type.body, color = Kit.colors.muted)
        QuietButton(text = stringResource(R.string.otp_wrong_number), onClick = onWrongNumber)

        // A wrong or expired code never wipes what was typed. Clearing the
        // field is the most common and most infuriating failure in OTP design.
        OtpBoxes(
            code = code,
            onCodeChange = { if (!verifying) code = it },
            hasError = failure != null,
            focusRequester = focusRequester,
        )

        when (failure) {
            OtpFailure.WrongCode -> KitNotice(
                title = stringResource(R.string.otp_wrong_code_title),
                body = stringResource(R.string.otp_wrong_code_body),
                danger = true,
            )
            OtpFailure.Expired -> KitNotice(
                title = stringResource(R.string.otp_expired_title),
                body = stringResource(R.string.otp_expired_body),
            )
            OtpFailure.RateLimited -> KitNotice(
                title = stringResource(R.string.otp_rate_limited_title),
                body = stringResource(R.string.otp_rate_limited_body),
                danger = true,
            )
            null -> error?.let { KitNotice(title = it.authErrorTitle(), body = it.authErrorBody(), danger = true) }
        }
    }
}
