package com.pamoja.app.ui.adventure

import android.os.Bundle
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.pamoja.app.widgets.PamojaWidgetRenderer
import com.pamoja.app.widgets.PamojaWidgetUpdater
import java.time.LocalDate
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pamoja.app.domain.model.*
import com.pamoja.app.domain.repository.AdventureRepository
import com.pamoja.app.ui.theme.PamojaTheme
import java.time.Instant

/** Separate offline package. Actual screen and ViewModel, explicitly sample data. */
class AdventurePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Preview-only overrides: never change the user's device language/font settings.
        val language = intent.getStringExtra("language")
            ?.takeIf { it in setOf("en-US", "en-GB", "es", "hi") }
        val fontScale = intent.getStringExtra("fontScale")?.toFloatOrNull()?.coerceIn(1f, 2f)
        super.onCreate(savedInstanceState)
        val previewConfiguration = Configuration(resources.configuration).apply {
            language?.let { setLocales(LocaleList(Locale.forLanguageTag(it))) }
            fontScale?.let { this.fontScale = it }
        }
        val previewContext = android.view.ContextThemeWrapper(this, com.pamoja.app.R.style.Theme_Pamoja).apply {
            applyOverrideConfiguration(previewConfiguration)
        }
        val sample = SampleAdventure(intent.getStringExtra("scenario") ?: "progress")
        val vm = AdventureViewModel(sample, SavedStateHandle(mapOf("groupId" to "sample")), true)
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            CompositionLocalProvider(LocalContext provides previewContext,
                LocalConfiguration provides previewConfiguration,
                LocalDensity provides Density(LocalDensity.current.density, previewConfiguration.fontScale)) {
            PamojaTheme(if (dark) ThemePreference.Dark else ThemePreference.Light) {
                Surface {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Text("OFFLINE PREVIEW · SAMPLE DATA", color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.weight(1f)) {
                            if (intent.getStringExtra("scenario") == "widgets") {
                                PamojaWidgetUpdater(previewContext).publish(5669, 34010, listOf(Group(
                                    groupId = "sample", name = "Family walkers", weeklyTarget = 70000,
                                    weeklySteps = 52480, weekStart = LocalDate.now()
                                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString(),
                                    memberCount = 4,
                                )))
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    AndroidView(factory = { PamojaWidgetRenderer.todayViews(previewContext).apply(previewContext, null) },
                                        modifier = Modifier.width(220.dp).height(230.dp))
                                    AndroidView(factory = { PamojaWidgetRenderer.groupViews(previewContext).apply(previewContext, null) },
                                        modifier = Modifier.fillMaxWidth().height(230.dp))
                                    AndroidView(factory = { PamojaWidgetRenderer.hookViews(previewContext).apply(previewContext, null) },
                                        modifier = Modifier.fillMaxWidth().height(180.dp))
                                }
                            } else AdventureScreen(onBack = { finish() }, onPremium = {}, onConnect = {}, viewModel = vm)
                        }
                    }
                }
            }
            }
        }
    }
}

private class SampleAdventure(scenario: String) : AdventureRepository {
    private var exists = scenario != "preview"
    private var value = Adventure("sample", 200000, if (scenario == "complete") 200000 else 82000,
        if (scenario == "complete") 5 else 2, false, scenario == "complete", "2026-09-21", "Europe/London",
        30000, 4, 0, if (scenario == "join") null else AdventureParticipation(true,
            Instant.parse("2026-09-19T12:00:00Z").toEpochMilli(), 20000, 1))
    override suspend fun entry(groupId: String) = AdventureEntry(true, true, if (exists) "sample" else null)
    override suspend fun read(groupId: String, adventureId: String) = value
    override suspend fun start(groupId: String, requestId: String, target: Long, timeZone: String) {
        exists = true
        value = value.copy(target = target, steps = 0, chapters = 0, participation = null)
    }
    override suspend fun sync(groupId: String, adventureId: String, join: Boolean) {
        value = value.copy(participation = AdventureParticipation(true, System.currentTimeMillis(), null, 0))
    }
    override suspend fun pause(groupId: String, adventureId: String) {
        value = value.copy(participation = value.participation?.copy(active = false))
    }
    override suspend fun commit(groupId: String, adventure: Adventure, steps: Long) {
        value = value.copy(participation = value.participation?.copy(commitment = steps, revision = (value.participation?.revision ?: 0) + 1))
    }
    override suspend fun finish(groupId: String, adventureId: String) { value = value.copy(completed = true) }
}
