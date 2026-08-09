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
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

private const val OTP_LENGTH = 6

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
                text = "Enter your code",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            // The number is restated in full so a mistyped digit is caught here
            // rather than after a code that could never have arrived.
            Text(
                text = "Sent to ${uiState.country.dialCode} ${uiState.phoneNumber}",
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
                    text = "Wrong number? Go back",
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
                OtpFailure.WrongCode -> AuthNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = "That code is not right",
                    body = "Check the digits and try again.",
                    tone = NoticeTone.Danger,
                )

                OtpFailure.Expired -> AuthNotice(
                    icon = PamojaIcons.Clock,
                    title = "This code expired",
                    body = "Codes last a few minutes. Send a fresh one below.",
                    tone = NoticeTone.Warning,
                )

                OtpFailure.RateLimited -> AuthNotice(
                    icon = PamojaIcons.Lock,
                    title = "Too many attempts",
                    body = "For safety we have paused code checks on this number. " +
                        "Try again in a few minutes, or use email instead.",
                    tone = NoticeTone.Danger,
                )

                null -> uiState.error?.let {
                    AuthNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = "That did not work",
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
                    text = "Did not get it?",
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
                        text = "Resend in 0:${secondsLeft.toString().padStart(2, '0')}",
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
                            text = "Send a new code",
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
                    Text(text = "Verify", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x4))

            Text(
                text = "If your phone offers to fill the code, tapping it fills every box at once.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )

            Spacer(modifier = Modifier.height(Spacing.x10))
        }
    }
}

/**
 * Six boxes driven by one hidden field.
 *
 * A field per box fights SMS autofill, which delivers the whole code at once,
 * and makes backspace behaviour strange. One field keeps autofill working and
 * the boxes become presentation.
 */
@Composable
private fun OtpBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    hasError: Boolean,
    focusRequester: FocusRequester,
) {
    val colors = LocalPamojaColors.current

    Box {
        BasicTextField(
            value = code,
            onValueChange = { new ->
                val digits = new.filter { it.isDigit() }.take(OTP_LENGTH)
                onCodeChange(digits)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            // Transparent so the real boxes below are what the user sees, while
            // this field keeps focus, the keyboard and autofill.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                ) {
                    repeat(OTP_LENGTH) { index ->
                        val char = code.getOrNull(index)
                        val isCursor = index == code.length

                        val borderColor = when {
                            hasError -> colors.statusDanger
                            isCursor -> colors.accentPrimary
                            char != null -> colors.borderStrong
                            else -> colors.borderDefault
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .background(colors.surfaceInput, RoundedCornerShape(PamojaRadii.sm))
                                .border(
                                    width = if (isCursor || hasError) 2.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(PamojaRadii.sm),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char?.toString() ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (hasError) colors.statusDanger else colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
        )
    }
}
