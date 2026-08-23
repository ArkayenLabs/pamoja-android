package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
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
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
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

            PamojaMark()

            Spacer(modifier = Modifier.height(Spacing.x5))

            Text(
                text = stringResource(R.string.auth_landing_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            Text(
                text = stringResource(R.string.auth_landing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x6))

            // A failure keeps every method available. A Google outage should not
            // strand someone who could happily use email.
            uiState.error?.let { message ->
                Spacer(modifier = Modifier.height(Spacing.x4))
                PamojaNotice(
                    icon = PamojaIcons.AlertCircle,
                    title = message.authErrorTitle(),
                    body = message.authErrorBody(),
                    tone = NoticeTone.Danger,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x6))

            AuthMethodButton(
                icon = PamojaIcons.Google,
                label = stringResource(R.string.auth_continue_google),
                loadingLabel = stringResource(R.string.auth_opening_google),
                onClick = { activity?.let(viewModel::signInWithGoogle) },
                isLoading = uiState.busyWith == AuthMethod.Google,
                enabled = uiState.busyWith == null,
                filled = true,
                isGoogleMark = true,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            AuthMethodButton(
                icon = PamojaIcons.Smartphone,
                label = stringResource(R.string.auth_continue_phone),
                loadingLabel = stringResource(R.string.auth_continue_phone),
                onClick = onChoosePhone,
                isLoading = false,
                enabled = uiState.busyWith == null,
                filled = false,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            AuthMethodButton(
                icon = PamojaIcons.Mail,
                label = stringResource(R.string.auth_continue_email),
                loadingLabel = stringResource(R.string.auth_continue_email),
                onClick = onChooseEmail,
                isLoading = false,
                enabled = uiState.busyWith == null,
                filled = false,
            )

            Spacer(modifier = Modifier.height(Spacing.x6))

            AuthLegalLine(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(Spacing.x8))
        }
    }
}
