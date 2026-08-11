package com.pamoja.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.auth.OTP_LENGTH
import com.pamoja.app.ui.auth.OtpBoxes
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Used inside click handlers and coroutine scopes.
    val userIdClipLabel = stringResource(R.string.settings_user_id_clip)
    val userIdCopiedMessage = stringResource(R.string.settings_user_id_copied)

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            onSignedOut()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Initialize edit dialog field with current name when opening
    LaunchedEffect(showEditNameDialog) {
        if (showEditNameDialog) {
            editNameInput = uiState.userName
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = colors.surface3,
            shape = RoundedCornerShape(PamojaRadii.xl),
            title = {
                Text(
                    text = stringResource(R.string.settings_edit_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_name_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentPrimary,
                        unfocusedBorderColor = colors.borderDefault,
                        focusedContainerColor = colors.surfaceInput,
                        unfocusedContainerColor = colors.surfaceInput,
                        cursorColor = colors.accentPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserName(editNameInput.trim())
                        showEditNameDialog = false
                    },
                    enabled = editNameInput.isNotBlank(),
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text(stringResource(R.string.settings_save), color = colors.textOnBrand, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            }
        )
    }

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
                    // Display Name Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_name_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.userName.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_name_fallback),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary
                            )
                        }
                        IconButton(onClick = { showEditNameDialog = true }) {
                            Icon(
                                painter = painterResource(PamojaIcons.Edit),
                                contentDescription = stringResource(R.string.settings_edit_name),
                                tint = colors.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
