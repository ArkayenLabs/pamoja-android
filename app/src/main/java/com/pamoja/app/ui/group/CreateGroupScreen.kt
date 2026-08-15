package com.pamoja.app.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.rememberSingleClick
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaElevation
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing

@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState           by viewModel.uiState.collectAsState()

    var groupName          by remember { mutableStateOf("") }
    var weeklyTargetIndex  by remember { mutableFloatStateOf(2f) }
    var maxMembers         by remember { mutableFloatStateOf(10f) }
    var canMembersEdit     by remember { mutableStateOf(true) }

    // Hoisted: validate runs outside composable scope.
    val nameRequired = stringResource(R.string.create_group_name_required)
    val nameTooLong = stringResource(R.string.create_group_name_too_long)

    // One rule, two callers: the field checks it on blur, the button checks it
    // every recomposition. Previously the button only asked isNotBlank(), so a
    // name of pure spaces passed the gate and arrived at Firestore trimmed to "".
    val nameErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> nameRequired
            input.trim().length > 50 -> nameTooLong
            else -> null
        }
    }
    val nameError = nameErrorFor(groupName)

    val stepPresets      = listOf(35_000, 50_000, 70_000, 100_000, 150_000)
    val stepPresetLabels = listOf("35k", "50k", "70k", "100k", "150k")
    val selectedIndex    = weeklyTargetIndex.toInt()
    val selectedTarget   = stepPresets[selectedIndex]
    val memberCount      = maxMembers.toInt()

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let {
            viewModel.clearCreatedGroupId()
            onGroupCreated(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Outside the scroll region, so the condition stays on screen rather
            // than scrolling away while the user is still offline.
            OfflineBanner(isOffline = uiState.isOffline)

            Box(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Layout.screenGutter)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(Spacing.x2))

                    // ── Back, a circular chip rather than an arrow and a word ──
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surface2)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter            = painterResource(PamojaIcons.ArrowLeft),
                            contentDescription = stringResource(R.string.common_back),
                            tint               = colors.textPrimary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.x4))

                    Text(
                        text  = stringResource(R.string.create_group_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(Spacing.x2))
                    Text(
                        text  = stringResource(R.string.create_group_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(Spacing.x6))

                    // ── Group name ─────────────────────────────────────────
                    PamojaTextField(
                        value         = groupName,
                        onValueChange = { groupName = it },
                        label         = stringResource(R.string.create_group_name_label),
                        placeholder   = stringResource(R.string.create_group_name_placeholder),
                        enabled       = !uiState.isLoading,
                        validate      = nameErrorFor,
                    )

                    Spacer(modifier = Modifier.height(Spacing.x5))

                    // ── Weekly step goal ───────────────────────────────────
                    SettingCard {
                        CardHeader(
                            label = stringResource(R.string.create_group_weekly_goal),
                        ) {
                            Text(
                                text  = "%,d".format(selectedTarget),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.accentPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.x4))

                        PamojaSlider(
                            value         = weeklyTargetIndex,
                            onValueChange = { weeklyTargetIndex = it },
                            valueRange    = 0f..4f,
                            steps         = 3,
                            accent        = colors.accentPrimary,
                        )

                        Spacer(modifier = Modifier.height(Spacing.x3))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            stepPresetLabels.forEachIndexed { index, label ->
                                val active = index == selectedIndex
                                Text(
                                    text  = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (active) colors.accentPrimary else colors.textTertiary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.x3))

                        // What the number actually asks of a person. A weekly
                        // group total means nothing until it is divided by seven
                        // days and the people expected to walk it.
                        Text(
                            text = pluralStringResource(
                                R.plurals.create_group_goal_hint,
                                memberCount,
                                "%,d".format(selectedTarget / 7 / memberCount),
                                memberCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.x3))

                    // ── Max members ────────────────────────────────────────
                    SettingCard {
                        CardHeader(
                            label = stringResource(R.string.create_group_max_members),
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text  = "$memberCount",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text  = " / 20",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(bottom = Spacing.x1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.x4))

                        // Ink rather than the brand colour, per the design. Only
                        // the goal is a brand-coloured decision; the cap is a
                        // constraint, and colouring both flattens the hierarchy.
                        PamojaSlider(
                            value         = maxMembers,
                            onValueChange = { maxMembers = it },
                            valueRange    = 2f..20f,
                            steps         = 17,
                            accent        = colors.textPrimary,
                        )

                        Spacer(modifier = Modifier.height(Spacing.x3))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("2",  style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                            Text("20", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.x3))

                    // ── Members can edit goal toggle ───────────────────────
                    SettingCard(verticalPadding = Spacing.x4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text  = stringResource(R.string.create_group_members_edit),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.x1))
                                Text(
                                    text  = stringResource(R.string.create_group_members_edit_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.x3))
                            Switch(
                                checked         = canMembersEdit,
                                onCheckedChange = { canMembersEdit = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor    = Color.White,
                                    checkedTrackColor    = colors.accentPrimary,
                                    checkedBorderColor   = colors.accentPrimary,
                                    uncheckedThumbColor  = colors.textTertiary,
                                    uncheckedTrackColor  = colors.surface2,
                                    uncheckedBorderColor = colors.borderDefault
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.x5))

                    // Both of these sit above the button they block, and both
                    // persist. A snackbar used to carry the failure and then
                    // vanish, leaving a form that had silently done nothing.
                    if (uiState.isOffline) {
                        PamojaNotice(
                            icon  = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.create_group_offline_title),
                            body  = stringResource(R.string.create_group_offline_body),
                            tone  = NoticeTone.Warning,
                        )
                    } else if (uiState.error != null) {
                        PamojaNotice(
                            icon  = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.create_group_failed_title),
                            body  = uiState.error!!.toErrorCopy().body(LocalContext.current),
                            tone  = NoticeTone.Danger,
                        )
                    }

                    // Why the button is dead. Only once they have actually typed
                    // something, since nagging an untouched empty form is noise.
                    if (groupName.isNotEmpty() && nameError != null) {
                        Spacer(modifier = Modifier.height(Spacing.x2))
                        Text(
                            text  = nameError,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.statusDanger,
                        )
                    }

                    // Clears the floating bar below, which overlaps this column.
                    Spacer(modifier = Modifier.height(BarClearance))
                }

                // ── Floating action bar ────────────────────────────────────
                // The submit control never scrolls away. Previously it sat at
                // the bottom of the scroll region, so on a shorter phone the
                // screen offered no visible way to finish and looked stuck.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.38f to colors.surfaceApp,
                                1f to colors.surfaceApp,
                            )
                        )
                        .padding(
                            start = Layout.screenGutter,
                            end = Layout.screenGutter,
                            top = Spacing.x8,
                            bottom = Spacing.x6,
                        )
                        .navigationBarsPadding()
                ) {
                    Button(
                        // Two taps inside the recomposition gap would create two
                        // groups, and there is no way to delete the unwanted one.
                        onClick = rememberSingleClick {
                            viewModel.createGroup(
                                name                 = groupName.trim(),
                                weeklyTarget         = selectedTarget,
                                maxMemberCap         = memberCount,
                                canMembersEditTarget = canMembersEdit
                            )
                        },
                        enabled  = nameError == null && uiState.canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Layout.buttonHeight),
                        shape  = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor         = colors.accentPrimary,
                            contentColor           = colors.textOnBrand,
                            disabledContainerColor = colors.accentPrimarySubtle,
                            disabledContentColor   = colors.textTertiary
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = colors.textOnBrand,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(Spacing.x2))
                        }
                        Text(
                            text  = stringResource(
                                if (uiState.isLoading) R.string.create_group_creating
                                else R.string.create_group_submit
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** Room the floating bar occupies, so scrolled content can clear it. */
private val BarClearance = 132.dp

/**
 * Card header: a mono, tracked-out caps label on the left, the value on the
 * right. The label is the design system's `.lbl`, which is what carries most of
 * the identity on an otherwise quiet screen.
 */
@Composable
private fun CardHeader(label: String, value: @Composable () -> Unit) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text  = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.x1)
        )
        value()
    }
}

