package com.pamoja.app.ui.auth

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The sign-in gate. Nothing in the app is reachable before one of these three
 * methods succeeds, so this screen answers "why do I need an account" before it
 * asks for anything.
 */
@Composable
fun AuthLandingScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onChoosePhone: () -> Unit,
    onChooseEmail: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity

    val topTint = if (colors.isDark) colors.surfaceSunken else colors.accentPrimarySubtle
    val background = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to topTint,
            0.45f to colors.surfaceApp,
            1.0f to colors.surfaceApp,
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x6)
        ) {
            Spacer(modifier = Modifier.height(Spacing.x2))

            AuthBackButton(onBack = onBack)

            Spacer(modifier = Modifier.height(Spacing.x8))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(PamojaRadii.lg))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.accentPrimary, colors.accentPrimaryPress)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Footprints),
                    contentDescription = null,
                    tint = colors.textOnBrand,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            Text(
                text = "Keep your groups safe",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            Text(
                text = "Sign in so your groups and step history follow you to a new phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x6))

            // Reassurance sits above the buttons, not buried under them, because
            // it is the answer to the question the buttons provoke.
            PamojaNotice(
                icon = PamojaIcons.Shield,
                title = "We only store what signs you in",
                body = "No contacts, no social graph, nothing posted anywhere.",
            )

            // A failure keeps every method available. A Google outage should not
            // strand someone who could happily use email.
            uiState.error?.let { message ->
                Spacer(modifier = Modifier.height(Spacing.x4))
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = "That did not work",
                    body = message,
                    tone = NoticeTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            AuthMethodButton(
                icon = PamojaIcons.Google,
                label = "Continue with Google",
                loadingLabel = "Opening Google…",
                onClick = { viewModel.signInWithGoogle(activity) },
                isLoading = uiState.busyWith == AuthMethod.Google,
                enabled = uiState.busyWith == null,
                filled = true,
                isGoogleMark = true,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            AuthMethodButton(
                icon = PamojaIcons.Smartphone,
                label = "Continue with phone",
                loadingLabel = "Continue with phone",
                onClick = onChoosePhone,
                isLoading = false,
                enabled = uiState.busyWith == null,
                filled = false,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            AuthMethodButton(
                icon = PamojaIcons.Mail,
                label = "Continue with email",
                loadingLabel = "Continue with email",
                onClick = onChooseEmail,
                isLoading = false,
                enabled = uiState.busyWith == null,
                filled = false,
            )

            Spacer(modifier = Modifier.height(Spacing.x6))

            Text(
                text = "By continuing you agree to our Terms and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.x8))
        }
    }
}
