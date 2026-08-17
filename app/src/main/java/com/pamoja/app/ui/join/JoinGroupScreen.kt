package com.pamoja.app.ui.join

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.GroupAvatar
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The invite preview.
 *
 * Shows what someone is being asked to join before they join it: the group's
 * name, how many people are in it, and the weekly target they would be signing
 * up to. Every dead end here names a way forward rather than just refusing.
 */
@Composable
fun JoinGroupScreen(
    code: String,
    onJoined: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: JoinGroupViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(code) { viewModel.resolve(code) }

    LaunchedEffect(uiState.joinedGroupId) {
        uiState.joinedGroupId?.let { groupId ->
            viewModel.clearNavigation()
            onJoined(groupId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OfflineBanner(isOffline = uiState.isOffline)

            when {
                uiState.isResolving -> InvitePreviewSkeleton()

                uiState.resolveError != null -> PamojaErrorState(
                    error = uiState.resolveError!!,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.group != null -> InvitePreviewContent(
                    state = uiState,
                    onJoin = { viewModel.join() },
                    onOpen = { viewModel.openExistingGroup() },
                    onCancel = onCancel,
                )
            }
        }

        // Cancel stays reachable in every state, including the dead ends, so
        // nobody is stuck on an invite that will never work.
        if (!uiState.isResolving && uiState.group == null) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.x6),
            ) {
                Text(
                    text = stringResource(R.string.common_back_to_groups),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                )
            }
        }
    }
}

@Composable
private fun InvitePreviewContent(
    state: JoinUiState,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val group = state.group ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Spacing.x10))

        // The group's own initials and colour, not a generic people icon. It is
        // the same avatar the home list draws, so a group someone has already
        // seen is recognisable here before they have read its name.
        GroupAvatar(
            name = group.name,
            size = 84.dp,
            cornerRadius = PamojaRadii.xxl,
            textStyle = MaterialTheme.typography.displaySmall,
            photoUrl = group.photoUrl,
        )

        Spacer(modifier = Modifier.height(Spacing.x5))

        Text(
            text = stringResource(
                if (state.isAlreadyMember) R.string.join_already_in
                else R.string.join_invited_to
            ).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.x2))

        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.x6))

        // The two facts that decide whether someone wants in: how many people,
        // and how much walking.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            InviteStat(
                value = "${group.memberCount}",
                label = stringResource(if (group.memberCount == 1) R.string.join_members_one else R.string.join_members_other),
                modifier = Modifier.weight(1f),
            )
            InviteStat(
                value = "%,d".format(group.weeklyTarget),
                label = stringResource(R.string.join_target_label),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x6))

        when {
            state.isAlreadyMember -> PamojaNotice(
                icon = PamojaIcons.Check,
                title = stringResource(R.string.join_already_member_title),
                body = stringResource(R.string.join_already_member_body),
                tone = NoticeTone.Success,
            )

            state.isFull -> PamojaNotice(
                icon = PamojaIcons.Users,
                title = stringResource(R.string.join_full_title),
                body = stringResource(R.string.join_full_body, group.maxMemberCap),
                tone = NoticeTone.Warning,
            )

            state.isOffline -> PamojaNotice(
                icon = PamojaIcons.AlertCircle,
                title = stringResource(R.string.join_offline_title),
                body = stringResource(R.string.join_offline_body),
                tone = NoticeTone.Warning,
            )

            state.joinError != null -> PamojaNotice(
                icon = PamojaIcons.AlertCircle,
                title = stringResource(R.string.join_failed_title),
                body = state.joinError.toJoinFailureBody(),
                tone = NoticeTone.Danger,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.isAlreadyMember) {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                ),
            ) {
                Text(text = stringResource(R.string.join_open_group), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onJoin,
                enabled = state.canJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                if (state.isJoining) {
                    CircularProgressIndicator(
                        color = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = stringResource(if (state.isFull) R.string.join_button_full else R.string.join_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.x2))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(if (state.isAlreadyMember) R.string.common_not_now else R.string.common_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x6))
    }
}

@Composable
private fun InviteStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PamojaRadii.xl),
        color = colors.surface1,
        border = BorderStroke(Layout.strokeHairline, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.x4, horizontal = Spacing.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InvitePreviewSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Spacing.x10))
        SkeletonBlock(
            modifier = Modifier.size(72.dp),
            height = 72.dp,
            shape = RoundedCornerShape(PamojaRadii.lg),
        )
        Spacer(modifier = Modifier.height(Spacing.x5))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f), height = 14.dp)
        Spacer(modifier = Modifier.height(Spacing.x3))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 28.dp)
        Spacer(modifier = Modifier.height(Spacing.x6))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            SkeletonBlock(modifier = Modifier.weight(1f), height = 76.dp)
            SkeletonBlock(modifier = Modifier.weight(1f), height = 76.dp)
        }
    }
}

/**
 * Wording specific to a failed join.
 *
 * The generic copy would say "someone got there first, refresh", which is not
 * what a person needs when the thing that happened is that a group filled up.
 */
@Composable
private fun Throwable.toJoinFailureBody(): String = when (this) {
    is com.pamoja.app.domain.error.AppError.Conflict ->
        stringResource(R.string.join_failed_taken)

    is com.pamoja.app.domain.error.AppError.Offline ->
        stringResource(R.string.join_failed_offline)

    is com.pamoja.app.domain.error.AppError.PermissionDenied ->
        stringResource(R.string.join_failed_closed)

    else -> stringResource(R.string.join_failed_generic)
}
