package com.pamoja.app.ui.kit

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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * A tappable row inside a card, e.g. "Weekly goal   70,000 steps  >".
 * [showDivider] draws the hairline under it; leave it off the last row.
 */
@Composable
fun ListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    showDivider: Boolean = true,
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
 * trailing slot (steps, a cheer button, an options button).
 */
@Composable
fun PersonRow(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    seed: String = name,
    detail: String? = null,
    emphasised: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
    ) {
        Avatar(name = name, photoUrl = photoUrl, seed = seed, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = Kit.type.bodyStrong.copy(
                    fontWeight = if (emphasised) FontWeight.ExtraBold else FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let { Text(it, style = Kit.type.caption, color = mutedContentColor()) }
        }
        trailing()
    }
}
