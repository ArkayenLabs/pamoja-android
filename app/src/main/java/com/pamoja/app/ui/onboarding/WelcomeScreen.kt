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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit) {

    // Gradient background: subtle indigo tint at top fades into the dark background.
    // This is reliable across all Android versions unlike Modifier.blur() which
    // requires API 31+ and renders as a hard rectangle on older devices.
    val backgroundGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF1C1A3A),  // Deep indigo-tinted dark at very top
            0.45f to PamojaBackground,  // Fades into app background colour
            1.0f to PamojaBackground    // Solid dark from midpoint down
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
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top: progress bar + logo area ─────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Step 1 of 3 — consistent with ProfileSetup (step 2) and HealthConnect (step 3)
                OnboardingProgressBar(currentStep = 1, totalSteps = 3)

                Spacer(modifier = Modifier.height(48.dp))

                // App icon — indigo gradient rounded square
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(PamojaIndigo, PamojaIndigoDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Pamoja logo",
                        tint               = PamojaWhite,
                        modifier           = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text  = "Pamoja",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PamojaTextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text      = "Walk further, together.",
                    style     = MaterialTheme.typography.bodyLarge.copy(
                        color     = PamojaTextSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Feature pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        icon     = Icons.AutoMirrored.Filled.DirectionsWalk,
                        label    = "Step tracking",
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon     = Icons.Default.Groups,
                        label    = "Group goals",
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon     = Icons.Default.Leaderboard,
                        label    = "Leaderboard",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Bottom CTA ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick  = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PamojaIndigo,
                        contentColor   = PamojaWhite
                    )
                ) {
                    Text(
                        text  = "Get started",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clickable sign-in link for returning users
                TextButton(onClick = onSignIn) {
                    Text(
                        text  = "Already have an account? Sign in",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = PamojaIndigo
                        )
                    )
                }
            }
        }
    }
}

// ─── Feature Pill ─────────────────────────────────────────────────────────────
@Composable
private fun FeaturePill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PamojaIndigoSubtle)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = PamojaIndigo,
            modifier           = Modifier.size(20.dp)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color     = PamojaIndigo,
                textAlign = TextAlign.Center,
                fontSize  = 10.sp
            )
        )
    }
}