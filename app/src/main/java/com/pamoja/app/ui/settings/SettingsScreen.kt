package com.pamoja.app.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.BuildConfig
import com.pamoja.app.R
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.ui.auth.OTP_LENGTH
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CardShape
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSheet
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.OptionRow
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.kit.mutedContentColor
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.widgets.GroupProgressWidgetProvider
import com.pamoja.app.widgets.TodayStepsWidgetProvider
import com.pamoja.app.widgets.WalkHookWidgetProvider
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onEditProfile: () -> Unit,
    onNotificationSettings: () -> Unit,
    /** Past updates. Their bell used to be on the dashboard, which is gone. */
    onActivity: () -> Unit,
    onAccount: () -> Unit,
    onLicenses: () -> Unit,
    /**
     * Set when the profile editor closed after saving.
     *
     * The editor leaves on save rather than staying put, so the confirmation
     * has to land here. Consumed once by the caller, so it does not fire again
     * on every recomposition or when returning from a different screen.
     */
    profileWasSaved: Boolean = false,
    onProfileSavedShown: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onBackgroundPermissionResult() }

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) onSignedOut()
    }

    // Two channels: failures that came back from a repository, and messages this
    // screen raised itself as a resource id.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.showSnackbar(it.toSnackbarMessage(context))
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.messageRes, uiState.messageArg) {
        val res = uiState.messageRes ?: return@LaunchedEffect
        val message = uiState.messageArg
            ?.let { context.getString(res, it) }
            ?: context.getString(res)
        snackbar.showSnackbar(message)
        viewModel.clearMessages()
    }

    // The third channel: the profile editor closed after saving. Acknowledged
    // here because that screen no longer exists by the time there is anything
    // to say.
    LaunchedEffect(profileWasSaved) {
        if (profileWasSaved) {
            onProfileSavedShown()
            snackbar.showSnackbar(context.getString(R.string.profile_edit_saved))
        }
    }

    // Reload on return, so a name changed in the editor is reflected here rather
    // than showing the value this screen loaded before navigating away.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // ── Export ───────────────────────────────────────────────────────────────
    //
    // The system document picker rather than a file written into app storage.
    // No storage permission, no FileProvider, and the file lands somewhere the
    // user chose and can find again instead of a directory they cannot see.
    val exportSavedMessage = stringResource(R.string.settings_export_saved)
    val exportFailedMessage = stringResource(R.string.settings_export_failed)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = uiState.exportJson
        viewModel.clearExport()
        // A null uri means the picker was dismissed, which is a choice and not
        // a failure worth announcing.
        if (uri == null || json == null) return@rememberLauncherForActivityResult

        val wrote = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray())
            } ?: error("Could not open the chosen file for writing")
        }.isSuccess

        scope.launch {
            snackbar.showSnackbar(if (wrote) exportSavedMessage else exportFailedMessage)
        }
    }

    // Opens the picker only once the data is actually in hand, so the user is
    // never asked where to save something that then fails to arrive.
    LaunchedEffect(uiState.exportJson) {
        if (uiState.exportJson != null) createDocumentLauncher.launch(EXPORT_FILE_NAME)
    }

    // Health Connect permission can be revoked in system settings while the app
    // is alive, so the status is re-read whenever this screen resumes rather
    // than trusted from construction.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealthStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val widgetUnavailable = stringResource(R.string.settings_widgets_unavailable)

    SettingsContent(
        state = uiState,
        snackbar = snackbar,
        language = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        onBack = onBack,
        onEditProfile = onEditProfile,
        onAccount = onAccount,
        onSubscription = {
            // Play owns the subscription, so cancelling has to happen there.
            // Required by policy, and it has to be easy to find rather than
            // buried: burying it is the dark pattern that gets apps removed.
            context.openUri(
                "https://play.google.com/store/account/subscriptions" +
                    "?sku=pamoja_circle_v1&package=com.arkayenlabs.pamoja",
            )
        },
        onTheme = viewModel::setTheme,
        onLanguage = { tag -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
        onNotifications = onNotificationSettings,
        onActivity = onActivity,
        onPinWidget = { provider ->
            if (!requestWidgetPin(context, provider)) scope.launch { snackbar.showSnackbar(widgetUnavailable) }
        },
        onSyncNow = viewModel::syncNow,
        onFixHealth = {
            // Health Connect owns the permission, so the fix is over there.
            // Falls back to the Play listing when the app is absent, which is
            // the Android 13-and-below case.
            val intent = if (uiState.healthStatus == HealthStatus.Unavailable) {
                Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE".toUri())
            } else {
                Intent(HEALTH_CONNECT_SETTINGS_ACTION)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                context.openUri("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
            }
        },
        onBackgroundSteps = {
            if (uiState.backgroundReadGranted) {
                runCatching { context.startActivity(Intent(HEALTH_CONNECT_SETTINGS_ACTION)) }
            } else {
                backgroundPermissionLauncher.launch(
                    com.pamoja.app.data.local.health.HealthConnectReader.BACKGROUND_PERMISSIONS,
                )
            }
        },
        onSupport = { context.openSupportMail(uiState.userId) },
        onExport = { if (!uiState.isExporting) viewModel.exportData() },
        onPrivacy = { context.openUri("https://www.arkayenlabs.com/privacy/pamoja") },
        onTerms = { context.openUri("https://www.arkayenlabs.com/terms/pamoja") },
        onLicenses = onLicenses.takeIf { BuildConfig.DEBUG },
        onRate = {
            val packageName = context.packageName
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
            }.onFailure { context.openUri("https://play.google.com/store/apps/details?id=$packageName") }
        },
        onSignOut = viewModel::signOut,
        onDeleteAccount = { viewModel.deleteAccount() },
        onReauthGoogle = { activity?.let(viewModel::reauthenticateWithGoogle) },
        onReauthPassword = viewModel::reauthenticateWithPassword,
        onSendReauthCode = { activity?.let(viewModel::sendReauthCode) },
        onReauthPhone = viewModel::reauthenticateWithPhone,
        onCancelReauth = viewModel::cancelReauth,
    )
}

