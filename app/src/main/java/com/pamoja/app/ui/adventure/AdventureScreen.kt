package com.pamoja.app.ui.adventure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.model.Adventure
import com.pamoja.app.ui.theme.Spacing
import com.pamoja.app.ui.theme.LocalPamojaColors
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val chapterTitles = listOf(R.string.adventure_opening_path_title, R.string.adventure_quiet_grove_title,
    R.string.adventure_shared_bridge_title, R.string.adventure_wide_horizon_title, R.string.adventure_lantern_clearing_title)
private val chapterStories = listOf(R.string.adventure_opening_path_story, R.string.adventure_quiet_grove_story,
    R.string.adventure_shared_bridge_story, R.string.adventure_wide_horizon_story, R.string.adventure_lantern_clearing_story)
private val chapterReflections = listOf(R.string.adventure_opening_path_reflection, R.string.adventure_quiet_grove_reflection,
    R.string.adventure_shared_bridge_reflection, R.string.adventure_wide_horizon_reflection, R.string.adventure_lantern_clearing_reflection)
private val chapterArt = listOf(R.drawable.trail_opening_path, R.drawable.trail_quiet_grove,
    R.drawable.trail_shared_bridge, R.drawable.trail_wide_horizon, R.drawable.trail_lantern_clearing)
private val chapterDescriptions = listOf(R.string.adventure_opening_path_alt, R.string.adventure_quiet_grove_alt,
    R.string.adventure_shared_bridge_alt, R.string.adventure_wide_horizon_alt, R.string.adventure_lantern_clearing_alt)

@Composable
fun AdventureScreen(onBack: () -> Unit, onPremium: () -> Unit, onConnect: () -> Unit,
    viewModel: AdventureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var chapter by rememberSaveable { mutableStateOf<Int?>(null) }
    val colors = LocalPamojaColors.current
    val trailCardColor = if (colors.isDark) colors.surface2 else colors.surface1
    val showPremiumPreview = state.entry == null && state.adventure == null && state.error == "feature_disabled"
    CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
        LazyColumn(Modifier.fillMaxSize().background(colors.surfaceApp).safeDrawingPadding(),
            contentPadding = PaddingValues(Spacing.x6), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.trail_back)) } }
        item { Text(stringResource(R.string.adventure_title), style = MaterialTheme.typography.headlineLarge) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (showPremiumPreview) item { AdventurePremiumPreview(onPremium) }
        else state.error?.let { error -> item { AdventureError(error, viewModel::refresh, onConnect, !state.busy) } }
        if (state.entry != null && state.adventure == null) item {
            AdventurePreview(state, viewModel::start, onPremium)
        }
        state.adventure?.let { adventure ->
            item { AdventureProgress(adventure) }
            if (adventure.completed && adventure.earnedFinish) item { CompletionShare(adventure) }
            if (!adventure.completed) item {
                ContributionControls(adventure, !state.busy, viewModel::sync, viewModel::pause, viewModel::finish)
            }
            if (adventure.participation?.active == true && !adventure.earnedFinish) item {
                CommitmentCard(adventure, !state.busy, viewModel::commit)
            }
        }
        if (state.entry != null) {
            item { Text(stringResource(R.string.adventure_chapter_list_title), style = MaterialTheme.typography.titleLarge) }
            items(chapterTitles.size) { index ->
                val adventure = state.adventure
                val earned = index < (adventure?.chapters ?: 0)
                OutlinedCard(onClick = { chapter = index }, enabled = earned, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = trailCardColor, contentColor = colors.textPrimary),
                    border = BorderStroke(1.dp, colors.borderDefault)) {
                    Column(Modifier.padding(Spacing.x4)) {
                        Text(stringResource(chapterTitles[index]), style = MaterialTheme.typography.titleMedium)
                        if (!earned && adventure != null) Text(stringResource(R.string.trail_locked,
                            localizedNumber((adventure.target * (index + 1) + 4) / 5)))
                    }
                }
            }
        }
            if (!showPremiumPreview && state.error == null && state.entry != null) item {
                TextButton(onClick = viewModel::refresh, enabled = !state.busy) {
                    Text(stringResource(R.string.trail_refresh))
                }
            }
        }
    }
    chapter?.takeIf { it < (state.adventure?.chapters ?: 0) }?.let { index ->
        AlertDialog(onDismissRequest = { chapter = null }, title = { Text(stringResource(chapterTitles[index])) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                item { TrailArtwork(index) }
                item { Text(stringResource(chapterStories[index])) }
                item { Text(stringResource(chapterReflections[index]), style = MaterialTheme.typography.titleMedium) }
            } }, confirmButton = { TextButton(onClick = { chapter = null }) { Text(stringResource(R.string.trail_back)) } })
    }
}

