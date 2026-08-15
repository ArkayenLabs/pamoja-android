package com.pamoja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.GroupAvatarGradients
import com.pamoja.app.ui.theme.PamojaRadii

/**
 * A group's squircle avatar: its initials on a colour derived from its name.
 *
 * The colour is deterministic, so the same group is the same colour on the home
 * list and on an invite preview. That consistency is the entire point of having
 * one of these rather than a generic icon: it is how a group becomes
 * recognisable before its name has been read.
 */
@Composable
fun GroupAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    cornerRadius: Dp = PamojaRadii.lg,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val gradient = GroupAvatarGradients[
        (name.firstOrNull()?.code ?: 0) % GroupAvatarGradients.size
    ]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(2).uppercase(),
            style = textStyle,
            color = Color.White,
        )
    }
}
