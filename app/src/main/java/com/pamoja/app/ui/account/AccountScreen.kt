package com.pamoja.app.ui.account

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.ui.auth.Country
import com.pamoja.app.ui.auth.OTP_LENGTH
import com.pamoja.app.ui.auth.OtpBoxes
import com.pamoja.app.ui.auth.authErrorBody
import com.pamoja.app.ui.components.DisabledReason
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.rememberSingleClick
import androidx.compose.ui.text.style.TextOverflow
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Which ways back into this account exist, and adding another.
 *
 * Its own screen rather than a section of Settings because three of the four
 * actions here open a form, and one of them is a two-step SMS verification.
 *
 * The framing throughout is deliberate: adding a method never replaces anything
 * and never touches the groups or history, and the copy says so, because the
 * fear this screen has to overcome is that signing in a second way will somehow
 * start a second account.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    LaunchedEffect(uiState.messageRes) {
        val res = uiState.messageRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(res))
        viewModel.clearMessage()
    }

    when (uiState.activeDialog) {
        AccountFlow.AddEmail -> AddEmailDialog(
            state = uiState,
            onConfirm = viewModel::addEmail,
            onDismiss = viewModel::dismissDialog,
        )

        AccountFlow.AddPhone -> AddPhoneDialog(
            state = uiState,
            onSendCode = { e164 -> activity?.let { viewModel.sendPhoneCode(e164, it) } },
            onConfirmCode = viewModel::confirmPhoneCode,
            onDismiss = viewModel::dismissDialog,
        )

        AccountFlow.ChangePassword -> ChangePasswordDialog(
            state = uiState,
            onConfirm = viewModel::changePassword,
            onDismiss = viewModel::dismissDialog,
        )

        // Google needs no form, and nothing opens a dialog for it.
        AccountFlow.AddGoogle, null -> Unit
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
            OfflineBanner(isOffline = uiState.isOffline)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.x4, vertical = Spacing.x2),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.account_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
            }

            when {
                // Nothing loaded, so the failure is the screen.
                uiState.loadError != null -> PamojaErrorState(
                    error = uiState.loadError!!,
                    onRetry = { viewModel.load() },
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                uiState.isLoading -> AccountSkeleton()

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.account_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.x6),
                    )

                    Spacer(modifier = Modifier.height(Spacing.x5))

                    // How exposed this account is, stated before the list rather
                    // than left to be inferred from it. One method is the state
                    // every account starts in, and nothing has ever said what
                    // that costs if the phone is lost.
                    val warning = when (uiState.methodCount) {
                        0 -> R.string.account_none_title to R.string.account_none_body
                        1 -> R.string.account_single_title to R.string.account_single_body
                        else -> null
                    }
                    warning?.let { (title, body) ->
                        Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {
                            PamojaNotice(
                                icon = PamojaIcons.Shield,
                                title = stringResource(title),
                                body = stringResource(body),
                                tone = if (uiState.methodCount == 0) NoticeTone.Danger
                                else NoticeTone.Warning,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.x5))
                    }

                    SectionLabel(stringResource(R.string.account_section_methods))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.x6)
                            .clip(RoundedCornerShape(PamojaRadii.xl))
                            .background(colors.surface1)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.xl))
                            .padding(vertical = Spacing.x1)
                    ) {
                        MethodRow(
                            icon = PamojaIcons.Google,
                            isGoogleMark = true,
                            title = stringResource(R.string.account_method_google),
                            value = uiState.methods.email.takeIf { uiState.methods.hasGoogle },
                            isConnected = uiState.methods.hasGoogle,
                            isBusy = uiState.busyWith == AccountFlow.AddGoogle,
                            enabled = uiState.canLink,
                            onAdd = { activity?.let(viewModel::addGoogle) },
                        )
                        MethodDivider()
                        MethodRow(
                            icon = PamojaIcons.Smartphone,
                            title = stringResource(R.string.account_method_phone),
                            value = uiState.methods.phoneNumber.takeIf { uiState.methods.hasPhone },
                            isConnected = uiState.methods.hasPhone,
                            isBusy = false,
                            enabled = uiState.canLink,
                            onAdd = { viewModel.openDialog(AccountFlow.AddPhone) },
                        )
                        MethodDivider()
                        MethodRow(
                            icon = PamojaIcons.Mail,
                            title = stringResource(R.string.account_method_email),
                            value = uiState.methods.email.takeIf { uiState.methods.hasEmail },
                            isConnected = uiState.methods.hasEmail,
                            isBusy = false,
                            enabled = uiState.canLink,
                            onAdd = { viewModel.openDialog(AccountFlow.AddEmail) },
                        )
                    }

                    // Google is the one flow with no dialog to fail inside, so
                    // its failure is drawn here, under the row it belongs to.
                    if (uiState.errorFrom == AccountFlow.AddGoogle) {
                        Spacer(modifier = Modifier.height(Spacing.x3))
                        Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {
                            PamojaNotice(
                                icon = PamojaIcons.AlertCircle,
                                title = stringResource(R.string.account_add_failed_title),
                                body = uiState.error!!.accountErrorBody(AccountFlow.AddGoogle),
                                tone = NoticeTone.Danger,
                            )
                        }
                    }

                    if (uiState.isOffline) {
                        Spacer(modifier = Modifier.height(Spacing.x3))
                        DisabledReason(
                            text = stringResource(R.string.account_offline_note),
                            modifier = Modifier.padding(horizontal = Spacing.x6),
                        )
                    }

                    // Only an account with a password has one to change. On a
                    // Google or phone account the row would be a dead end.
                    if (uiState.methods.hasEmail) {
                        Spacer(modifier = Modifier.height(Spacing.x6))

                        SectionLabel(stringResource(R.string.account_section_password))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.x6)
                                .clip(RoundedCornerShape(PamojaRadii.md))
                                .background(colors.surface1)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(PamojaRadii.md))
                                .clickable(enabled = uiState.canLink) {
                                    viewModel.openDialog(AccountFlow.ChangePassword)
                                }
                                .padding(horizontal = Spacing.x4, vertical = Spacing.x4),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(PamojaRadii.xs))
                                        .background(colors.accentPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(PamojaIcons.Lock),
                                        contentDescription = null,
                                        tint = colors.accentPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.x3))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.account_change_password),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.account_change_password_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                Icon(
                                    painter = painterResource(PamojaIcons.ChevronRight),
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(Spacing.x10)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalPamojaColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(
            start = Spacing.x6, end = Spacing.x6, bottom = Spacing.x2
        ),
    )
}

