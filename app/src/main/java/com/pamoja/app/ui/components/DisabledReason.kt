package com.pamoja.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Why the control above this is greyed out.
 *
 * A disabled button with nothing next to it is the worst state in an app: the
 * user cannot tell whether it is broken, whether they missed a field, or
 * whether they are simply not allowed. They tap it repeatedly, then leave.
 *
 * Sits directly beneath the control it explains, in one line, so the reason is
 * read in the same glance as the thing it applies to. Deliberately lighter than
 * [PamojaNotice], which is a card and is right when a whole form is blocked;
 * this is for a single control inside a form that otherwise works.
 *
 * [actionLabel] exists for the case where the reason is also fixable from here.
 * Offline has no action, it resolves itself. A free-plan limit does: the way
 * past it is a button, and the reason line is where someone is already looking.
 */
@Composable
fun DisabledReason(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = PamojaIcons.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalPamojaColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp),
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(Spacing.x1))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(PamojaRadii.xs))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.x1),
            )
        }
    }
}
