package com.pamoja.app.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun ProfileSetupScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name   by remember { mutableStateOf("") }
    var age    by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.clearSuccess()
            onContinue()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onProfileSetupStarted()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.x6)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(Spacing.x8))

            // ── Pill progress bar ────────────────────────────────────────
            OnboardingProgressBar(currentStep = 2, totalSteps = 3)

            Spacer(modifier = Modifier.height(Spacing.x8))

            Text(
                text  = "Set up your profile",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(Spacing.x2))

            Text(
                text  = "Your group sees your name. Everything else is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(Spacing.x8))

            // ── Avatar placeholder ───────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceSunken),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.Camera),
                        contentDescription = "Add photo",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x7))

            // ── Name field (required) ────────────────────────────────────
            DarkTextField(
                value       = name,
                onValueChange = { name = it },
                label       = "Name",
                placeholder = "What should we call you?"
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            // ── Optional stats row ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
            ) {
                DarkTextField(
                    value       = age,
                    onValueChange = { age = it },
                    label       = "Age",
                    placeholder = "-",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
                DarkTextField(
                    value       = height,
                    onValueChange = { height = it },
                    label       = "Height cm",
                    placeholder = "-",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
                DarkTextField(
                    value       = weight,
                    onValueChange = { weight = it },
                    label       = "Weight kg",
                    placeholder = "-",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x2))

            Text(
                text  = "Age, height and weight are optional and private.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )

            Spacer(modifier = Modifier.height(Spacing.x8))

            Button(
                onClick = {
                    viewModel.signUpAndCreateProfile(
                        name   = name.trim(),
                        age    = age.toIntOrNull(),
                        height = height.toFloatOrNull(),
                        weight = weight.toFloatOrNull()
                    )
                },
                enabled  = name.isNotBlank() && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = colors.accentPrimary,
                    contentColor           = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor   = colors.textTertiary
                )
            ) {
                Text(
                    text  = if (uiState.isLoading) "Creating account…" else "Continue",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x10))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── Pill progress bar ───────────────────────────────────────────────────────
@Composable
fun OnboardingProgressBar(currentStep: Int, totalSteps: Int) {
    val colors = LocalPamojaColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        repeat(totalSteps) { index ->
            val isActive = index < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) colors.accentPrimary else colors.borderStrong
                    )
            )
        }
    }
}

// ─── ProgressDots (legacy alias kept for HealthConnectScreen) ─────────────────
@Composable
fun ProgressDots(current: Int, total: Int) {
    OnboardingProgressBar(currentStep = current, totalSteps = total)
}

// ─── Themed text field ─────────────────────────────────────────────────────────
// Clean surface with a subtle border that highlights to the brand hue on focus.
@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = LocalPamojaColors.current
    Column(modifier = modifier) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(Spacing.x2))
        OutlinedTextField(
            value    = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text  = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(PamojaRadii.sm),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = colors.accentPrimary,
                unfocusedBorderColor    = colors.borderDefault,
                focusedContainerColor   = colors.surfaceInput,
                unfocusedContainerColor = colors.surfaceInput,
                cursorColor             = colors.accentPrimary,
                focusedLabelColor       = colors.accentPrimary,
                unfocusedLabelColor     = colors.textSecondary
            )
        )
    }
}