/**
 * One sign-in method, connected or not.
 *
 * A connected row is deliberately inert: there is nothing useful to do to it.
 * Unlinking is not offered, because removing the last method would lock the
 * account permanently and the guard rails that would make it safe are more
 * work than the feature is worth today.
 */
/**
 * Hairline between method rows, inset past the icon chip.
 *
 * Full-width would cut the chips off from their own text; starting it where the
 * text starts is what makes each row read as one object.
 */
@Composable
private fun MethodDivider() {
    val colors = LocalPamojaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(Layout.strokeHairline)
            .background(colors.borderSubtle)
    )
}

@Composable
private fun MethodRow(
    @DrawableRes icon: Int,
    title: String,
    value: String?,
    isConnected: Boolean,
    isBusy: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    isGoogleMark: Boolean = false,
) {
    val colors = LocalPamojaColors.current
    val accent = if (isConnected) colors.statusSuccess else colors.accentPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnected && enabled && !isBusy, onClick = onAdd)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The design's 44dp chip. A connected method carries the jade tint; an
        // unconnected one stays neutral, so the list reads as "what I have"
        // before any word on it has been read.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(PamojaRadii.md))
                .background(
                    if (isConnected) colors.statusSuccessSubtle else colors.surface2
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                // Google's mark is multi-colour and its brand rules forbid
                // recolouring it, so it alone goes untinted.
                tint = when {
                    isGoogleMark -> Color.Unspecified
                    isConnected -> colors.statusSuccess
                    else -> colors.textSecondary
                },
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value ?: stringResource(
                    if (isConnected) R.string.account_connected
                    else R.string.account_not_connected
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(Spacing.x3))

        when {
            isBusy -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.accentPrimary,
                strokeWidth = 2.dp,
            )

            // A tick as well as the word, so the state is not carried by colour
            // alone.
            isConnected -> Icon(
                painter = painterResource(PamojaIcons.ShieldCheck),
                contentDescription = stringResource(R.string.account_connected),
                tint = colors.statusSuccess,
                modifier = Modifier.size(20.dp),
            )

            else -> Text(
                text = stringResource(R.string.account_add),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.accentPrimary else colors.textTertiary,
            )
        }
    }
}

