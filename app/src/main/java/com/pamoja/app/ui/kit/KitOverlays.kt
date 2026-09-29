package com.pamoja.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * A sheet from the bottom, e.g. the group switcher or a setting's choices.
 *
 * Material's ModalBottomSheet does the gestures and insets; everything that
 * shows is the kit's. Content scrolls, so a long list or 200% font size never
 * pushes an action off the screen. [content] gets `close`, which slides the
 * sheet away and then runs what the choice leads to, e.g. navigating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit,
) {
    val colors = Kit.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = colors.surface,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = KitRadius.hero, topEnd = KitRadius.hero),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(colors.line),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            title?.let { Text(it, style = Kit.type.title, modifier = Modifier.padding(bottom = 8.dp)) }
            content(close)
        }
    }
}

/**
 * A question that needs an answer before going on, e.g. "Leave this group?".
 *
 * [danger] turns the confirm button red, for actions that cannot be undone.
 * [content] sits between the body and the buttons, e.g. a field to type the
 * group's name into before deleting it.
 */
@Composable
fun KitDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    dismissText: String? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
    /**
     * False once a stray tap outside would throw away something that cost
     * money, e.g. a verification code already sent. Back still closes it.
     */
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HeroShape)
                .background(Kit.colors.surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = Kit.type.title, color = Kit.colors.ink)
            body?.let { Text(it, style = Kit.type.body, color = Kit.colors.muted) }
            content()
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    loading = loading,
                    danger = danger,
                )
                dismissText?.let { QuietButton(it, onClick = onDismiss, enabled = !loading) }
            }
        }
    }
}
