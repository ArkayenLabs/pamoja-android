package com.pamoja.app.ui.card

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons
import kotlinx.coroutines.launch

/** The weekly card, from group details: a preview and one button to share it. */
@Composable
fun WeeklyCardScreen(onBack: () -> Unit, viewModel: WeeklyCardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.weekly_card_share_failed)
    val shareText = stringResource(R.string.weekly_card_share_text, stringResource(R.string.app_name), WeeklyCardShare.appLink())

    WeeklyCardContent(
        state = state,
        layer = layer,
        sharing = sharing,
        snackbar = snackbar,
        onBack = onBack,
        onShowLastWeek = viewModel::showLastWeek,
        onShare = {
            scope.launch {
                sharing = true
                runCatching { WeeklyCardShare.share(context, layer.toImageBitmap().asAndroidBitmap(), shareText) }
                    .onSuccess { viewModel.onShared() }
                    .onFailure { snackbar.showSnackbar(failed) }
                sharing = false
            }
        },
    )
}

/** Stateless rendering boundary, used by the debug preview. */
@Composable
internal fun WeeklyCardContent(
    state: WeeklyCardUiState,
    layer: GraphicsLayer,
    sharing: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onShowLastWeek: (Boolean) -> Unit = {},
) = KitInAppTheme {
    val context = LocalContext.current
    // The whole card on screen at once where the window allows: the height
    // left after the bars, back button, title and Share button (about 340dp
    // together, measured on a 411x891dp phone), at the card's 9:16.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value.toInt() }
    val maxCardWidth = ((windowHeight - 340).coerceAtLeast(240) * 9 / 16).dp

    Box(Modifier.fillMaxSize()) {
        KitScreen(
            gutter = 20.dp,
            top = {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            },
            bottom = {
                if (state.group != null) {
                    PrimaryButton(stringResource(R.string.weekly_card_share), onClick = onShare, loading = sharing)
                }
            },
        ) {
            Text(stringResource(R.string.weekly_card_title), style = Kit.type.headline)
            // Early in a week there is little to share yet; last week is ready.
            if (state.hasLastWeek) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekChoice(stringResource(R.string.weekly_card_this_week), selected = !state.finished) { onShowLastWeek(false) }
                    WeekChoice(stringResource(R.string.weekly_card_last_week), selected = state.finished) { onShowLastWeek(true) }
                }
            }
            when {
                state.group != null -> {
                    val description = stringResource(R.string.weekly_card_desc, state.group.name)
                    Box(Modifier.align(Alignment.CenterHorizontally)) {
                        ScaledWeeklyCard(
                            state = state,
                            layer = layer,
                            // Rounded on screen only; the shared image keeps its corners.
                            modifier = Modifier
                                .widthIn(max = maxCardWidth)
                                .clip(RoundedCornerShape(16.dp))
                                .semantics { contentDescription = description },
                        )
                    }
                }
                state.error != null -> {
                    val copy = state.error.toErrorCopy()
                    KitNotice(title = stringResource(copy.title), body = copy.body(context), danger = true)
                }
                else -> KitSkeleton(height = 480.dp)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WeekChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Kit.colors
    Text(
        label,
        style = Kit.type.buttonSmall,
        color = if (selected) colors.onButton else colors.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.button else colors.surface)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
