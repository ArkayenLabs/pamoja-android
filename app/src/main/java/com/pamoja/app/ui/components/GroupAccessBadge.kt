package com.pamoja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/** A small, explicit group-level access marker. Free and unresolved states stay quiet. */
@Composable
fun GroupAccessBadge(
    access: GroupAccessState,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true,
) {
    val colors = LocalPamojaColors.current
    val presentation = when (access) {
        is GroupAccessState.Premium -> AccessBadgePresentation(
            label = stringResource(R.string.group_access_premium),
            icon = PamojaIcons.Star,
            foreground = colors.accentAmber,
            background = colors.accentAmberSubtle,
        )

        is GroupAccessState.Preview -> AccessBadgePresentation(
            label = stringResource(R.string.group_access_preview),
            icon = PamojaIcons.Clock,
            foreground = colors.accentPrimary,
            background = colors.accentPrimarySubtle,
        )

        GroupAccessState.Free,
        GroupAccessState.Loading,
        is GroupAccessState.PreviewExpired,
        is GroupAccessState.Unavailable -> null
    } ?: return

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .then(
                if (showContainer) {
                    Modifier
                        .clip(RoundedCornerShape(PamojaRadii.xs))
                        .background(presentation.background)
                        .padding(horizontal = Spacing.x2, vertical = 3.dp)
                } else {
                    Modifier.padding(horizontal = Spacing.x1, vertical = 2.dp)
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(presentation.icon),
            contentDescription = null,
            tint = presentation.foreground,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = presentation.label,
            style = MaterialTheme.typography.labelSmall,
            color = presentation.foreground,
            maxLines = 1,
        )
    }
}

private data class AccessBadgePresentation(
    val label: String,
    val icon: Int,
    val foreground: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
)