/** Matches the real layout: a notice, then three method rows. */
@Composable
private fun AccountSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.9f), height = 14.dp)

        Spacer(modifier = Modifier.height(Spacing.x6))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 72.dp)

        Spacer(modifier = Modifier.height(Spacing.x6))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.3f), height = 12.dp)
        Spacer(modifier = Modifier.height(Spacing.x3))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 200.dp)
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

/**
 * The shell every flow on this screen shares.
 *
 * Keeps the failure notice in one place, directly above the button that raised
 * it, rather than each dialog inventing its own placement.
 */
@Composable
private fun AccountDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    isBusy: Boolean,
    error: AppError?,
    flow: AccountFlow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * False once dismissing would throw away something that cost money, namely
     * a verification code already sent. Back still closes it; only the stray
     * tap on the scrim is refused.
     */
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalPamojaColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
        containerColor = colors.surface3,
        shape = RoundedCornerShape(PamojaRadii.xl),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(Spacing.x4))

                content()

                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.x4))
                    PamojaNotice(
                        icon = PamojaIcons.AlertCircle,
                        title = stringResource(R.string.account_add_failed_title),
                        body = error.accountErrorBody(flow),
                        tone = NoticeTone.Danger,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                // Guarded because this same button sends the SMS in the
                // add-phone flow, and two taps inside the recomposition gap
                // would be two paid messages.
                onClick = rememberSingleClick(onClick = onConfirm),
                enabled = confirmEnabled && !isBusy,
                shape = RoundedCornerShape(PamojaRadii.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.textTertiary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                }
                Text(text = confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun AddEmailDialog(
    state: AccountUiState,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Prefilled from the account where there is one, which is the Google case:
    // the address is already known and retyping it is a chance to get it wrong.
    var email by remember { mutableStateOf(state.methods.email.orEmpty()) }
    var password by remember { mutableStateOf("") }

    // Hoisted, since validate runs on focus change rather than in composition.
    val emailRequired = stringResource(R.string.email_required)
    val emailInvalid = stringResource(R.string.email_invalid)
    val passwordTooShort = stringResource(R.string.password_too_short)

    // The same check the sign-up screen makes, so an address rejected there
    // cannot be accepted here. Patterns is an Android class, which the domain
    // may not touch but the UI layer may.
    val emailErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> emailRequired
            !android.util.Patterns.EMAIL_ADDRESS.matcher(input.trim()).matches() -> emailInvalid
            else -> null
        }
    }
    // Same rule the field shows and the button asks, so a password the field
    // flags cannot still be submitted.
    val passwordErrorFor: (String) -> String? = { input ->
        if (input.length < MIN_NEW_PASSWORD) passwordTooShort else null
    }

    AccountDialog(
        title = stringResource(R.string.account_add_email_title),
        body = stringResource(R.string.account_add_email_body),
        confirmLabel = stringResource(R.string.account_add),
        confirmEnabled = emailErrorFor(email) == null && passwordErrorFor(password) == null,
        isBusy = state.busyWith == AccountFlow.AddEmail,
        error = state.error.takeIf { state.errorFrom == AccountFlow.AddEmail },
        flow = AccountFlow.AddEmail,
        onConfirm = { onConfirm(email, password) },
        onDismiss = onDismiss,
    ) {
        PamojaTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.email_label),
            placeholder = stringResource(R.string.email_placeholder),
            keyboardType = KeyboardType.Email,
            enabled = state.busyWith == null,
            validate = emailErrorFor,
        )

        Spacer(modifier = Modifier.height(Spacing.x3))

        PamojaTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.password_label),
            placeholder = stringResource(R.string.password_placeholder_new),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            enabled = state.busyWith == null,
            validate = passwordErrorFor,
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    state: AccountUiState,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }

    val passwordTooShort = stringResource(R.string.password_too_short)
    val newPasswordErrorFor: (String) -> String? = { input ->
        if (input.length < MIN_NEW_PASSWORD) passwordTooShort else null
    }

    AccountDialog(
        title = stringResource(R.string.account_change_password),
        body = stringResource(R.string.account_change_password_body),
        confirmLabel = stringResource(R.string.account_change_password_confirm),
        confirmEnabled = current.isNotBlank() && newPasswordErrorFor(new) == null,
        isBusy = state.busyWith == AccountFlow.ChangePassword,
        error = state.error.takeIf { state.errorFrom == AccountFlow.ChangePassword },
        flow = AccountFlow.ChangePassword,
        onConfirm = { onConfirm(current, new) },
        onDismiss = onDismiss,
    ) {
        PamojaTextField(
            value = current,
            onValueChange = { current = it },
            label = stringResource(R.string.account_password_current),
            placeholder = stringResource(R.string.password_placeholder_existing),
            keyboardType = KeyboardType.Password,
            isPassword = true,
            enabled = state.busyWith == null,
        )

        Spacer(modifier = Modifier.height(Spacing.x3))

        PamojaTextField(
            value = new,
            onValueChange = { new = it },
            label = stringResource(R.string.account_password_new),
            placeholder = stringResource(R.string.password_placeholder_new),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            enabled = state.busyWith == null,
            validate = newPasswordErrorFor,
        )
    }
}

