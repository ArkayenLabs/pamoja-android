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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.util.InviteLink
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun InviteScreen(
    groupId: String,
    onGoToGroup: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager  = LocalClipboardManager.current
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.toSnackbarMessage(context))
            viewModel.clearError()
        }
    }

    // Always the verified https App Link, derived from the group ID rather than
    // read from Firestore. Existing documents still hold the old pamoja:// string,
    // which messengers refuse to render as a tappable link, so it is never shown.
    // Incoming links of either form still resolve, see InviteLink.parseCode.
    val inviteLink = InviteLink.build(groupId)

    // Resolved here because both are used inside click handlers and coroutine
    // scopes, neither of which is a composable scope.
    val linkCopiedMessage = stringResource(R.string.invite_link_copied)
    val shareChooserTitle = stringResource(R.string.invite_share_chooser)
    val groupName  = uiState.group?.name ?: ""
    val maxCap     = uiState.group?.maxMemberCap ?: 10

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.x6),
            verticalArrangement   = Arrangement.SpaceBetween,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {

            // ── Top section ───────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Spacer(modifier = Modifier.height(Spacing.x12))

                // Success icon, gradient circle
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(colors.accentPrimarySubtle)
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(PamojaRadii.xl))
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(colors.accentPrimary, colors.accentPrimaryPress)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter            = painterResource(PamojaIcons.Users),
                            contentDescription = stringResource(R.string.invite_created_desc),
                            tint               = colors.textOnBrand,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x6))

                Text(
                    text      = stringResource(R.string.invite_created_title),
                    style     = MaterialTheme.typography.headlineLarge,
                    color     = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.x2))

                Text(
                    text      = stringResource(R.string.invite_subtitle, maxCap),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.x7))

                // ── Invite link card ──────────────────────────────────────
                val linkShape = RoundedCornerShape(PamojaRadii.md)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(linkShape)
                        .background(colors.surface1)
                        .border(width = 1.dp, color = colors.borderSubtle, shape = linkShape)
                        .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text     = inviteLink,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(inviteLink))
                            scope.launch { snackbarHostState.showSnackbar(linkCopiedMessage) }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter            = painterResource(PamojaIcons.Copy),
                            contentDescription = stringResource(R.string.invite_copy_desc),
                            tint               = colors.accentPrimary,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Bottom CTAs ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.x4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.x3)
            ) {
                // Copy link, primary.
                // No app-specific share button here on purpose. Hardcoding
                // WhatsApp dead-ended whenever it was not installed, and it
                // presumes which messenger the user's group uses. The system
                // sheet below already surfaces WhatsApp first for anyone who
                // has it.
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteLink))
                        scope.launch { snackbarHostState.showSnackbar(linkCopiedMessage) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(PamojaRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor   = colors.textOnBrand
                    )
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.Copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.textOnBrand
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text  = stringResource(R.string.invite_copy),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Share via the system sheet, tonal secondary
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            // The bare URL, nothing wrapped around it. Prose
                            // before the link forced people to hand-edit the
                            // message before it was usable, and it stops
                            // messengers from rendering a link preview.
                            putExtra(Intent.EXTRA_TEXT, inviteLink)
                        }
                        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(PamojaRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimarySubtle,
                        contentColor   = colors.accentPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.Share),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text  = stringResource(R.string.invite_more_options),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Go to group, ghost text button with trailing arrow icon
                TextButton(onClick = onGoToGroup) {
                    Text(
                        text  = stringResource(R.string.invite_go_to_group),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(Spacing.x1))
                    Icon(
                        painter = painterResource(PamojaIcons.ArrowRight),
                        contentDescription = null,
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(16.dp)
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
