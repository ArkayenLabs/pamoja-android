package com.pamoja.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaGreen
import com.pamoja.app.ui.theme.PamojaGreenSubtle
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaWhite

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit) {

    // Subtle pulsing glow animation behind the logo
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue  = 0.55f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PamojaBackground)
    ) {

        // ── Ambient glow blobs in background ────────────────────────────────
        // Top-right indigo glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = 80.dp, y = (-60).dp)
                .blur(120.dp)
                .background(
                    color = PamojaIndigo.copy(alpha = glowAlpha * 0.4f),
                    shape = CircleShape
                )
        )
        // Bottom-left green glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .blur(100.dp)
                .background(
                    color = PamojaGreen.copy(alpha = glowAlpha * 0.25f),
                    shape = CircleShape
                )
        )

        // ── Main content ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(1.dp))

            // ── Logo + headline ────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(48.dp))

                // App icon — indigo rounded square with footstep icon
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
                        imageVector  = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Pamoja logo",
                        tint         = PamojaWhite,
                        modifier     = Modifier.size(34.dp)
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
                    text  = "Walk further, together.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = PamojaTextSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(40.dp))

                // ── Feature pills row ────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        icon  = Icons.AutoMirrored.Filled.DirectionsWalk,
                        label = "Step tracking",
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon  = Icons.Default.Groups,
                        label = "Group goals",
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon  = Icons.Default.Leaderboard,
                        label = "Leaderboard",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Bottom CTA area ───────────────────────────────────────────
            Column(
                modifier = Modifier.padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
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

                // Muted sign-in hint — shown but low emphasis
                Text(
                    text  = "Already have an account? Sign in",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = PamojaTextSecondary
                    )
                )
            }
        }
    }
}

// ─── Feature Pill ────────────────────────────────────────────────────────────
// Small card with icon + label, used to surface app features on welcome screen
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
            imageVector = icon,
            contentDescription = null,
            tint = PamojaIndigo,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(
                color = PamojaIndigo,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        )
    }
}