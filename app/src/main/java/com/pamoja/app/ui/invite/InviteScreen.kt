package com.pamoja.app.ui.invite

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaQrCode
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import com.pamoja.app.util.InviteLink
import kotlinx.coroutines.launch

@Composable
fun InviteScreen(
    groupId: String,
    onGoToGroup: () -> Unit,
    onBack: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

    val inviteLink = InviteLink.build(groupId)
    val linkCopiedMessage = stringResource(R.string.invite_link_copied)
    val shareChooserTitle = stringResource(R.string.invite_share_chooser)
    val group = uiState.group

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            OfflineBanner(isOffline = uiState.isOffline)

            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(PamojaIcons.ArrowLeft),
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.x6)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.x3),
            ) {
                val compact = maxHeight < 620.dp
                val gap = if (compact) Spacing.x2 else Spacing.x3
                val qrSize = if (compact) 124.dp else 156.dp

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!uiState.hasLoadedOnce) {
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth(0.72f),
                                height = 32.dp,
                            )
                            Spacer(modifier = Modifier.height(gap))
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth(0.56f),
                                height = 14.dp,
                            )
                        } else if (group != null) {
                            val heading = buildAnnotatedString {
                                append(stringResource(R.string.invite_title_prefix))
                                pushStyle(
                                    SpanStyle(
                                        color = colors.accentPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                )
                                append(group.name)
                                pop()
                            }
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.headlineLarge,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(gap))
                            Text(
                                text = stringResource(R.string.invite_subtitle, group.maxMemberCap),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            PamojaNotice(
                                icon = PamojaIcons.AlertCircle,
                                title = stringResource(R.string.invite_details_failed_title),
                                body = stringResource(
                                    if (uiState.isOffline) R.string.invite_offline_body
                                    else R.string.invite_details_failed_body
                                ),
                                tone = if (uiState.isOffline) NoticeTone.Warning else NoticeTone.Danger,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PamojaQrCode(content = inviteLink, size = qrSize)
                        Spacer(modifier = Modifier.height(gap))
                        Text(
                            text = stringResource(R.string.invite_qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        val linkShape = RoundedCornerShape(PamojaRadii.md)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(linkShape)
                                .background(colors.surface1)
                                .border(1.dp, colors.borderSubtle, linkShape)
                                .padding(start = Spacing.x4, end = Spacing.x2, top = Spacing.x2, bottom = Spacing.x2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = inviteLink,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(Spacing.x2))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(inviteLink))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(linkCopiedMessage)
                                    }
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(PamojaIcons.Copy),
                                    contentDescription = stringResource(R.string.invite_copy_desc),
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, inviteLink)
                                }
                                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPrimary,
                                contentColor = colors.textOnBrand,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(PamojaIcons.Share),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.x2))
                            Text(
                                text = stringResource(R.string.invite_share),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        if (uiState.hasLoadedOnce && group == null) {
                            TextButton(
                                onClick = viewModel::retry,
                                enabled = !uiState.isLoading,
                            ) {
                                Text(stringResource(R.string.common_try_again))
                            }
                        } else {
                            TextButton(onClick = onGoToGroup) {
                                Text(
                                    text = stringResource(R.string.invite_go_to_group),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.accentPrimary,
                                )
                                Spacer(modifier = Modifier.width(Spacing.x1))
                                Icon(
                                    painter = painterResource(PamojaIcons.ArrowRight),
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