/** The languages the app is translated into, as each one names itself. */
private val LANGUAGES = listOf(
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "es" to "Español",
    "hi" to "हिन्दी",
)

/** Stateless rendering boundary, driven by the debug preview. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    snackbar: SnackbarHostState,
    language: String,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onAccount: () -> Unit,
    onSubscription: () -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onLanguage: (String) -> Unit,
    onNotifications: () -> Unit,
    onActivity: () -> Unit,
    onPinWidget: (Class<out android.appwidget.AppWidgetProvider>) -> Unit,
    onSyncNow: () -> Unit,
    onFixHealth: () -> Unit,
    onBackgroundSteps: () -> Unit,
    onSupport: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    /** Null hides the row: licences are only generated for release builds. */
    onLicenses: (() -> Unit)?,
    onRate: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onReauthGoogle: () -> Unit,
    onReauthPassword: (String) -> Unit,
    onSendReauthCode: () -> Unit,
    onReauthPhone: (String) -> Unit,
    onCancelReauth: () -> Unit,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var widgetSheet by rememberSaveable { mutableStateOf(false) }
    var languageSheet by rememberSaveable { mutableStateOf(false) }
    val name = state.userName.ifBlank { stringResource(R.string.settings_name_fallback) }
    val systemLanguage = stringResource(R.string.settings_language_system)
    val languageLabel = LANGUAGES.firstOrNull { it.first == language }?.second ?: systemLanguage

    Box(Modifier.fillMaxSize()) {
        KitScreen(
            gutter = 20.dp,
            top = {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            },
        ) {
            // You first: the face and name your groups see, and the way to
            // change them.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable(role = Role.Button, onClick = onEditProfile)
                    .padding(vertical = 4.dp),
            ) {
                Avatar(name = name, photoUrl = state.photoUrl, size = 64.dp, seed = state.userId.ifBlank { name })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        name,
                        style = Kit.type.headline.copy(fontSize = 28.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.settings_open_profile), style = Kit.type.caption, color = Kit.colors.muted)
                }
                Icon(
                    painter = painterResource(PamojaIcons.ChevronRight),
                    contentDescription = null,
                    tint = Kit.colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (state.isLoading) {
                KitSkeleton(height = 140.dp)
                KitSkeleton(height = 200.dp)
                KitSkeleton(height = 160.dp)
                return@KitScreen
            }

            KitCard(spacing = 0.dp) {
                CardHeader(stringResource(R.string.settings_section_account), Modifier.padding(bottom = 6.dp))
                SettingsRow(
                    label = stringResource(R.string.settings_account_title),
                    // Names the methods rather than saying "manage", so an
                    // account with only one way in says so from here without
                    // having to be opened.
                    detail = authMethodsSummary(state.authMethods),
                    onClick = onAccount,
                )
                SettingsRow(
                    label = stringResource(R.string.settings_subscription_title),
                    onClick = onSubscription,
                    showDivider = false,
                )
            }

            HealthCard(
                status = state.healthStatus,
                lastSyncTime = state.lastSyncTime,
                isSyncing = state.isSyncing,
                showBackground = state.healthStatus == HealthStatus.Connected && state.backgroundReadSupported,
                backgroundGranted = state.backgroundReadGranted,
                onSyncNow = onSyncNow,
                onFix = onFixHealth,
                onBackground = onBackgroundSteps,
            )

            KitCard(spacing = 0.dp) {
                CardHeader(stringResource(R.string.settings_section_preferences), Modifier.padding(bottom = 10.dp))
                ThemeChoice(selected = state.theme, onSelect = onTheme)
                Text(
                    stringResource(
                        if (state.theme == ThemePreference.System) R.string.settings_theme_system_sub
                        else R.string.settings_theme_fixed_sub,
                    ),
                    style = Kit.type.caption,
                    color = Kit.colors.muted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                SettingsRow(
                    label = stringResource(R.string.settings_language),
                    value = languageLabel,
                    onClick = { languageSheet = true },
                    showDivider = false,
                )
            }

            KitCard(spacing = 0.dp) {
                SettingsRow(stringResource(R.string.settings_notifications_title), onClick = onNotifications)
                SettingsRow(stringResource(R.string.activity_title), onClick = onActivity)
                SettingsRow(
                    stringResource(R.string.settings_widgets_title),
                    onClick = { widgetSheet = true },
                    showDivider = false,
                )
            }

            KitCard(spacing = 0.dp) {
                CardHeader(stringResource(R.string.settings_section_support), Modifier.padding(bottom = 6.dp))
                SettingsRow(stringResource(R.string.settings_support_title), onClick = onSupport)
                SettingsRow(
                    label = stringResource(R.string.settings_export_title),
                    detail = stringResource(
                        if (state.isExporting) R.string.settings_export_working else R.string.settings_export_subtitle,
                    ),
                    onClick = onExport,
                    showDivider = false,
                )
            }

            KitCard(spacing = 0.dp) {
                CardHeader(stringResource(R.string.settings_section_information), Modifier.padding(bottom = 6.dp))
                SettingsRow(stringResource(R.string.settings_privacy_title), onClick = onPrivacy)
                SettingsRow(stringResource(R.string.settings_terms_title), onClick = onTerms)
                onLicenses?.let { SettingsRow(stringResource(R.string.settings_licenses_title), onClick = it) }
                SettingsRow(stringResource(R.string.settings_rate_title), onClick = onRate, showDivider = false)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryButton(
                    stringResource(R.string.settings_logout_title),
                    icon = PamojaIcons.LogOut,
                    onClick = { confirmSignOut = true },
                )
                QuietButton(
                    stringResource(R.string.settings_delete_title),
                    danger = true,
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                // Read from the build, never typed here: a hardcoded "1.0.0"
                // once claimed that on every release.
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = Kit.type.caption,
                color = Kit.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (languageSheet) {
        KitSheet(onDismiss = { languageSheet = false }, title = stringResource(R.string.settings_language)) { close ->
            Column(Modifier.selectableGroup()) {
                (listOf("" to systemLanguage) + LANGUAGES).forEach { (tag, label) ->
                    OptionRow(
                        text = label,
                        selected = tag == language,
                        onClick = { close { onLanguage(tag) } },
                    )
                }
            }
        }
    }

    if (widgetSheet) {
        KitSheet(onDismiss = { widgetSheet = false }, title = stringResource(R.string.settings_widgets_picker_title)) { close ->
            SettingsRow(
                label = stringResource(R.string.widget_group_name),
                detail = stringResource(R.string.settings_widgets_group_subtitle),
                onClick = { close { onPinWidget(GroupProgressWidgetProvider::class.java) } },
            )
            SettingsRow(
                label = stringResource(R.string.widget_today_name),
                detail = stringResource(R.string.settings_widgets_today_subtitle),
                onClick = { close { onPinWidget(TodayStepsWidgetProvider::class.java) } },
            )
            SettingsRow(
                label = stringResource(R.string.widget_hook_name),
                detail = stringResource(R.string.settings_widgets_hook_subtitle),
                onClick = { close { onPinWidget(WalkHookWidgetProvider::class.java) } },
                showDivider = false,
            )
        }
    }

    if (confirmSignOut) {
        KitDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            body = stringResource(R.string.settings_logout_confirm_body),
            confirmText = stringResource(R.string.settings_logout_confirm_action),
            dismissText = stringResource(R.string.common_cancel),
            // Signing out loses nothing, so it is not painted as danger. The
            // dialog exists to catch the misplaced tap, not to warn.
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmDelete = false
                onDeleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    // Firebase refuses to delete an account on a stale session. Sessions here
    // last indefinitely, so this is the normal path rather than an edge case.
    state.reauthRequired?.let { method ->
        ReauthDialog(
            method = method,
            phoneNumber = state.reauthPhoneNumber,
            awaitingCode = state.reauthVerificationId != null,
            busy = state.isLoading,
            hasError = state.error != null,
            onGoogle = onReauthGoogle,
            onPassword = onReauthPassword,
            onSendCode = onSendReauthCode,
            onCode = onReauthPhone,
            onCancel = onCancelReauth,
        )
    }
}

/**
 * A row in a settings card: a label, an optional quiet line under it, an
 * optional value on the right, and a chevron. The kit's ListRow, plus the
 * second line a few settings need (sign-in methods, export progress).
 */
@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    value: String? = null,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold))
                detail?.let { Text(it, style = Kit.type.caption, color = mutedContentColor()) }
            }
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
                    .background(Kit.colors.line),
            )
        }
    }
}

