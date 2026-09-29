package com.pamoja.app.ui.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.theme.PamojaIcons
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

/**
 * Stateless rendering boundary used by visual and interaction tests.
 *
 * One reason, one promise (steps only), one button. The native Health Connect
 * sheet does the asking.
 */
@Composable
internal fun HealthConnectContent(
    permState: PermState,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val body = when (permState) {
        PermState.HC_UNAVAILABLE -> stringResource(R.string.hc_body_unavailable)
        PermState.DENIED -> stringResource(R.string.hc_body_denied)
        PermState.GRANTED -> stringResource(R.string.hc_body_granted)
        PermState.UNKNOWN -> stringResource(R.string.hc_body_default)
    }
    val problem = permState == PermState.DENIED || permState == PermState.HC_UNAVAILABLE

    KitScreen(
        bottom = {
            PrimaryButton(
                text = stringResource(
                    when (permState) {
                        PermState.HC_UNAVAILABLE -> R.string.hc_continue_without
                        PermState.DENIED -> R.string.hc_open_settings
                        PermState.GRANTED -> R.string.hc_connected
                        PermState.UNKNOWN -> R.string.hc_connect
                    },
                ),
                icon = when (permState) {
                    PermState.DENIED -> PamojaIcons.Settings
                    PermState.GRANTED -> PamojaIcons.Check
                    else -> PamojaIcons.Footprints
                },
                onClick = onPrimary,
            )
            if (permState != PermState.GRANTED && permState != PermState.HC_UNAVAILABLE) {
                QuietButton(stringResource(R.string.hc_skip), onClick = onSkip)
            }
        },
    ) {
        KitCard(tone = CardTone.Hero, padding = 20.dp) {
            Icon(
                painter = painterResource(PamojaIcons.Footprints),
                contentDescription = stringResource(R.string.hc_icon_desc),
                modifier = Modifier.size(40.dp),
            )
        }
        Text(stringResource(R.string.hc_title), style = Kit.type.headline)
        Text(body, style = Kit.type.body, color = if (problem) Kit.colors.danger else Kit.colors.muted)
        KitCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.ShieldCheck),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(stringResource(R.string.hc_steps_only), style = Kit.type.body)
            }
        }
    }
}
