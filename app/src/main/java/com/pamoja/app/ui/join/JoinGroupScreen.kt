package com.pamoja.app.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import java.text.NumberFormat

/**
 * The invite preview (mockup row C, "Invited").
 *
 * Shows what someone is being asked to join before they join it: the group's
 * name, how many people are in it, and the weekly goal they would be signing
 * up to. Every dead end here names a way forward rather than just refusing.
 */
@Composable
fun JoinGroupScreen(
    code: String,
    onJoined: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: JoinGroupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(code) { viewModel.resolve(code) }

    LaunchedEffect(uiState.joinedGroupId) {
        uiState.joinedGroupId?.let { groupId ->
            viewModel.clearNavigation()
            onJoined(groupId)
        }
    }

    JoinGroupContent(
        state = uiState,
        onJoin = viewModel::join,
        onOpen = viewModel::openExistingGroup,
        onRetry = viewModel::retry,
        onCancel = onCancel,
    )
}

/** Stateless boundary for previews and tests. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JoinGroupContent(
    state: JoinUiState,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val group = state.group

    KitScreen(
        bottom = {
            when {
                group == null && state.isResolving -> Unit
                group == null -> {
                    // Cancel stays reachable in every state, including the dead
                    // ends, so nobody is stuck on an invite that will never work.
                    PrimaryButton(stringResource(R.string.common_try_again), onClick = onRetry)
                    QuietButton(stringResource(R.string.common_back_to_groups), onClick = onCancel)
                }
                state.isAlreadyMember -> {
                    PrimaryButton(stringResource(R.string.join_open_group), onClick = onOpen)
                    QuietButton(stringResource(R.string.common_not_now), onClick = onCancel)
                }
                else -> {
                    PrimaryButton(
                        stringResource(if (state.isFull) R.string.join_button_full else R.string.join_button),
                        onClick = onJoin,
                        enabled = state.canJoin,
                        loading = state.isJoining,
                    )
                    QuietButton(stringResource(R.string.common_not_now), onClick = onCancel)
                }
            }
        },
    ) {
        val resolveError = state.resolveError
        when {
            state.isResolving -> {
                KitSkeleton(height = 360.dp)
                KitSkeleton(height = 20.dp)
            }

            resolveError != null -> {
                val copy = resolveError.toErrorCopy()
                KitNotice(title = stringResource(copy.title), body = copy.body(context), danger = true)
            }

            group != null -> {
                KitCard(tone = CardTone.Hero, padding = 24.dp, spacing = 14.dp) {
                    Text("pamoja", style = Kit.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
                    Seats(members = group.memberCount, cap = group.maxMemberCap)
                    Text(
                        stringResource(if (state.isAlreadyMember) R.string.join_already_in else R.string.join_invited_to),
                        style = Kit.type.bodyStrong.copy(fontSize = 18.sp),
                    )
                    Text(group.name, style = Kit.type.display.copy(fontSize = 46.sp, lineHeight = 46.sp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip(pluralStringResource(R.plurals.member_count, group.memberCount, group.memberCount))
                        HeroChip("${NumberFormat.getIntegerInstance().format(group.weeklyTarget)} ${stringResource(R.string.join_target_label)}")
                    }
                }
                Text(stringResource(R.string.join_shared_goal_body), style = Kit.type.body, color = Kit.colors.muted)

                when {
                    state.isAlreadyMember -> KitNotice(
                        title = stringResource(R.string.join_already_member_title),
                        body = stringResource(R.string.join_already_member_body),
                    )
                    state.isFull -> KitNotice(
                        title = stringResource(R.string.join_full_title),
                        body = stringResource(R.string.join_full_body, group.maxMemberCap),
                    )
                    state.isOffline -> KitNotice(
                        title = stringResource(R.string.join_offline_title),
                        body = stringResource(R.string.join_offline_body),
                    )
                    state.joinError != null -> KitNotice(
                        title = stringResource(R.string.join_failed_title),
                        body = state.joinError.toJoinFailureBody(),
                        danger = true,
                    )
                }
            }
        }
    }
}

/**
 * One seat per place in the group: filled for each member, open for the room
 * left, so the size of the group and how full it is read at a glance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Seats(members: Int, cap: Int) {
    val onHero = Kit.colors.onHero
    val seats = cap.coerceAtLeast(members).coerceIn(1, MAX_SEATS)
    val description = pluralStringResource(R.plurals.member_count, members, members)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(seats) { index ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (index < members) onHero else Color.Transparent)
                    .border(2.dp, onHero, CircleShape),
            )
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text,
        style = Kit.type.bodyStrong.copy(fontSize = 14.sp),
        modifier = Modifier
            .clip(CircleShape)
            // White at 60% on light lilac, 10% on dark, as mocked: the page's
            // surface colour vanished against the dark hero.
            .background(Color.White.copy(alpha = if (Kit.colors.isDark) 0.10f else 0.6f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

private const val MAX_SEATS = 12

/**
 * Wording specific to a failed join.
 *
 * The generic copy would say "someone got there first, refresh", which is not
 * what a person needs when the thing that happened is that a group filled up.
 */
@Composable
private fun AppError.toJoinFailureBody(): String = when (this) {
    is AppError.Conflict -> stringResource(R.string.join_failed_taken)
    is AppError.Offline -> stringResource(R.string.join_failed_offline)
    is AppError.PermissionDenied -> stringResource(R.string.join_failed_closed)
    else -> stringResource(R.string.join_failed_generic)
}