@Composable
private fun AdventurePremiumPreview(onPremium: () -> Unit) {
    val colors = LocalPamojaColors.current
    val trailCardColor = if (colors.isDark) colors.surface2 else colors.surface1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = trailCardColor, contentColor = colors.textPrimary)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4)
        ) {
            Text(stringResource(R.string.premium_trail_title), style = MaterialTheme.typography.titleLarge)
            TrailArtwork(0)
            Text(stringResource(R.string.adventure_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.adventure_included), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.adventure_access_body), color = colors.textSecondary)
            Button(onClick = onPremium, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.trail_sponsor))
            }
        }
    }
}

@Composable
private fun CompletionShare(adventure: Adventure) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(stringResource(R.string.trail_share_privacy), style = MaterialTheme.typography.bodySmall,
            color = LocalPamojaColors.current.textSecondary)
        Button(enabled = !preparing, onClick = {
            preparing = true
            failed = false
            scope.launch {
                try {
                    val share = prepareTrailCompletionShare(context, adventure)
                    shareLauncher.launch(Intent.createChooser(share, context.getString(R.string.trail_share)))
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { failed = true
                } finally { preparing = false }
            }
        }) { Text(stringResource(R.string.trail_share)) }
        if (failed) Text(stringResource(R.string.trail_share_failed), color = LocalPamojaColors.current.textSecondary)
    }
}

@Composable
private fun AdventurePreview(state: AdventureUiState, start: (Long) -> Unit, premium: () -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val displayedTarget = state.pendingTarget?.toString() ?: target
    val amount = displayedTarget.toLongOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text(stringResource(R.string.adventure_promise), style = MaterialTheme.typography.titleLarge)
        TrailArtwork(0)
        Text(stringResource(R.string.adventure_intro))
        Text(stringResource(R.string.adventure_included))
        Text(stringResource(R.string.adventure_access_body))
        if (state.entry?.covered == false) {
            Button(onClick = premium, enabled = !state.busy) { Text(stringResource(R.string.trail_sponsor)) }
        } else if (state.entry?.organizer == true) {
            OutlinedTextField(value = displayedTarget, onValueChange = { target = it.filter(Char::isDigit).take(8) },
                enabled = !state.busy && state.pendingTarget == null,
                label = { Text(stringResource(R.string.adventure_goal)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.trail_target_range, localizedNumber(1_000), localizedNumber(11_200_000)))
            Button(onClick = { confirm = true }, enabled = !state.busy && amount != null && amount in 1_000..11_200_000) {
                Text(stringResource(R.string.trail_start))
            }
        } else Text(stringResource(R.string.trail_wait_organizer))
    }
    if (confirm && amount != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(localizedNumber(amount)) }, text = { Text(stringResource(R.string.trail_start_body)) },
        confirmButton = { TextButton(onClick = { confirm = false; start(amount) }) { Text(stringResource(R.string.trail_start)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.trail_cancel)) } })
}