/**
 * The design system's slider: an 8dp pill track and a 28dp thumb that is a
 * surface disc ringed in the accent, not a filled dot. Material's default thumb
 * is a filled lozenge and reads as a different control entirely.
 *
 * The track override is what needs the opt-in: an 8dp pill is not reachable
 * through SliderDefaults.colors(), only by supplying the track itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PamojaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
) {
    val colors = LocalPamojaColors.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            activeTrackColor   = accent,
            inactiveTrackColor = colors.surface2,
            activeTickColor    = Color.Transparent,
            inactiveTickColor  = colors.borderDefault,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.surface1)
                    .border(3.5.dp, accent, CircleShape)
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(8.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor   = accent,
                    inactiveTrackColor = colors.surface2,
                    activeTickColor    = Color.Transparent,
                    inactiveTickColor  = colors.borderDefault,
                ),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}

// ─── Setting card container ────────────────────────────────────────────────────
@Composable
private fun SettingCard(
    verticalPadding: androidx.compose.ui.unit.Dp = Spacing.x4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPamojaColors.current
    val cardShape = RoundedCornerShape(PamojaRadii.xl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(PamojaElevation.card, cardShape, spotColor = colors.overlay)
            .clip(cardShape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(horizontal = Spacing.x5, vertical = verticalPadding),
        content = content
    )
}
