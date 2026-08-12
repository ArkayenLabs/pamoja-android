package com.pamoja.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.ui.auth.OTP_LENGTH
import com.pamoja.app.ui.auth.OtpBoxes
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.toSnackbarMessage
import com.pamoja.app.ui.profile.ProfileAvatar
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onEditProfile: () -> Unit,
    onNotificationSettings: () -> Unit,
    onAccount: () -> Unit,
    onLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Used inside click handlers and coroutine scopes.
    val userIdClipLabel = stringResource(R.string.settings_user_id_clip)
    val userIdCopiedMessage = stringResource(R.string.settings_user_id_copied)

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            onSignedOut()
        }
    }

    // Two channels: failures that came back from a repository, and messages this
    // screen raised itself as a resource id.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.toSnackbarMessage(context))
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.messageRes, uiState.messageArg) {
        val res = uiState.messageRes ?: return@LaunchedEffect
        val message = uiState.messageArg
            ?.let { context.getString(res, it) }
            ?: context.getString(res)
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessages()
    }

    // Reload on return, so a name changed in the editor is reflected here rather
    // than showing the value this screen loaded before navigating away.
    LaunchedEffect(Unit) { viewModel.refresh() }

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

    // ── Dialogs ──────────────────────────────────────────────────────────────
    //
    // The rename dialog that used to live here is gone, replaced by the profile
    // editor. It built a User from just the id and the new name and handed it to
    // updateUser, which writes the whole document, so renaming yourself silently
    // erased your age, height and weight.

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = colors.surface3,
            shape = RoundedCornerShape(PamojaRadii.xl),
            title = {
                Text(
                    text = stringResource(R.string.settings_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.statusDanger
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount()
                        showDeleteConfirmDialog = false
                    },
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusDanger)
                ) {
                    Text(stringResource(R.string.settings_delete_confirm), color = colors.textOnBrand, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            }
        )
    }

    // Firebase refuses to delete an account on a stale session. Sessions here
    // last indefinitely, so this is the normal path rather than an edge case.
    uiState.reauthRequired?.let { method ->
        var password by remember { mutableStateOf("") }
        var otpCode by remember { mutableStateOf("") }
        val otpFocusRequester = remember { FocusRequester() }

        // Phone runs in two steps inside one dialog: send the SMS, then enter the
        // code. This flag is what tells them apart.
        val awaitingCode = uiState.reauthVerificationId != null

        LaunchedEffect(awaitingCode) {
            if (awaitingCode) otpFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { viewModel.cancelReauth() },
            containerColor = colors.surface3,
            shape = RoundedCornerShape(PamojaRadii.xl),
            title = {
                Text(
                    text = stringResource(R.string.settings_reauth_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = when {
                            method == ReauthMethod.Phone && awaitingCode ->
                                stringResource(R.string.settings_reauth_code_body, uiState.reauthPhoneNumber.orEmpty())

                            method == ReauthMethod.Phone ->
                                stringResource(R.string.settings_reauth_phone_body)

                            else ->
                                stringResource(R.string.settings_reauth_body)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )

                    if (method == ReauthMethod.Password) {
                        Spacer(modifier = Modifier.height(Spacing.x4))
                        PamojaTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.password_label),
                            placeholder = stringResource(R.string.password_placeholder_existing),
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                        )
                    }

                    if (method == ReauthMethod.Phone && awaitingCode) {
                        Spacer(modifier = Modifier.height(Spacing.x4))
                        OtpBoxes(
                            code = otpCode,
                            onCodeChange = { otpCode = it },
                            hasError = uiState.error != null,
                            focusRequester = otpFocusRequester,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (method) {
                            ReauthMethod.Google -> activity?.let(viewModel::reauthenticateWithGoogle)
                            ReauthMethod.Password -> viewModel.reauthenticateWithPassword(password)
                            ReauthMethod.Phone ->
                                if (awaitingCode) {
                                    viewModel.reauthenticateWithPhone(otpCode)
                                } else {
                                    activity?.let(viewModel::sendReauthCode)
                                }
                        }
                    },
                    enabled = !uiState.isLoading && when (method) {
                        ReauthMethod.Google -> true
                        ReauthMethod.Password -> password.isNotBlank()
                        ReauthMethod.Phone -> !awaitingCode || otpCode.length == OTP_LENGTH
                    },
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text(
                        text = when {
                            method == ReauthMethod.Google -> stringResource(R.string.auth_continue_google)
                            method == ReauthMethod.Phone && !awaitingCode -> stringResource(R.string.settings_reauth_send_code)
                            else -> stringResource(R.string.common_confirm)
                        },
                        color = colors.textOnBrand,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelReauth() }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            }
        )
    }

    // ── Main UI Structure ────────────────────────────────────────────────────

    Scaffold(
        containerColor = colors.surfaceApp,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.x2, vertical = Spacing.x2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.settings_back_desc),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.x2))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentPrimary,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(Spacing.x4))

                // ─── Section: Profile ────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_profile), color = colors.textTertiary)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6)
                        .clip(RoundedCornerShape(PamojaRadii.md))
                        .background(colors.surface1)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                        .padding(Spacing.x4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.x4)
                ) {
                    // Profile header. The whole row is the target, not just the
                    // pencil, because a 18dp icon is well under the 48dp minimum
                    // and the row is what reads as tappable.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PamojaRadii.sm))
                            .clickable(onClick = onEditProfile)
                            .padding(vertical = Spacing.x1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(
                            name = uiState.userName.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.settings_name_fallback),
                            size = 48.dp,
                        )

                        Spacer(modifier = Modifier.width(Spacing.x4))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.userName.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.settings_name_fallback),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                // Shows the details the app already holds. They
                                // were collected at onboarding and then never
                                // displayed anywhere, which is hard to justify.
                                text = profileSummary(uiState.age, uiState.height, uiState.weight),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }

                        Icon(
                            painter = painterResource(PamojaIcons.Edit),
                            contentDescription = stringResource(R.string.settings_open_profile),
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // User ID Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_user_id),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.userId,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(userIdClipLabel, uiState.userId)
                                clipboard.setPrimaryClip(clip)
                                scope.launch {
                                    snackbarHostState.showSnackbar(userIdCopiedMessage)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(PamojaIcons.Copy),
                                contentDescription = stringResource(R.string.settings_copy_user_id),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x6))

                // ─── Section: Account ────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_account), color = colors.textTertiary)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6)
                        .clip(RoundedCornerShape(PamojaRadii.md))
                        .background(colors.surface1)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                ) {
                    SettingsRow(
                        icon = PamojaIcons.Shield,
                        title = stringResource(R.string.settings_account_title),
                        // Names the methods rather than saying "manage", so an
                        // account with only one way in says so from here without
                        // having to be opened.
                        subtitle = authMethodsSummary(uiState.authMethods),
                        onClick = onAccount,
                    )
                    SettingsRow(
                        icon = PamojaIcons.Star,
                        title = stringResource(R.string.settings_subscription_title),
                        subtitle = stringResource(R.string.settings_subscription_sub),
                        onClick = {
                            // Play owns the subscription, so cancelling has to
                            // happen there. Required by policy, and it has to be
                            // easy to find rather than buried: burying it is the
                            // dark pattern that gets apps removed.
                            //
                            // The general subscription centre until there are
                            // products to name. Once they exist this should carry
                            // ?sku=<productId>&package=<packageName> so it lands
                            // on Pamoja's own subscription rather than a list.
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://play.google.com/store/account/subscriptions".toUri(),
                                    )
                                )
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x6))

                // ─── Section: Preferences ───────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_preferences), color = colors.textTertiary)

                ThemeSelector(
                    selected = uiState.theme,
                    onSelect = viewModel::setTheme,
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6)
                        .clip(RoundedCornerShape(PamojaRadii.md))
                        .background(colors.surface1)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                ) {
                    SettingsRow(
                        icon = PamojaIcons.Bell,
                        title = stringResource(R.string.settings_notifications_title),
                        subtitle = stringResource(R.string.settings_notifications_sub),
                        onClick = onNotificationSettings,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x6))

                // ─── Section: Health ────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_health), color = colors.textTertiary)

                HealthSection(
                    status = uiState.healthStatus,
                    lastSyncTime = uiState.lastSyncTime,
                    isSyncing = uiState.isSyncing,
                    onSyncNow = viewModel::syncNow,
                    onFix = {
                        // Health Connect owns the permission, so the fix is over
                        // there. Falls back to the Play listing when the app is
                        // absent, which is the Android 13-and-below case.
                        val intent = if (uiState.healthStatus == HealthStatus.Unavailable) {
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
                            )
                        } else {
                            Intent(HEALTH_CONNECT_SETTINGS_ACTION)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
                                )
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(Spacing.x6))

                // ─── Section: App Settings & Info ───────────────────────────
                SectionLabel(stringResource(R.string.settings_section_information), color = colors.textTertiary)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6)
                        .clip(RoundedCornerShape(PamojaRadii.md))
                        .background(colors.surface1)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                ) {
                    SettingsRow(
                        icon = PamojaIcons.ShieldCheck,
                        title = stringResource(R.string.settings_privacy_title),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arkayenlabs.com/privacy/pamoja"))
                            context.startActivity(intent)
                        }
                    )
                    SettingsRow(
                        icon = PamojaIcons.Info,
                        title = stringResource(R.string.settings_terms_title),
                        subtitle = stringResource(R.string.settings_terms_subtitle),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arkayenlabs.com/terms/pamoja"))
                            context.startActivity(intent)
                        }
                    )
                    SettingsRow(
                        icon = PamojaIcons.Info,
                        title = stringResource(R.string.settings_licenses_title),
                        subtitle = stringResource(R.string.settings_licenses_subtitle),
                        onClick = onLicenses,
                    )
                    SettingsRow(
                        icon = PamojaIcons.Star,
                        title = stringResource(R.string.settings_rate_title),
                        subtitle = stringResource(R.string.settings_rate_subtitle),
                        onClick = {
                            val packageName = context.packageName
                            val intent = try {
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                            } catch (e: Exception) {
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.x6))

                // ─── Section: Danger Zone ────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_account_actions), color = colors.statusDanger)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6)
                        .clip(RoundedCornerShape(PamojaRadii.md))
                        .background(colors.surface1)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                ) {
                    SettingsRow(
                        icon = PamojaIcons.LogOut,
                        title = stringResource(R.string.settings_logout_title),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        iconColor = colors.textSecondary,
                        onClick = { viewModel.signOut() }
                    )
                    SettingsRow(
                        icon = PamojaIcons.Trash,
                        title = stringResource(R.string.settings_delete_title),
                        subtitle = stringResource(R.string.settings_delete_subtitle),
                        iconColor = colors.statusDanger,
                        onClick = { showDeleteConfirmDialog = true }
                    )
                }

                // ─── Debug tools, never present in a release build ───────────
                if (com.pamoja.app.BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(Spacing.x8))

                    SectionLabel(stringResource(R.string.settings_debug), color = colors.textTertiary)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.x6)
                            .clip(RoundedCornerShape(PamojaRadii.md))
                            .background(colors.surface1)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                    ) {
                        SettingsRow(
                            icon = PamojaIcons.Notification,
                            title = stringResource(R.string.settings_debug_notifications_title),
                            subtitle = stringResource(R.string.settings_debug_notifications_subtitle),
                            iconColor = colors.textSecondary,
                            onClick = { viewModel.sendDebugNotifications() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.x12))

                // App version signature
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.x6),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.Info),
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text = stringResource(R.string.settings_version, "1.0.0"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(Spacing.x8)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = Spacing.x6, vertical = Spacing.x2)
    )
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    iconColor: Color? = null,
    onClick: () -> Unit
) {
    val colors = LocalPamojaColors.current
    val resolvedIconColor = iconColor ?: colors.accentPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(PamojaRadii.xs))
                .background(resolvedIconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = resolvedIconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Icon(
            painter = painterResource(PamojaIcons.ChevronRight),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * The one-line summary under the name in the profile header.
 *
 * Falls back to an invitation rather than an empty line, since a blank subtitle
 * under a tappable row gives no reason to tap it.
 */
@Composable
private fun profileSummary(age: Int?, height: Float?, weight: Float?): String {
    val parts = buildList {
        age?.let { add(stringResource(R.string.profile_detail_age, it)) }
        height?.let { add(stringResource(R.string.profile_detail_height, it.toInt())) }
        weight?.let { add(stringResource(R.string.profile_detail_weight, it.toInt())) }
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.profile_details_empty)
    } else {
        parts.joinToString(stringResource(R.string.profile_detail_separator))
    }
}

/**
 * The one-line summary on the Account row.
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
 * System / Light / Dark, as a segmented control.
 *
 * A segmented control rather than a dialog because there are exactly three
 * options and the result is visible instantly behind the control: choosing Dark
 * and watching the sheet recolour underneath is the confirmation, so a dialog
 * would only get in the way of the feedback.
 */
@Composable
private fun ThemeSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x4),
    ) {
        Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(Spacing.x3))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PamojaRadii.sm))
                .background(colors.surfaceSunken)
                .padding(Spacing.x1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
        ) {
            ThemePreference.entries.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(PamojaRadii.sm))
                        .background(if (isSelected) colors.accentPrimary else Color.Transparent)
                        .clickable { onSelect(option) }
                        // 44dp plus the row padding clears the 48dp target.
                        .padding(vertical = Spacing.x3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(option.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        // Selection is carried by weight as well as colour, so
                        // it survives being read without colour perception.
                        color = if (isSelected) colors.textOnBrand else colors.textSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.x3))

        Text(
            text = stringResource(
                if (selected == ThemePreference.System) R.string.settings_theme_system_sub
                else R.string.settings_theme_fixed_sub
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@StringRes
private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.System -> R.string.settings_theme_system
    ThemePreference.Light -> R.string.settings_theme_light
    ThemePreference.Dark -> R.string.settings_theme_dark
}

/** Health Connect's package, for the settings deep link and the store fallback. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val HEALTH_CONNECT_SETTINGS_ACTION =
    "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"

/**
 * Whether steps are actually reaching us, and how to fix it when they are not.
 *
 * This is the single most predictable support question for the app: after
 * onboarding there was previously no way to see whether Health Connect was
 * still connected, and revoking it in system settings left the group quietly
 * seeing zero with nothing on screen to explain why.
 */
@Composable
private fun HealthSection(
    status: HealthStatus,
    lastSyncTime: Long,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onFix: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)

    val (title, subtitle) = when (status) {
        HealthStatus.Connected ->
            R.string.settings_health_connected to R.string.settings_health_connected_sub
        HealthStatus.NotConnected ->
            R.string.settings_health_not_connected to R.string.settings_health_not_connected_sub
        HealthStatus.Unavailable ->
            R.string.settings_health_unavailable to R.string.settings_health_unavailable_sub
    }

    val accent = when (status) {
        HealthStatus.Connected -> colors.statusSuccess
        HealthStatus.NotConnected -> colors.statusWarning
        HealthStatus.Unavailable -> colors.textTertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6)
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(Spacing.x4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A dot as well as the wording, so the state is not carried by
            // colour alone.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(PamojaRadii.xs))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(Spacing.x3))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.x2))

        Text(
            text = stringResource(subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Spacing.x4))

        Text(
            text = lastSyncedLabel(lastSyncTime),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )

        Spacer(modifier = Modifier.height(Spacing.x3))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            // Syncing is pointless without permission, so the primary action
            // becomes the thing that would actually help.
            if (status == HealthStatus.Connected) {
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimarySubtle,
                        contentColor = colors.accentPrimary,
                        disabledContainerColor = colors.surfaceSunken,
                        disabledContentColor = colors.textTertiary,
                    ),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = colors.textTertiary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(Spacing.x2))
                    }
                    Text(
                        text = stringResource(
                            if (isSyncing) R.string.settings_health_syncing
                            else R.string.settings_health_sync_now
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Button(
                    onClick = onFix,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.textOnBrand,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (status == HealthStatus.Unavailable) R.string.settings_health_install
                            else R.string.settings_health_fix
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
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
