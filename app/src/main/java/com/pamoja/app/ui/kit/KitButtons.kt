package com.pamoja.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Large fills the width (56dp); Small hugs its label (44dp). */
enum class ButtonSize { Large, Small }

/**
 * The screen's main action. Near-black in light mode, lilac in dark.
 * [danger] makes it red, only for confirming something that cannot be undone.
 *
 * Heights are minimums, not fixed: at 200% font size the label wraps and the
 * button grows rather than clipping it.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
    danger: Boolean = false,
) {
    val colors = Kit.colors
    KitButton(
        text = text, onClick = onClick, modifier = modifier, size = size,
        enabled = enabled, loading = loading, icon = icon,
        container = if (danger) colors.danger else colors.button,
        // The dark theme's danger is a light coral, which needs ink on it.
        content = if (danger && !colors.isDark) Color.White else colors.onButton,
        border = null,
        textStyle = if (size == ButtonSize.Large) Kit.type.button else Kit.type.buttonSmall,
    )
}

/** Alternatives beside or below a primary action, e.g. the sign-in options. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
    /** False for brand marks that must keep their own colours, e.g. Google's G. */
    tintIcon: Boolean = true,
) {
    KitButton(
        text = text, onClick = onClick, modifier = modifier, size = size,
        enabled = enabled, loading = loading, icon = icon, tintIcon = tintIcon,
        container = Kit.colors.button2, content = Kit.colors.ink,
        border = BorderStroke(1.5.dp, Kit.colors.button2Border),
        // 16sp at full width, as drawn on the sign-in options.
        textStyle = if (size == ButtonSize.Large) Kit.type.buttonSmall.copy(fontSize = 16.sp) else Kit.type.buttonSmall,
    )
}

/** A text-only action, e.g. "I have an invite link" or "Leave group". */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    KitButton(
        text = text, onClick = onClick, modifier = modifier.fillMaxWidth(),
        size = ButtonSize.Small, enabled = enabled, loading = false, icon = null,
        container = Color.Transparent,
        content = if (danger) Kit.colors.danger else Kit.colors.ink,
        border = null, textStyle = Kit.type.buttonSmall, minHeight = 48.dp,
    )
}

@Composable
private fun KitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    size: ButtonSize,
    enabled: Boolean,
    loading: Boolean,
    @DrawableRes icon: Int?,
    tintIcon: Boolean = true,
    container: Color,
    content: Color,
    border: BorderStroke?,
    textStyle: TextStyle,
    minHeight: Dp = if (size == ButtonSize.Large) 56.dp else 44.dp,
) {
    val widthModifier = if (size == ButtonSize.Large) Modifier.fillMaxWidth() else Modifier
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = PillShape,
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .then(widthModifier)
            .defaultMinSize(minHeight = minHeight)
            // Disabled reads as disabled without a second palette to maintain.
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                PaddingValues(horizontal = if (size == ButtonSize.Large) 24.dp else 18.dp, vertical = 10.dp),
            ),
        ) {
            if (loading) {
                // Keeps the label's name for TalkBack while the spinner shows.
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = text },
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon?.let {
                        Icon(
                            painter = painterResource(it),
                            contentDescription = null,
                            tint = if (tintIcon) LocalContentColor.current else Color.Unspecified,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(text = text, style = textStyle, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * A 44dp round button with only an icon, e.g. Back or Group details.
 * [contentDescription] is required: an icon-only control needs a spoken name.
 * On a white card or sheet pass a [container] that shows, e.g. iconBackground.
 */
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Kit.colors.surface,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = container,
        contentColor = Kit.colors.ink,
        modifier = modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