/**
 * System / Light / Dark as three pills. A segmented control rather than a
 * sheet because there are exactly three and the result shows instantly
 * behind the control: the screen recolouring is the confirmation.
 */
@Composable
private fun ThemeChoice(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    val colors = Kit.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.track)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        ThemePreference.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.button else colors.track)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    // 44dp high with the track's padding clears the 48dp target.
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(option.labelRes()),
                    style = Kit.type.buttonSmall,
                    // Selection is carried by the fill as well as the text
                    // colour, so it survives being read without colour.
                    color = if (isSelected) colors.onButton else colors.ink,
                )
            }
        }
    }
}

@StringRes
private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.System -> R.string.settings_theme_system
    ThemePreference.Light -> R.string.settings_theme_light
    ThemePreference.Dark -> R.string.settings_theme_dark
}

/**
 * Whether steps are actually reaching us, and how to fix it when they are not.
 *
 * This is the single most predictable support question for the app: after
 * onboarding there was previously no way to see whether Health Connect was
 * still connected, and revoking it in system settings left the group quietly
 * seeing zero with nothing on screen to explain why.
 */
@Composable
private fun HealthCard(
    status: HealthStatus,
    lastSyncTime: Long,
    isSyncing: Boolean,
    showBackground: Boolean,
    backgroundGranted: Boolean,
    onSyncNow: () -> Unit,
    onFix: () -> Unit,
    onBackground: () -> Unit,
) {
    val colors = Kit.colors
    val (title, body) = when (status) {
        HealthStatus.Connected -> R.string.settings_health_connected to R.string.settings_health_connected_sub
        HealthStatus.NotConnected -> R.string.settings_health_not_connected to R.string.settings_health_not_connected_sub
        HealthStatus.Unavailable -> R.string.settings_health_unavailable to R.string.settings_health_unavailable_sub
    }
    KitCard {
        CardHeader(stringResource(R.string.settings_section_health), detail = lastSyncedLabel(lastSyncTime))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // A dot as well as the wording, so the state is not carried by
            // colour alone. Lime is the kit's one bright colour: all is well.
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            HealthStatus.Connected -> colors.pop
                            HealthStatus.NotConnected -> colors.danger
                            HealthStatus.Unavailable -> colors.muted
                        },
                    ),
            )
            Text(stringResource(title), style = Kit.type.bodyStrong)
        }
        Text(stringResource(body), style = Kit.type.caption, color = colors.muted)
        // Syncing is pointless without permission, so the button becomes the
        // thing that would actually help.
        if (status == HealthStatus.Connected) {
            SecondaryButton(
                stringResource(if (isSyncing) R.string.settings_health_syncing else R.string.settings_health_sync_now),
                onClick = onSyncNow,
                loading = isSyncing,
                size = ButtonSize.Small,
            )
        } else {
            PrimaryButton(
                stringResource(
                    if (status == HealthStatus.Unavailable) R.string.settings_health_install else R.string.settings_health_fix,
                ),
                onClick = onFix,
                size = ButtonSize.Small,
            )
        }
        if (showBackground) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )
            SettingsRow(
                label = stringResource(R.string.settings_background_steps_title),
                detail = stringResource(
                    if (backgroundGranted) R.string.settings_background_steps_on else R.string.settings_background_steps_off,
                ),
                onClick = onBackground,
                showDivider = false,
            )
        }
    }
}

