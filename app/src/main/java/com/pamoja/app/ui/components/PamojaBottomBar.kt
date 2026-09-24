package com.pamoja.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaElevation
import com.pamoja.app.ui.theme.PamojaMotion
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

enum class PamojaMainTab {
    Today,
    Groups,
    You,
}

/**
 * The Trail Dock: Pamoja's three stable destinations, kept deliberately small
 * enough that the weekly story remains the screen's hero. The icon shapes are
 * a curated Phosphor subset (MIT): a deliberately simple home for the personal
 * dashboard, then togetherness and path symbols for the other destinations.
 */
@Composable
fun PamojaBottomBar(
    selected: PamojaMainTab,
    onToday: () -> Unit,
    onGroups: () -> Unit,
    onYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.xxl)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(PamojaElevation.raised, shape)
                .clip(shape)
                .background(colors.surface1)
                .border(1.dp, colors.borderDefault, shape)
                .padding(Spacing.x1)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
        ) {
            TrailDockItem(
                selected = selected == PamojaMainTab.Today,
                icon = R.drawable.ic_nav_today,
                label = stringResource(R.string.nav_today),
                onClick = onToday,
                modifier = Modifier.weight(1f),
            )
            TrailDockItem(
                selected = selected == PamojaMainTab.Groups,
                icon = R.drawable.ic_nav_groups,
                label = stringResource(R.string.nav_groups),
                onClick = onGroups,
                modifier = Modifier.weight(1f),
            )
            TrailDockItem(
                selected = selected == PamojaMainTab.You,
                icon = R.drawable.ic_nav_you,
                label = stringResource(R.string.nav_you),
                onClick = onYou,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrailDockItem(
    selected: Boolean,
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accentPrimary else colors.textTertiary,
        animationSpec = tween(PamojaMotion.durationFast),
        label = "trail-dock-color",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(PamojaMotion.durationFast),
        label = "trail-dock-scale",
    )

    Column(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(PamojaRadii.xl))
            .background(if (selected) colors.accentPrimarySubtle else colors.surface1)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .semantics { contentDescription = label }
            .padding(vertical = Spacing.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(25.dp)
                .scale(iconScale),
        )

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = contentColor,
        )
    }
}
