package com.pamoja.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.data.local.health.StepCounterService
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
import kotlinx.coroutines.launch

@Composable
fun HealthConnectScreen(
    onConnected: () -> Unit,
    onSkip: () -> Unit,
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val userPreferences   = viewModel.userPreferences
    val stepCounterManager = viewModel.stepCounterManager
    val scope             = rememberCoroutineScope()
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                userPreferences.setHealthConnectGranted(true)
                try { StepCounterService.start(context) } catch (_: Exception) {}
                onConnected()
            } else {
                userPreferences.setHealthConnectGranted(false)
                snackbarHostState.showSnackbar("Permission denied. You can enable it later.")
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

            // ── Top content ────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Spacer(modifier = Modifier.height(32.dp))

                OnboardingProgressBar(currentStep = 3, totalSteps = 3)

                Spacer(modifier = Modifier.height(48.dp))

                // Step ring illustration — layered circles give depth
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(PamojaIndigoSubtle)
                    )
                    // Inner icon box
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
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = "Step tracking",
                            tint     = PamojaWhite,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text  = "Track your steps",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PamojaTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text  = "Pamoja counts your steps using your device sensor so your group always sees your real progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PamojaTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Permission explanation card ────────────────────────
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
                        icon       = Icons.AutoMirrored.Filled.DirectionsWalk,
                        iconColor  = PamojaIndigo,
                        iconBg     = PamojaIndigoSubtle,
                        title      = "Physical activity",
                        subtitle   = "Step count using device hardware sensor"
                    )
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PamojaBorder)
                    )
                    PermissionRow(
                        icon       = Icons.Default.Shield,
                        iconColor  = PamojaGreen,
                        iconBg     = PamojaGreenSubtle,
                        title      = "Nothing else",
                        subtitle   = "No location, heart rate or sleep data"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PamojaBorder)
                    )
                    PermissionRow(
                        icon       = Icons.Default.Lock,
                        iconColor  = PamojaGreen,
                        iconBg     = PamojaGreenSubtle,
                        title      = "Private by default",
                        subtitle   = "Only your group members see your steps"
                    )
                }

                if (!stepCounterManager.isStepCounterAvailable()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text  = "Step counter sensor is not available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Bottom CTAs ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACTIVITY_RECOGNITION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (granted) {
                            scope.launch {
                                userPreferences.setHealthConnectGranted(true)
                                try { StepCounterService.start(context) } catch (_: Exception) {}
                                onConnected()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    },
                    enabled  = stepCounterManager.isStepCounterAvailable(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = PamojaIndigo,
                        contentColor           = PamojaWhite,
                        disabledContainerColor = PamojaIndigoSubtle,
                        disabledContentColor   = PamojaTextSecondary
                    )
                ) {
                    Text(
                        text  = "Enable step tracking",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                TextButton(onClick = onSkip) {
                    Text(
                        text  = "Maybe later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaTextSecondary
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

// ─── Permission explanation row ───────────────────────────────────────────────
@Composable
fun HealthAccessRow(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    PermissionRow(icon, iconTint, iconBackground, title, subtitle)
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint     = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
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