package com.pamoja.app.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.StepGoal
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.domain.usecase.UpdateGroupSettingsUseCase
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSheet
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.ListRow
import com.pamoja.app.ui.kit.OptionRow
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** What the create form hands back: everything a group starts with. */
internal data class NewGroup(
    val name: String,
    val weeklyTarget: Int,
    val maxMemberCap: Int,
    val weekStart: DayOfWeek,
)

/**
 * Start a group: its name, weekly goal, member limit and week start, all on
 * one screen. The three settings arrive filled with sensible defaults, so a
 * quick group is still one field and one tap; a tester found "name only" left
 * no way to set them up front. Each opens the same sheet group details uses.
 */
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let {
            viewModel.clearCreatedGroupId()
            onGroupCreated(it)
        }
    }

    CreateGroupContent(
        isLoading = uiState.isLoading,
        isOffline = uiState.isOffline,
        errorBody = uiState.error?.toErrorCopy()?.body(context),
        onCreate = { group ->
            viewModel.createGroup(
                name = group.name,
                weeklyTarget = group.weeklyTarget,
                maxMemberCap = group.maxMemberCap,
                weekStartDay = group.weekStart,
            )
        },
        onBack = onBack,
    )
}

private enum class CreateSheet { Goal, Cap, WeekStart }

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun CreateGroupContent(
    isLoading: Boolean,
    isOffline: Boolean,
    errorBody: String?,
    onCreate: (NewGroup) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    var goal by rememberSaveable { mutableIntStateOf(StepGoal.DEFAULT_WEEKLY_TOTAL) }
    var cap by rememberSaveable { mutableIntStateOf(DEFAULT_MEMBER_CAP) }
    // Monday across most of Europe and Asia, Sunday in the US, Canada and
    // Japan: the device locale's own answer.
    var weekStart by rememberSaveable { mutableStateOf(WeekWindow.localeDefault()) }
    var sheet by rememberSaveable { mutableStateOf<CreateSheet?>(null) }
    val max = UpdateGroupSettingsUseCase.MAX_GROUP_NAME_LENGTH
    // One rule for the field and the button: a name of only spaces must not
    // reach Firestore trimmed to "".
    val nameError = when {
        name.isBlank() -> stringResource(R.string.create_group_name_required)
        name.trim().length > max -> stringResource(R.string.create_group_name_too_long)
        else -> null
    }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.create_group_submit),
                onClick = { onCreate(NewGroup(name.trim(), goal, cap, weekStart)) },
                enabled = nameError == null && !isOffline,
                loading = isLoading,
            )
        },
    ) {
        Text(stringResource(R.string.start_group_title), style = Kit.type.headline)
        Text(stringResource(R.string.start_group_body), style = Kit.type.body, color = Kit.colors.muted)
        KitTextField(
            value = name,
            onValueChange = {
                touched = true
                name = it.take(max)
            },
            label = stringResource(R.string.create_group_name_label),
            placeholder = stringResource(R.string.create_group_name_placeholder),
            helper = stringResource(R.string.group_name_character_count, name.length, max),
            error = nameError.takeIf { touched },
            enabled = !isLoading,
        )
        KitCard(spacing = 0.dp, padding = 18.dp) {
            CardHeader(stringResource(R.string.details_settings_title), Modifier.padding(bottom = 6.dp))
            ListRow(
                stringResource(R.string.details_goal),
                value = stringResource(R.string.details_goal_value, "%,d".format(goal)),
                onClick = { if (!isLoading) sheet = CreateSheet.Goal },
            )
            ListRow(
                stringResource(R.string.details_member_limit),
                value = cap.toString(),
                onClick = { if (!isLoading) sheet = CreateSheet.Cap },
            )
            ListRow(
                stringResource(R.string.details_week_starts),
                value = weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                onClick = { if (!isLoading) sheet = CreateSheet.WeekStart },
                showDivider = false,
            )
        }
        if (isOffline) {
            KitNotice(
                title = stringResource(R.string.create_group_offline_title),
                body = stringResource(R.string.create_group_offline_body),
            )
        } else if (errorBody != null) {
            KitNotice(title = stringResource(R.string.create_group_failed_title), body = errorBody, danger = true)
        }
    }

    sheet?.let { which ->
        SettingSheet(
            which = which,
            goal = goal,
            cap = cap,
            weekStart = weekStart,
            onGoal = { goal = it },
            onCap = { cap = it },
            onWeekStart = { weekStart = it },
            onDismiss = { sheet = null },
        )
    }
}

/** The same choices as group details, applied to the form rather than saved. */
@Composable
private fun SettingSheet(
    which: CreateSheet,
    goal: Int,
    cap: Int,
    weekStart: DayOfWeek,
    onGoal: (Int) -> Unit,
    onCap: (Int) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(
        when (which) {
            CreateSheet.Goal -> R.string.details_goal
            CreateSheet.Cap -> R.string.details_member_limit
            CreateSheet.WeekStart -> R.string.details_week_starts
        },
    )
    KitSheet(onDismiss = onDismiss, title = title) { close ->
        when (which) {
            CreateSheet.Goal -> StepGoal.editOptionsFor(goal).forEach { option ->
                OptionRow(
                    text = stringResource(R.string.details_goal_value, "%,d".format(option)),
                    selected = option == goal,
                    onClick = { close { onGoal(option) } },
                )
            }

            CreateSheet.Cap -> {
                var draft by rememberSaveable { mutableIntStateOf(cap) }
                val floor = UpdateGroupSettingsUseCase.MIN_MEMBER_CAP
                val ceiling = UpdateGroupSettingsUseCase.MAX_MEMBER_CAP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    CircleIconButton(
                        PamojaIcons.Minus,
                        stringResource(R.string.details_fewer_desc),
                        onClick = { draft = (draft - 1).coerceAtLeast(floor) },
                        enabled = draft > floor,
                        container = Kit.colors.iconBackground,
                    )
                    Text(
                        draft.toString(),
                        style = Kit.type.number,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(width = 72.dp, height = 44.dp),
                    )
                    CircleIconButton(
                        PamojaIcons.Add,
                        stringResource(R.string.details_more_desc),
                        onClick = { draft = (draft + 1).coerceAtMost(ceiling) },
                        enabled = draft < ceiling,
                        container = Kit.colors.iconBackground,
                    )
                }
                PrimaryButton(
                    stringResource(R.string.details_save),
                    onClick = { close { onCap(draft) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            CreateSheet.WeekStart -> listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                OptionRow(
                    text = day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    selected = day == weekStart,
                    onClick = { close { onWeekStart(day) } },
                )
            }
        }
    }
}

/** The member limit a new group starts with; the admin can change it later. */
private const val DEFAULT_MEMBER_CAP = 10
