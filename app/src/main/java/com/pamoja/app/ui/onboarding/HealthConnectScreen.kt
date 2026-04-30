package com.pamoja.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.data.local.health.StepCounterService
import com.pamoja.app.ui.theme.PamojaBlue
import com.pamoja.app.ui.theme.PamojaBlueLight
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenLight
import kotlinx.coroutines.launch

@Composable
fun HealthConnectScreen(
    onConnected: () -> Unit,
    onSkip: () -> Unit,
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val userPreferences = viewModel.userPreferences
    val stepCounterManager = viewModel.stepCounterManager
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            if (granted) {
                userPreferences.setHealthConnectGranted(true)
                StepCounterService.start(context)
                onConnected()
            } else {
                userPreferences.setHealthConnectGranted(false)
                snackbarHostState.showSnackbar(
                    "Permission denied. You can enable it later from settings."
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp))

                ProgressDots(current = 3, total = 3)

                Spacer(modifier = Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PamojaBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Step Counter",
                        tint = PamojaBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Track your steps",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pamoja counts your steps automatically so your group always sees your real progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "What Pamoja accesses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    HealthAccessRow(
                        icon = Icons.Default.DirectionsWalk,
                        iconBackground = PamojaBlueLight,
                        iconTint = PamojaBlue,
                        title = "Physical activity",
                        subtitle = "Step count only, using your device sensor"
                    )
                    HealthAccessRow(
                        icon = Icons.Default.Shield,
                        iconBackground = PamojaGreenLight,
                        iconTint = PamojaGreen,
                        title = "Nothing else",
                        subtitle = "No location, heart rate or sleep data"
                    )
                }
            }

            Column(
                modifier = Modifier.padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!stepCounterManager.isStepCounterAvailable()) {
                    Text(
                        text = "Step counter sensor not available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACTIVITY_RECOGNITION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (alreadyGranted) {
                            scope.launch {
                                userPreferences.setHealthConnectGranted(true)
                                StepCounterService.start(context)
                                onConnected()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    },
                    enabled = stepCounterManager.isStepCounterAvailable(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PamojaBlue)
                ) {
                    Text(
                        text = "Enable step tracking",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onSkip) {
                    Text(
                        text = "Maybe later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun HealthAccessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}