@Composable
private fun AdventureProgress(adventure: Adventure) {
    val colors = LocalPamojaColors.current
    val trailCardColor = if (colors.isDark) colors.surface2 else colors.surface1
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = trailCardColor, contentColor = colors.textPrimary)) {
        TrailArtwork((adventure.chapters - 1).coerceIn(0, chapterArt.lastIndex))
        Column(Modifier.padding(Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            if (adventure.chapters > 0) Text(stringResource(chapterTitles[(adventure.chapters - 1).coerceAtMost(chapterTitles.lastIndex)]),
                style = MaterialTheme.typography.titleMedium)
            if (adventure.earnedFinish) {
                Text(stringResource(R.string.adventure_completion_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.adventure_completion_body))
            }
            Text(stringResource(R.string.trail_progress, localizedNumber(adventure.steps), localizedNumber(adventure.target)),
                style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(progress = { (adventure.steps.toFloat() / adventure.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            if (!adventure.earnedFinish) Text(adventure.approximateWeeks?.let {
                stringResource(R.string.trail_pace, localizedNumber(it))
            } ?: stringResource(R.string.trail_no_pace))
        }
    }
}

@Composable
private fun ContributionControls(adventure: Adventure, enabled: Boolean, sync: () -> Unit, pause: () -> Unit, finish: () -> Unit) {
    var pausing by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        if (adventure.earnedFinish) {
            Button(onClick = finish, enabled = enabled) { Text(stringResource(R.string.trail_finish)) }
        } else {
            Text(stringResource(R.string.adventure_starting_sync))
            Button(onClick = sync, enabled = enabled) {
                Text(stringResource(if (adventure.participation?.active == true) R.string.trail_sync else R.string.trail_join))
            }
            if (adventure.participation?.active == true) TextButton(onClick = { pausing = true }, enabled = enabled) {
                Text(stringResource(R.string.trail_pause))
            }
        }
        adventure.participation?.let { person ->
            val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(person.lastSyncAt))
            Text(stringResource(R.string.trail_last_sync, date), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pausing) AlertDialog(onDismissRequest = { pausing = false }, title = { Text(stringResource(R.string.trail_pause)) },
        text = { Text(stringResource(R.string.trail_pause_body)) },
        confirmButton = { TextButton(onClick = { pausing = false; pause() }) { Text(stringResource(R.string.trail_confirm)) } },
        dismissButton = { TextButton(onClick = { pausing = false }) { Text(stringResource(R.string.trail_cancel)) } })
}

@Composable
private fun CommitmentCard(adventure: Adventure, enabled: Boolean, commit: (Long) -> Unit) {
    var value by rememberSaveable(adventure.nextWeek, adventure.participation?.revision) {
        mutableStateOf(adventure.participation?.commitment?.toString().orEmpty())
    }
    val number = value.toLongOrNull()
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.parse(adventure.nextWeek).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val colors = LocalPamojaColors.current
    val trailCardColor = if (colors.isDark) colors.surface2 else colors.surface1
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(
        containerColor = trailCardColor, contentColor = colors.textPrimary),
        border = BorderStroke(1.dp, colors.borderDefault)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            Text(stringResource(R.string.trail_week, date, adventure.timeZone), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.trail_commitment)) }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(stringResource(R.string.trail_commitment_range, localizedNumber(0), localizedNumber(1_400_000)),
                style = MaterialTheme.typography.bodySmall)
            adventure.participation?.commitment?.let { Text(stringResource(R.string.trail_saved_commitment, localizedNumber(it))) }
            Button(onClick = { number?.let(commit) }, enabled = enabled && number != null && number in 0..1_400_000) {
                Text(stringResource(R.string.trail_save_commitment))
            }
            TextButton(onClick = { commit(0) }, enabled = enabled) { Text(stringResource(R.string.trail_rest)) }
            Text(stringResource(R.string.trail_rest_body))
        }
    }
}

@Composable
private fun AdventureError(reason: String, refresh: () -> Unit, connect: () -> Unit, enabled: Boolean) {
    val message = when (reason) {
        "health_unavailable" -> R.string.trail_health_error
        "device_bound_elsewhere" -> R.string.trail_device_error
        "feature_disabled" -> R.string.trail_disabled
        "week_changed", "commitment_changed" -> R.string.trail_stale
        "access_denied", "signed_out" -> R.string.trail_access_error
        else -> R.string.trail_error
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(Spacing.x4)) {
            Text(stringResource(message))
            TextButton(onClick = refresh, enabled = enabled) { Text(stringResource(R.string.trail_refresh)) }
            if (reason == "health_unavailable") TextButton(onClick = connect, enabled = enabled) { Text(stringResource(R.string.trail_connect)) }
        }
    }
}

@Composable
private fun localizedNumber(number: Long): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(number)

/** Fictional scenery, independent of the real progress bar and chapter thresholds. */
@Composable
private fun TrailArtwork(index: Int) {
    Image(painter = painterResource(chapterArt[index]), contentDescription = stringResource(chapterDescriptions[index]),
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(MaterialTheme.shapes.medium))
}