/**
 * Deleting the account: what goes, then the word typed to unlock the button.
 * Lists exactly what deleteAllUserData removes, no more. It deletes
 * memberships and step entries and decrements member counts; it does not
 * delete groups this user administers, so nothing here claims it does.
 */
@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.settings_delete_type_word)
    KitDialog(
        title = stringResource(R.string.settings_delete_title),
        body = stringResource(R.string.settings_delete_warning),
        confirmText = stringResource(R.string.settings_delete_confirm),
        dismissText = stringResource(R.string.common_cancel),
        danger = true,
        confirmEnabled = typed.trim().equals(word, ignoreCase = true),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.settings_delete_loses_profile,
                R.string.settings_delete_loses_history,
                R.string.settings_delete_loses_groups,
            ).forEach { res ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Kit.colors.danger),
                    )
                    Text(stringResource(res), style = Kit.type.body)
                }
            }
        }
        KitTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.settings_delete_type_hint),
            placeholder = word,
        )
    }
}

/**
 * Proving it is you before the account goes. Google opens its own sheet;
 * a password is typed here; a phone number runs in two steps, sending the
 * code and then entering it.
 */
@Composable
private fun ReauthDialog(
    method: ReauthMethod,
    phoneNumber: String?,
    awaitingCode: Boolean,
    busy: Boolean,
    hasError: Boolean,
    onGoogle: () -> Unit,
    onPassword: (String) -> Unit,
    onSendCode: () -> Unit,
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    KitDialog(
        title = stringResource(R.string.settings_reauth_title),
        body = when {
            method == ReauthMethod.Phone && awaitingCode ->
                stringResource(R.string.settings_reauth_code_body, phoneNumber.orEmpty())
            method == ReauthMethod.Phone -> stringResource(R.string.settings_reauth_phone_body)
            else -> stringResource(R.string.settings_reauth_body)
        },
        confirmText = when {
            method == ReauthMethod.Google -> stringResource(R.string.auth_continue_google)
            method == ReauthMethod.Phone && !awaitingCode -> stringResource(R.string.settings_reauth_send_code)
            else -> stringResource(R.string.common_confirm)
        },
        dismissText = stringResource(R.string.common_cancel),
        loading = busy,
        confirmEnabled = when (method) {
            ReauthMethod.Google -> true
            ReauthMethod.Password -> password.isNotBlank()
            ReauthMethod.Phone -> !awaitingCode || code.length == OTP_LENGTH
        },
        onConfirm = {
            when (method) {
                ReauthMethod.Google -> onGoogle()
                ReauthMethod.Password -> onPassword(password)
                ReauthMethod.Phone -> if (awaitingCode) onCode(code) else onSendCode()
            }
        },
        onDismiss = onCancel,
    ) {
        if (method == ReauthMethod.Password) {
            KitTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password_label),
                placeholder = stringResource(R.string.password_placeholder_existing),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        if (method == ReauthMethod.Phone && awaitingCode) {
            KitTextField(
                value = code,
                onValueChange = { new -> code = new.filter { it.isDigit() }.take(OTP_LENGTH) },
                label = stringResource(R.string.otp_title),
                error = if (hasError) stringResource(R.string.settings_code_wrong) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }
    }
}

/**
 * The one-line summary on the sign-in methods row.
 *
 * Written to be read at a glance from Settings: an account with a single method
 * is one lost phone away from losing every group, so that case says what to do
 * rather than merely listing what is connected.
 */
@Composable
private fun authMethodsSummary(methods: AuthMethods): String {
    val names = buildList {
        if (methods.hasGoogle) add(stringResource(R.string.account_method_google))
        if (methods.hasPhone) add(stringResource(R.string.account_method_phone))
        if (methods.hasEmail) add(stringResource(R.string.account_method_email))
    }
    return when (names.size) {
        0 -> stringResource(R.string.settings_account_none)
        1 -> stringResource(R.string.settings_account_one, names.first())
        else -> stringResource(
            R.string.settings_account_many,
            names.joinToString(stringResource(R.string.account_method_separator)),
        )
    }
}

/**
 * "Synced 12 min ago", at the coarsest unit that is still honest.
 *
 * Anything under a minute is "moments ago" rather than a second count, because
 * a number ticking once a second reads as something being wrong.
 */
@Composable
private fun lastSyncedLabel(lastSyncTime: Long): String {
    if (lastSyncTime <= 0L) return stringResource(R.string.settings_health_never_synced)

    val elapsed = System.currentTimeMillis() - lastSyncTime
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> stringResource(R.string.settings_health_synced_moments)
        minutes < 60 -> stringResource(R.string.settings_health_synced_minutes, minutes.toInt())
        hours < 24 -> stringResource(R.string.settings_health_synced_hours, hours.toInt())
        else -> stringResource(R.string.settings_health_synced_days, days.toInt())
    }
}

private fun Context.openUri(uri: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
}

/**
 * Device, Android version and app version are prefilled because the first
 * reply to any support mail is otherwise a request for exactly those, which
 * costs a day for no reason. ACTION_SENDTO with a mailto URI resolves only to
 * mail apps; a phone with none configured does nothing rather than crashing.
 */
private fun Context.openSupportMail(userId: String) {
    val body = getString(
        R.string.settings_support_body,
        android.os.Build.MANUFACTURER,
        android.os.Build.MODEL,
        android.os.Build.VERSION.RELEASE,
        BuildConfig.VERSION_NAME,
        userId.ifBlank { "-" },
    )
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_support_subject))
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { startActivity(intent) }
}

private fun requestWidgetPin(
    context: Context,
    provider: Class<out android.appwidget.AppWidgetProvider>,
): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
        ComponentName(context, provider),
        null,
        null,
    )
}

/** Support address, also published on the site and required by Play. */
private const val SUPPORT_EMAIL = "support@arkayenlabs.com"

/** Suggested name in the save dialog. The user can change it. */
private const val EXPORT_FILE_NAME = "pamoja-my-data.json"

/** Health Connect's package, for the settings deep link and the store fallback. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val HEALTH_CONNECT_SETTINGS_ACTION = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
