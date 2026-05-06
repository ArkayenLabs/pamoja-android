package com.pamoja.app.ui.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onSignedIn() }
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
            Spacer(modifier = Modifier.height(16.dp))

            // Back
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = PamojaTextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text  = "Welcome back",
                style = MaterialTheme.typography.headlineLarge,
                color = PamojaTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = "Sign in to see your groups and progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            DarkTextField(
                value         = email,
                onValueChange = { email = it },
                label         = "Email",
                placeholder   = "your@email.com",
                keyboardType  = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(12.dp))

            DarkTextField(
                value         = password,
                onValueChange = { password = it },
                label         = "Password",
                placeholder   = "Your password",
                keyboardType  = KeyboardType.Password
            )

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick  = { /* V2 */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text  = "Forgot password?",
                    style = MaterialTheme.typography.labelSmall,
                    color = PamojaIndigo
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick  = { viewModel.signIn(email.trim(), password) },
                enabled  = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
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
                    text  = if (uiState.isLoading) "Signing in…" else "Sign in",
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
