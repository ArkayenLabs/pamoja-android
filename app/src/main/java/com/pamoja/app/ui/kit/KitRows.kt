package com.pamoja.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * A tappable row inside a card, e.g. "Weekly goal   70,000 steps  >".
 * [showDivider] draws the hairline under it; leave it off the last row.
 * [leadingIcon] marks the row's kind, e.g. a lock on a Premium feature.
 */
@Composable
fun ListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    showDivider: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = Kit.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
        ) {
            leadingIcon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                label,
                style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                color = LocalContentColor.current,
                modifier = Modifier.weight(1f),
            )
            value?.let {
                Text(
                    it,
                    style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.Normal),
                    color = mutedContentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(PamojaIcons.ChevronRight),
                contentDescription = null,
                tint = mutedContentColor(),
                modifier = Modifier.size(18.dp),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )
        }
    }
}

/**
 * A person in a list: face, name, a quiet second line, and an optional
 * trailing slot (steps, a cheer button, an options button). [avatarName]
 * gives the face its initials when [name] is a label, e.g. "You".
 */
@Composable
fun PersonRow(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    avatarName: String = name,
    seed: String = name,
    detail: String? = null,
    emphasised: Boolean = false,
    /** Drawn under the name instead of [detail], e.g. the week's day dots. */
    below: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
    ) {
        Avatar(name = avatarName, photoUrl = photoUrl, seed = seed, size = 40.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (below != null) 4.dp else 2.dp),
        ) {
            Text(
                name,
                style = Kit.type.bodyStrong.copy(
                    fontWeight = if (emphasised) FontWeight.ExtraBold else FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                below != null -> below()
                detail != null -> Text(detail, style = Kit.type.caption, color = mutedContentColor())
            }
        }
        trailing()
    }
}

/**
 * One action in a sheet, e.g. "Start a new group" or "Remove from group".
 * [danger] is for the ones that take something away.
 */
@Composable
fun ActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = if (danger) Kit.colors.danger else Kit.colors.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 8.dp),
    ) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(text, style = Kit.type.buttonSmall, color = tint, modifier = Modifier.weight(1f))
    }
}

/**
 * One choice of several, e.g. a weekly goal. The chosen one carries a check,
 * and TalkBack hears it as a selected radio button.
 */
@Composable
fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text,
            style = Kit.type.bodyStrong.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(PamojaIcons.Check),
                contentDescription = null,
                tint = Kit.colors.ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
