package com.pamoja.app.ui.invite

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.ui.theme.PamojaBackground
import com.pamoja.app.ui.theme.PamojaBorder
import com.pamoja.app.ui.theme.PamojaIndigo
import com.pamoja.app.ui.theme.PamojaIndigoDark
import com.pamoja.app.ui.theme.PamojaIndigoLight
import com.pamoja.app.ui.theme.PamojaIndigoSubtle
import com.pamoja.app.ui.theme.PamojaSurface
import com.pamoja.app.ui.theme.PamojaSurfaceVariant
import com.pamoja.app.ui.theme.PamojaTextPrimary
import com.pamoja.app.ui.theme.PamojaTextSecondary
import com.pamoja.app.ui.theme.PamojaTextTertiary
import com.pamoja.app.ui.theme.PamojaWhite
import kotlinx.coroutines.launch

@Composable
fun InviteScreen(
    groupId: String,
    onGoToGroup: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager  = LocalClipboardManager.current
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val inviteLink = uiState.group?.inviteLink ?: "pamoja://join/$groupId"
    val groupName  = uiState.group?.name ?: ""
    val maxCap     = uiState.group?.maxMemberCap ?: 10

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
            verticalArrangement   = Arrangement.SpaceBetween,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {

            // ── Top section ───────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Spacer(modifier = Modifier.height(48.dp))

                // Success icon — gradient circle
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(PamojaIndigoSubtle)
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(PamojaIndigo, PamojaIndigoDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Groups,
                            contentDescription = "Group created",
                            tint               = PamojaWhite,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text      = "Group created!",
                    style     = MaterialTheme.typography.headlineLarge,
                    color     = PamojaTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text      = "Invite your people. This link stays active until all $maxCap spots are filled.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = PamojaTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Invite link card ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PamojaSurface)
                        .border(
                            width  = 1.dp,
                            color  = PamojaBorder,
                            shape  = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text     = inviteLink,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = PamojaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(inviteLink))
                            scope.launch { snackbarHostState.showSnackbar("Link copied") }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ContentCopy,
                            contentDescription = "Copy link",
                            tint               = PamojaIndigoLight,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Bottom CTAs ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // WhatsApp — keep the brand green but give it the same shape/height
                Button(
                    onClick = {
                        val msg = "Join my Pamoja group \"$groupName\"! " +
                                "We're tracking our steps together this week. " +
                                "Join here: $inviteLink"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg)
                            setPackage("com.whatsapp")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("WhatsApp not installed") }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor   = PamojaWhite
                    )
                ) {
                    Text(
                        text  = "Share on WhatsApp",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // More share options — secondary style
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my Pamoja group \"$groupName\"! $inviteLink"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite link"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PamojaSurfaceVariant,
                        contentColor   = PamojaTextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PamojaTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = "More share options",
                        style = MaterialTheme.typography.labelLarge.copy(color = PamojaTextPrimary)
                    )
                }

                // Go to group — ghost text button
                TextButton(onClick = onGoToGroup) {
                    Text(
                        text  = "Go to my group →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PamojaIndigo
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