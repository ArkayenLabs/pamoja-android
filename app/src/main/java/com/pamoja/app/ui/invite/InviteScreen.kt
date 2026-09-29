package com.pamoja.app.ui.invite

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.ui.components.PamojaQrCode
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.kit.mutedContentColor
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.util.InviteLink
import kotlinx.coroutines.launch

/**
 * Right after creating a group: share, copy or show the QR, then carry on.
 * The link works before the group has loaded, so sharing never waits on it.
 */
@Composable
fun InviteScreen(
    groupId: String,
    onGoToGroup: () -> Unit,
    onBack: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

    val inviteLink = InviteLink.build(groupId)
    val linkCopied = stringResource(R.string.invite_link_copied)
    val chooserTitle = stringResource(R.string.invite_share_chooser)
    val group = uiState.group
    val failed = uiState.hasLoadedOnce && group == null

    Box(Modifier.fillMaxSize()) {
        KitScreen(
            top = {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            },
            bottom = {
                PrimaryButton(
                    text = stringResource(R.string.invite_share),
                    icon = PamojaIcons.Share,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, inviteLink)
                        }
                        context.startActivity(Intent.createChooser(intent, chooserTitle))
                    },
                )
                if (failed) {
                    QuietButton(stringResource(R.string.common_try_again), onClick = viewModel::retry, enabled = !uiState.isLoading)
                } else {
                    QuietButton(stringResource(R.string.invite_go_to_group), onClick = onGoToGroup)
                }
            },
        ) {
            Text(stringResource(R.string.invite_step_title), style = Kit.type.headline)
            // The group's name as the one detail that matters; its member limit
            // tells people how many can join.
            group?.let {
                Text(it.name, style = Kit.type.title, color = Kit.colors.emphasis)
                Text(
                    stringResource(R.string.invite_subtitle, it.maxMemberCap),
                    style = Kit.type.body,
                    color = Kit.colors.muted,
                )
            }
            if (failed) {
                KitNotice(
                    title = stringResource(R.string.invite_details_failed_title),
                    body = stringResource(
                        if (uiState.isOffline) R.string.invite_offline_body else R.string.invite_details_failed_body,
                    ),
                    danger = !uiState.isOffline,
                )
            }
            KitCard(tone = CardTone.Hero, padding = 20.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PamojaQrCode(content = inviteLink, size = 180.dp)
                }
                Text(
                    stringResource(R.string.invite_qr_hint),
                    style = Kit.type.caption,
                    color = mutedContentColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SecondaryButton(
                text = stringResource(R.string.invite_copy_desc),
                icon = PamojaIcons.Copy,
                onClick = {
                    clipboardManager.setText(AnnotatedString(inviteLink))
                    scope.launch { snackbarHostState.showSnackbar(linkCopied) }
                },
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
