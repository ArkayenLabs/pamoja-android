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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun EmailAuthScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onForgotPassword: (String) -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()

    // Sign up and sign in share one layout, so switching between them costs no
    // re-learning. Only the copy and the submit action change.
    var isSignUp by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isValid = email.isNotBlank() && password.length >= if (isSignUp) 8 else 1

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
                text = if (isSignUp) "Sign up with email" else "Welcome back",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            Text(
                text = if (isSignUp) {
                    "One account keeps your groups and history safe."
                } else {
                    "Sign in to pick up where you left off."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x8))

            PamojaTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                supportingText = if (isSignUp) {
                    "Used to sign in and to reset your password. Never shown to your group."
                } else null,
                validate = { input ->
                    when {
                        input.isBlank() -> "Enter your email address"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(input.trim())
                            .matches() -> "That email address does not look right"

                        else -> null
                    }
                },
            )

            Spacer(modifier = Modifier.height(Spacing.x5))

            PamojaTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = if (isSignUp) "At least 8 characters" else "Your password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                validate = if (isSignUp) {
                    { input ->
                        if (input.length < 8) "Use at least 8 characters" else null
                    }
                } else null,
            )

            if (isSignUp) {
                Spacer(modifier = Modifier.height(Spacing.x4))
                PasswordStrength(password = password)
            }

            if (!isSignUp) {
                Spacer(modifier = Modifier.height(Spacing.x2))
                TextButton(
                    onClick = { onForgotPassword(email) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                    )
                }
            }

            uiState.error?.let { message ->
                Spacer(modifier = Modifier.height(Spacing.x5))
                AuthNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = "That did not work",
                    body = message,
                    tone = NoticeTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x8))

            Button(
                onClick = {
                    if (isSignUp) {
                        viewModel.signUpWithEmail(email, password)
                    } else {
                        viewModel.signInWithEmail(email, password)
                    }
                },
                enabled = isValid && uiState.busyWith == null,
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
                    Text(
                        text = if (isSignUp) "Create account" else "Sign in",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x5))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "New to Pamoja?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        viewModel.clearError()
                    },
                ) {
                    Text(
                        text = if (isSignUp) "Sign in" else "Create one",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x10))
        }
    }
}

/**
 * Four segment strength bar with a plain language requirement line.
 *
 * Deliberately not colour-only: the label states what is missing, so the meter
 * still works for someone who cannot distinguish the segment colours.
 */
@Composable
private fun PasswordStrength(password: String) {
    val colors = LocalPamojaColors.current

    val hasLength = password.length >= 8
    val hasNumberOrSymbol = password.any { !it.isLetterOrDigit() || it.isDigit() }
    val isLong = password.length >= 12

    val score = listOf(password.isNotEmpty(), hasLength, hasNumberOrSymbol, isLong).count { it }

    val (label, tint) = when {
        password.isEmpty() -> "" to colors.borderStrong
        score <= 1 -> "Too short" to colors.statusDanger
        score == 2 -> "Getting there" to colors.statusWarning
        score == 3 -> "Good password" to colors.statusSuccess
        else -> "Strong password" to colors.statusSuccess
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index < score) tint else colors.borderStrong)
                )
            }
        }

        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.x2))
            Text(
                text = when {
                    !hasLength -> "$label. Use at least 8 characters."
                    !hasNumberOrSymbol -> "$label. Add a number or symbol to strengthen it."
                    else -> label
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
