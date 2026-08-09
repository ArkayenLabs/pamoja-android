package com.pamoja.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Returning-user sign-in screen.
 *
 * Because Pamoja uses anonymous Firebase auth, there are no credentials to enter.
 * This screen simply restores the existing anonymous session (or creates a new one
 * if the app was freshly reinstalled) and navigates the user to Home.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.clearSuccess()
            onSignedIn()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val topTint = if (colors.isDark) Color(0xFF1C1A3A) else Color(0xFFECEAFB)
    val backgroundGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to topTint,
            0.45f to colors.surfaceApp,
            1.0f to colors.surfaceApp
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.x4))

            // Back
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter            = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = "Back",
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x12))

            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(PamojaRadii.lg))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(colors.accentPrimary, colors.accentPrimaryPress)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter            = painterResource(PamojaIcons.Footprints),
                    contentDescription = null,
                    tint               = colors.textOnBrand,
                    modifier           = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x7))

            Text(
                text      = "Welcome back",
                style     = MaterialTheme.typography.headlineLarge,
                color     = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            Text(
                text      = "Tap Continue to restore your session and pick up right where you left off.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        // Bottom CTA pinned to bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.x6, vertical = Spacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick  = { viewModel.signIn() },
                enabled  = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = colors.accentPrimary,
                    contentColor           = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color       = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text  = "Continue",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}
