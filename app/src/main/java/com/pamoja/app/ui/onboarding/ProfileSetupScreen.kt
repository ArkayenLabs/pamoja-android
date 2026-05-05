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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaSurfaceVariant
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun ProfileSetupScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name   by remember { mutableStateOf("") }
    var age    by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onContinue()
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
            .background(PamojaBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── iOS pill progress bar ────────────────────────────────────
            OnboardingProgressBar(currentStep = 2, totalSteps = 3)

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text  = "Set up your profile",
                style = MaterialTheme.typography.headlineLarge,
                color = PamojaTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text  = "Your group sees your name. Everything else is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Avatar placeholder ───────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PamojaSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Add photo",
                        tint = PamojaTextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Name field (required) ────────────────────────────────────
            DarkTextField(
                value       = name,
                onValueChange = { name = it },
                label       = "Name",
                placeholder = "What should we call you?"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Optional stats row ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DarkTextField(
                    value       = age,
                    onValueChange = { age = it },
                    label       = "Age",
                    placeholder = "—",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
                DarkTextField(
                    value       = height,
                    onValueChange = { height = it },
                    label       = "Height cm",
                    placeholder = "—",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
                DarkTextField(
                    value       = weight,
                    onValueChange = { weight = it },
                    label       = "Weight kg",
                    placeholder = "—",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text  = "Age, height and weight are optional and private.",
                style = MaterialTheme.typography.bodySmall,
                color = PamojaTextTertiary
            )

            Spacer(modifier = Modifier.height(36.dp))

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
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = PamojaIndigo,
                    contentColor           = PamojaWhite,
                    disabledContainerColor = PamojaIndigoSubtle,
                    disabledContentColor   = PamojaTextTertiary
                )
            ) {
                Text(
                    text  = if (uiState.isLoading) "Creating account…" else "Continue",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── iOS-style pill progress bar ─────────────────────────────────────────────
// Active steps are filled indigo; upcoming are dim surface. More elegant than dots.
@Composable
fun OnboardingProgressBar(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(totalSteps) { index ->
            val isActive = index < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) PamojaIndigo else PamojaSurfaceVariant
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

// ─── Dark themed text field ───────────────────────────────────────────────────
// Minimal, iOS-ish: no colored container glow, just a clean surface with
// a subtle border that highlights to indigo on focus.
@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = PamojaTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value    = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text  = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PamojaTextTertiary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PamojaTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = PamojaIndigo,
                unfocusedBorderColor    = PamojaBorder,
                focusedContainerColor   = PamojaSurface,
                unfocusedContainerColor = PamojaSurface,
                cursorColor             = PamojaIndigo,
                focusedLabelColor       = PamojaIndigo,
                unfocusedLabelColor     = PamojaTextSecondary
            )
        )
    }
}

// Legacy alias — SignInScreen references PamojaTextField
@Composable
fun PamojaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) = DarkTextField(value, onValueChange, label, placeholder, modifier, keyboardType)