/**
 * Number, then code, in one dialog.
 *
 * The country list replaces the dialog's own body rather than opening a bottom
 * sheet on top of it, so there is never a second window over this one competing
 * for the keyboard.
 */
@Composable
private fun AddPhoneDialog(
    state: AccountUiState,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPamojaColors.current

    var country by remember { mutableStateOf(Country.fromLocale()) }
    var number by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pickingCountry by remember { mutableStateOf(false) }

    val awaitingCode = state.verificationId != null
    val isBusy = state.busyWith == AccountFlow.AddPhone
    val digits = number.filter { it.isDigit() }

    val otpFocusRequester = remember { FocusRequester() }
    LaunchedEffect(awaitingCode) {
        if (awaitingCode) otpFocusRequester.requestFocus()
    }

    if (pickingCountry) {
        CountryPickerDialog(
            selected = country,
            onSelect = {
                country = it
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
        return
    }

    AccountDialog(
        title = stringResource(R.string.account_add_phone_title),
        body = if (awaitingCode) {
            stringResource(R.string.account_phone_code_body, state.pendingPhoneNumber.orEmpty())
        } else {
            stringResource(R.string.account_add_phone_body)
        },
        confirmLabel = stringResource(
            if (awaitingCode) R.string.common_confirm else R.string.phone_send_code
        ),
        // Gated on a country-appropriate digit count, because every send costs
        // a real message and a half-typed number would simply waste one.
        confirmEnabled = if (awaitingCode) {
            code.length == OTP_LENGTH
        } else {
            digits.length in country.nationalDigits
        },
        isBusy = isBusy,
        error = state.error.takeIf { state.errorFrom == AccountFlow.AddPhone },
        flow = AccountFlow.AddPhone,
        onConfirm = {
            if (awaitingCode) onConfirmCode(code) else onSendCode(country.dialCode + digits)
        },
        onDismiss = onDismiss,
        // A code has been paid for and sent, so a mistap on the scrim must not
        // be what throws it away.
        dismissOnClickOutside = !awaitingCode,
    ) {
        if (awaitingCode) {
            OtpBoxes(
                code = code,
                // Frozen while the code is being checked, matching the sign-in
                // OTP screen: editing mid-verification changes what is on
                // screen without changing what is being verified.
                onCodeChange = { if (!isBusy) code = it },
                hasError = state.errorFrom == AccountFlow.AddPhone,
                focusRequester = otpFocusRequester,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ISO code and dial code, never a flag: emoji are banned product
                // wide and this reads correctly to a screen reader.
                // wrapContentWidth, and fillMaxHeight rather than fillMaxSize.
                // A Row is measured before its weighted siblings and is handed
                // the full width, so fillMaxSize here made this chip swallow the
                // row and squeezed the number field to nothing: its label
                // wrapped one letter per line and every tap landed on the
                // country picker, leaving no way to type a number at all.
                Surface(
                    onClick = { pickingCountry = true },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    color = colors.surfaceInput,
                    modifier = Modifier
                        .height(56.dp)
                        .wrapContentWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = colors.borderDefault,
                                shape = RoundedCornerShape(PamojaRadii.sm),
                            )
                            .fillMaxHeight()
                            .padding(horizontal = Spacing.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = country.dialCode,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }

                PamojaTextField(
                    value = number,
                    // Capped to the country's own longest valid length, same
                    // reason as the sign-in phone screen: the field must not
                    // accept more digits than the country could ever need.
                    onValueChange = {
                        number = it.filter { char -> char.isDigit() }
                            .take(country.nationalDigits.last)
                    },
                    label = stringResource(R.string.phone_label),
                    placeholder = stringResource(R.string.phone_placeholder),
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The country list, as a dialog rather than the bottom sheet the sign-in screen
 * uses, so it stays inside the dialog window the add-phone flow already owns.
 */
@Composable
private fun CountryPickerDialog(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Country.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface3,
        shape = RoundedCornerShape(PamojaRadii.xl),
        title = {
            Text(
                text = stringResource(R.string.phone_select_country),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
        },
        text = {
            Column {
                PamojaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.phone_label_country),
                    placeholder = stringResource(R.string.phone_search_country),
                    imeAction = ImeAction.Search,
                )

                Spacer(modifier = Modifier.height(Spacing.x3))

                // Bounded, because a LazyColumn inside a dialog with no height
                // constraint measures to zero and renders nothing.
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results, key = { it.isoCode }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .padding(vertical = Spacing.x3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = country.dialCode,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            if (country.isoCode == selected.isoCode) {
                                Spacer(modifier = Modifier.width(Spacing.x3))
                                Icon(
                                    painter = painterResource(PamojaIcons.Check),
                                    contentDescription = stringResource(R.string.common_selected),
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_back), color = colors.textSecondary)
            }
        },
    )
}

/**
 * Account-flavoured wording for the shared error taxonomy.
 *
 * A collision means something different in each flow, and the sign-in phrasing
 * is wrong for all of them: "that email is already registered" is nonsense when
 * what happened is that a Google account belongs to somebody else's Pamoja
 * account. Only the phrasing is specialised; the classification stays shared.
 */
@Composable
private fun Throwable.accountErrorBody(flow: AccountFlow): String {
    val specialised = when (toAppError()) {
        is AppError.Conflict -> when (flow) {
            AccountFlow.AddGoogle -> R.string.account_conflict_google
            AccountFlow.AddPhone -> R.string.account_conflict_phone
            AccountFlow.AddEmail -> R.string.account_conflict_email
            AccountFlow.ChangePassword -> null
        }

        is AppError.InvalidCredentials -> when (flow) {
            AccountFlow.ChangePassword -> R.string.account_password_wrong_current
            AccountFlow.AddPhone -> R.string.account_phone_wrong_code
            else -> null
        }

        else -> null
    }

    return specialised?.let { stringResource(it) } ?: authErrorBody()
}

/**
 * What a new password must clear, matching the sign-up screen.
 *
 * The use case enforces a lower floor, since that is the rule Firebase itself
 * imposes; this is the stricter thing the UI asks for.
 */
private const val MIN_NEW_PASSWORD = 8
