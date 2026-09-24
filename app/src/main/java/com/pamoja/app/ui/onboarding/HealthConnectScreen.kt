package com.pamoja.app.ui.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.ui.components.PamojaMark
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class PermState { UNKNOWN, GRANTED, DENIED, HC_UNAVAILABLE }

/**
 * A contextual permission primer shown only after a person creates or joins a
 * group. It says the one fact needed to make an informed choice, then hands the
 * decision to Android's native Health Connect permission UI.
 */
@Composable
fun HealthConnectScreen(
    onConnected: () -> Unit,
    onSkip: () -> Unit,
    viewModel: HealthConnectViewModel = hiltViewModel(),
) {
    val healthConnectReader = viewModel.healthConnectReader
    val userPreferences = viewModel.userPreferences
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var permState by remember { mutableStateOf(PermState.UNKNOWN) }

    LaunchedEffect(Unit) {
        viewModel.onScreenViewed()
        permState = when {
            !healthConnectReader.isAvailable() -> PermState.HC_UNAVAILABLE
            healthConnectReader.hasPermission() -> PermState.GRANTED
            else -> PermState.UNKNOWN
        }

        // Someone who connected previously should not see education they have
        // already acted on just because they created or joined another group.
        if (permState == PermState.GRANTED) {
            userPreferences.setHealthConnectGranted(true)
            onConnected()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        scope.launch {
            val hasPermission = granted.containsAll(HealthConnectReader.REQUIRED_PERMISSIONS)
            userPreferences.setHealthConnectGranted(hasPermission)
            if (hasPermission) {
                permState = PermState.GRANTED
                val userId = userPreferences.userId.first().orEmpty()
                viewModel.onPermissionGranted(userId)
                onConnected()
            } else {
                permState = PermState.DENIED
                viewModel.onPermissionDenied()
            }
        }
    }

    val openHealthSettings = {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            }
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }
        context.startActivity(intent)
    }

    HealthConnectContent(
        permState = permState,
        onPrimary = {
            when (permState) {
                PermState.HC_UNAVAILABLE -> {
                    viewModel.onSkipped()
                    onSkip()
                }
                PermState.DENIED -> openHealthSettings()
                PermState.GRANTED -> onConnected()
                PermState.UNKNOWN -> {
                    viewModel.onPermissionRequested()
                    permissionLauncher.launch(HealthConnectReader.REQUIRED_PERMISSIONS)
                }
            }
        },
        onSkip = {
            viewModel.onSkipped()
            onSkip()
        },
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun HealthConnectContent(
    permState: PermState,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val subtitle = when (permState) {
        PermState.HC_UNAVAILABLE -> stringResource(R.string.hc_body_unavailable)
        PermState.DENIED -> stringResource(R.string.hc_body_denied)
        PermState.GRANTED -> stringResource(R.string.hc_body_granted)
        PermState.UNKNOWN -> stringResource(R.string.hc_body_default)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.x5),
    ) {
        Spacer(Modifier.height(Spacing.x5))
        PamojaMark()

        Spacer(Modifier.height(Spacing.x8))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(PamojaRadii.xl))
                .background(colors.accentPrimarySubtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.Footprints),
                contentDescription = stringResource(R.string.hc_icon_desc),
                tint = colors.accentPrimary,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(Spacing.x6))
        Text(
            text = stringResource(R.string.hc_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.x2))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (permState == PermState.DENIED || permState == PermState.HC_UNAVAILABLE) {
                colors.statusDanger
            } else {
                colors.textSecondary
            },
        )

        Spacer(Modifier.height(Spacing.x6))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PamojaRadii.lg))
                .background(colors.accentTealSubtle)
                .border(
                    width = 1.dp,
                    color = colors.borderSubtle,
                    shape = RoundedCornerShape(PamojaRadii.lg),
                )
                .padding(Spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(PamojaIcons.ShieldCheck),
                contentDescription = null,
                tint = colors.accentTeal,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(Spacing.x3))
            Text(
                text = stringResource(R.string.hc_steps_only),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(Spacing.x7))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (permState == PermState.GRANTED) {
                    colors.statusSuccess
                } else {
                    colors.accentPrimary
                },
                contentColor = colors.textOnBrand,
            ),
        ) {
            val icon = when (permState) {
                PermState.DENIED -> PamojaIcons.Settings
                PermState.GRANTED -> PamojaIcons.Check
                else -> PamojaIcons.Footprints
            }
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(Spacing.x2))
            Text(
                text = stringResource(
                    when (permState) {
                        PermState.HC_UNAVAILABLE -> R.string.hc_continue_without
                        PermState.DENIED -> R.string.hc_open_settings
                        PermState.GRANTED -> R.string.hc_connected
                        PermState.UNKNOWN -> R.string.hc_connect
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (permState != PermState.GRANTED && permState != PermState.HC_UNAVAILABLE) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.hc_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(Spacing.x6))
    }
}
