package com.pamoja.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenSubtle
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaWhite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Permission states — drives UI
private enum class PermState { UNKNOWN, GRANTED, DENIED, HC_UNAVAILABLE }

@Composable
fun HealthConnectScreen(
    onConnected: () -> Unit,
    onSkip: () -> Unit,
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val healthConnectReader = viewModel.healthConnectReader
    val userPreferences     = viewModel.userPreferences
    val scope               = rememberCoroutineScope()
    val context             = LocalContext.current
    val snackbarHostState   = remember { SnackbarHostState() }

    // Check current permission state on entry
    var permState by remember { mutableStateOf(PermState.UNKNOWN) }

    LaunchedEffect(Unit) {
        viewModel.onScreenViewed()
        permState = when {
            !healthConnectReader.isAvailable() -> PermState.HC_UNAVAILABLE
            healthConnectReader.hasPermission() -> PermState.GRANTED
            else -> PermState.UNKNOWN
        }
    }

    // Health Connect permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        scope.launch {
            val hasPermission = granted.containsAll(HealthConnectReader.REQUIRED_PERMISSIONS)
            if (hasPermission) {
                userPreferences.setHealthConnectGranted(true)
                permState = PermState.GRANTED
                val userId = userPreferences.userId.first() ?: ""
                viewModel.onPermissionGranted(userId)
                onConnected()
            } else {
                userPreferences.setHealthConnectGranted(false)
                permState = PermState.DENIED
                viewModel.onPermissionDenied()
            }
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── Top content ─────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Spacer(modifier = Modifier.height(32.dp))

                OnboardingProgressBar(currentStep = 3, totalSteps = 3)

                Spacer(modifier = Modifier.height(48.dp))

                // Step ring illustration
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(PamojaIndigoSubtle)
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(PamojaIndigo, PamojaIndigoDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = "Step tracking",
                            tint               = PamojaWhite,
                            modifier           = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text      = "Track your steps",
                    style     = MaterialTheme.typography.headlineLarge,
                    color     = PamojaTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                val subtitle = when (permState) {
                    PermState.HC_UNAVAILABLE ->
                        "Health Connect is not available on this device. Step tracking won't be available."
                    PermState.DENIED ->
                        "Without this permission, your steps will show as 0 to your group. You can enable it later in Health Connect settings."
                    PermState.GRANTED ->
                        "You're all set! Pamoja will sync your steps via Health Connect — no battery drain, no background tracking."
                    else ->
                        "Pamoja uses Health Connect to count your steps. Your data stays private and is only shared with your group members."
                }

                Text(
                    text      = subtitle,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = if (permState == PermState.DENIED) MaterialTheme.colorScheme.error
                                else PamojaTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // What Pamoja accesses card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PamojaSurface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text  = "WHAT PAMOJA ACCESSES",
                        style = MaterialTheme.typography.labelSmall,
                        color = PamojaTextSecondary
                    )
                    PermissionRow(
                        icon      = Icons.AutoMirrored.Filled.DirectionsWalk,
                        iconColor = PamojaIndigo,
                        iconBg    = PamojaIndigoSubtle,
                        title     = "Daily step count",
                        subtitle  = "Read from Health Connect — battery friendly"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PamojaBorder))
                    PermissionRow(
                        icon      = Icons.Default.Shield,
                        iconColor = PamojaGreen,
                        iconBg    = PamojaGreenSubtle,
                        title     = "Nothing else",
                        subtitle  = "No location, heart rate or sleep data"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PamojaBorder))
                    PermissionRow(
                        icon      = Icons.Default.Lock,
                        iconColor = PamojaGreen,
                        iconBg    = PamojaGreenSubtle,
                        title     = "Private by default",
                        subtitle  = "Only your group members see your steps"
                    )
                }

                if (permState == PermState.HC_UNAVAILABLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text      = "Health Connect is not available on this device.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Bottom CTAs ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (permState) {

                    // ── HC not on this device → skip only ────────────────
                    PermState.HC_UNAVAILABLE -> {
                        Button(
                            onClick  = {
                                viewModel.onSkipped()
                                onSkip()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = PamojaIndigo,
                                contentColor   = PamojaWhite
                            )
                        ) {
                            Text(
                                text  = "Continue without steps",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // ── Denied → offer Settings shortcut + skip ──────────
                    PermState.DENIED -> {
                        Button(
                            onClick = {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= 34) {
                                Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                                    putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                                }
                            } else {
                                Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                            }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = PamojaIndigo,
                                contentColor   = PamojaWhite
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text  = "Open Settings",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        TextButton(onClick = {
                            viewModel.onSkipped()
                            onSkip()
                        }) {
                            Text(
                                text  = "Skip for now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PamojaTextSecondary
                            )
                        }
                    }

                    // ── Granted → proceed ────────────────────────────────
                    PermState.GRANTED -> {
                        Button(
                            onClick  = onConnected,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = PamojaGreen,
                                contentColor   = PamojaWhite
                            )
                        ) {
                            Text(
                                text  = "Steps connected ✓",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // ── Unknown → request permission ─────────────────────
                    else -> {
                        Button(
                            onClick = {
                                viewModel.onPermissionRequested()
                                permissionLauncher.launch(HealthConnectReader.REQUIRED_PERMISSIONS)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = PamojaIndigo,
                                contentColor   = PamojaWhite
                            )
                        ) {
                            Text(
                                text  = "Connect Health Connect",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        TextButton(onClick = {
                            viewModel.onSkipped()
                            onSkip()
                        }) {
                            Text(
                                text  = "Skip for now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PamojaTextSecondary
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─── Permission explanation row ───────────────────────────────────────────────
@Composable
private fun PermissionRow(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconColor,
                modifier           = Modifier.size(20.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium,
                color = PamojaTextPrimary
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PamojaTextSecondary
            )
        }
    }
}