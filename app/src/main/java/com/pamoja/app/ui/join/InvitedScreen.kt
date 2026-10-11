package com.pamoja.app.ui.join

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * What an invite is, before sign-in: the same card as the signed-in preview.
 * [onJoin] goes on to sign-in with the decision kept; [onNotNow] goes back to
 * where they were, with the invite still waiting for after sign-in.
 */
@Composable
fun InvitedScreen(
    code: String,
    onJoin: () -> Unit,
    onNotNow: () -> Unit,
    viewModel: InvitedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(code) { viewModel.load(code) }
    JoinGroupContent(
        state = state,
        onJoin = { viewModel.accept(onJoin) },
        onOpen = {},
        onRetry = viewModel::retry,
        onCancel = onNotNow,
        signedOut = true,
    )
}
