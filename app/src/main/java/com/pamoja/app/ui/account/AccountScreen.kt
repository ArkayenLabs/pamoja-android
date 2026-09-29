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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.PasswordPolicy
import com.pamoja.app.ui.auth.Country
import com.pamoja.app.ui.auth.OTP_LENGTH
import com.pamoja.app.ui.auth.OtpBoxes
import com.pamoja.app.ui.auth.authErrorBody
import com.pamoja.app.ui.components.rememberSingleClick
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CardHeader
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.FieldShape
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitDialog
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitNotice
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.ListRow
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    AccountContent(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::load,
        onAddGoogle = { activity?.let(viewModel::addGoogle) },
        onOpen = viewModel::openDialog,
        onDismiss = viewModel::dismissDialog,
        onAddEmail = viewModel::addEmail,
        onChangePassword = viewModel::changePassword,
        onSendPhoneCode = { e164 -> activity?.let { viewModel.sendPhoneCode(e164, it) } },
        onConfirmPhoneCode = viewModel::confirmPhoneCode,
        onMessageShown = viewModel::clearMessage,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun AccountContent(
    state: AccountUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddGoogle: () -> Unit,
    onOpen: (AccountFlow) -> Unit,
    onDismiss: () -> Unit,
    onAddEmail: (String, String) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onSendPhoneCode: (String) -> Unit,
    onConfirmPhoneCode: (String) -> Unit,
    onMessageShown: () -> Unit,
) = KitInAppTheme {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(res))
        onMessageShown()
    }

    Box(Modifier.fillMaxSize()) {
        KitScreen(
            gutter = 20.dp,
            top = {
                TopRow(start = {
                    CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
                })
            },
        ) {
            Text(stringResource(R.string.account_title), style = Kit.type.headline)

            val loadError = state.loadError
            when {
                // Nothing loaded, so the failure is the screen.
                loadError != null -> {
                    val copy = loadError.toErrorCopy()
                    KitNotice(title = stringResource(copy.title), body = copy.body(context), danger = true)
                    PrimaryButton(stringResource(R.string.common_try_again), onClick = onRetry)
                }

                state.isLoading -> {
                    KitSkeleton(height = 80.dp)
                    KitSkeleton(height = 220.dp)
                }

                else -> {
                    Text(stringResource(R.string.account_subtitle), style = Kit.type.body, color = Kit.colors.muted)

                    // How exposed this account is, stated before the list rather
                    // than left to be inferred from it. One method is the state
                    // every account starts in, and nothing has ever said what
                    // that costs if the phone is lost.
                    when (state.methodCount) {
                        0 -> KitNotice(
                            title = stringResource(R.string.account_none_title),
                            body = stringResource(R.string.account_none_body),
                            danger = true,
                        )
                        1 -> KitNotice(
                            title = stringResource(R.string.account_single_title),
                            body = stringResource(R.string.account_single_body),
                        )
                    }

                    if (state.isOffline) {
                        KitNotice(
                            title = stringResource(R.string.offline_banner_title),
                            body = stringResource(R.string.account_offline_note),
                        )
                    }

                    KitCard(spacing = 0.dp) {
                        CardHeader(stringResource(R.string.account_section_methods), Modifier.padding(bottom = 6.dp))
                        MethodRow(
                            icon = PamojaIcons.Google,
                            isGoogleMark = true,
                            title = stringResource(R.string.account_method_google),
                            value = state.methods.email.takeIf { state.methods.hasGoogle },
                            isConnected = state.methods.hasGoogle,
                            isBusy = state.busyWith == AccountFlow.AddGoogle,
                            enabled = state.canLink,
                            onAdd = onAddGoogle,
                        )
                        MethodRow(
                            icon = PamojaIcons.Smartphone,
                            title = stringResource(R.string.account_method_phone),
                            value = state.methods.phoneNumber.takeIf { state.methods.hasPhone },
                            isConnected = state.methods.hasPhone,
                            isBusy = false,
                            enabled = state.canLink,
                            onAdd = { onOpen(AccountFlow.AddPhone) },
                        )
                        MethodRow(
                            icon = PamojaIcons.Mail,
                            title = stringResource(R.string.account_method_email),
                            value = state.methods.email.takeIf { state.methods.hasEmail },
                            isConnected = state.methods.hasEmail,
                            isBusy = false,
                            enabled = state.canLink,
                            onAdd = { onOpen(AccountFlow.AddEmail) },
                            showDivider = false,
                        )
                    }

                    // Google is the one flow with no dialog to fail inside, so
                    // its failure is drawn here, under the list it belongs to.
                    state.error?.takeIf { state.errorFrom == AccountFlow.AddGoogle }?.let {
                        KitNotice(
                            title = stringResource(R.string.account_add_failed_title),
                            body = it.accountErrorBody(AccountFlow.AddGoogle),
                            danger = true,
                        )
                    }

                    // Only an account with a password has one to change. On a
                    // Google or phone account the row would be a dead end.
                    if (state.methods.hasEmail) {
                        KitCard(spacing = 0.dp) {
                            ListRow(
                                label = stringResource(R.string.account_change_password),
                                onClick = { if (state.canLink) onOpen(AccountFlow.ChangePassword) },
                                leadingIcon = PamojaIcons.Lock,
                                showDivider = false,
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (state.activeDialog) {
        AccountFlow.AddEmail -> AddEmailDialog(state, onAddEmail, onDismiss)
        AccountFlow.AddPhone -> AddPhoneDialog(state, onSendPhoneCode, onConfirmPhoneCode, onDismiss)
        AccountFlow.ChangePassword -> ChangePasswordDialog(state, onChangePassword, onDismiss)
        // Google needs no form, and nothing opens a dialog for it.
        AccountFlow.AddGoogle, null -> Unit
    }
}

/**
 * One sign-in method, connected or not.
 *
 * A connected row is deliberately inert: there is nothing useful to do to it.
 * Unlinking is not offered, because removing the last method would lock the
 * account permanently and the guard rails that would make it safe are more
 * work than the feature is worth today.
 */
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
    showDivider: Boolean = true,
) {
    val colors = Kit.colors
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    // Google's mark keeps its own colours on white, as its
                    // brand rules require; the others sit on the kit's tint.
                    .background(if (isGoogleMark) Color.White else colors.iconBackground),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (isGoogleMark) Color.Unspecified else colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Kit.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    value ?: stringResource(if (isConnected) R.string.account_connected else R.string.account_not_connected),
                    style = Kit.type.caption,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                isBusy -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.ink, strokeWidth = 2.dp)
                // A tick as well as the words, so the state is not carried by
                // colour alone.
                isConnected -> Icon(
                    painter = painterResource(PamojaIcons.ShieldCheck),
                    contentDescription = stringResource(R.string.account_connected),
                    tint = colors.ink,
                    modifier = Modifier.size(22.dp),
                )
                else -> SecondaryButton(
                    stringResource(R.string.account_add),
                    onClick = onAdd,
                    enabled = enabled,
                    size = ButtonSize.Small,
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

/**
 * The shell every flow on this screen shares: the failure notice sits in one
 * place, directly above the button that raised it.
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
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    KitDialog(
        title = title,
        body = body,
        confirmText = confirmLabel,
        dismissText = stringResource(R.string.common_cancel),
        // Guarded because this same button sends the SMS in the add-phone
        // flow, and two taps inside the recomposition gap would be two paid
        // messages.
        onConfirm = rememberSingleClick(onClick = onConfirm),
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
        loading = isBusy,
        dismissOnClickOutside = dismissOnClickOutside,
    ) {
        content()
        error?.let {
            KitNotice(
                title = stringResource(R.string.account_add_failed_title),
                body = it.accountErrorBody(flow),
                danger = true,
            )
        }
    }
}

@Composable
private fun AddEmailDialog(
    state: AccountUiState,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Prefilled from the account where there is one, which is the Google case:
    // the address is already known and retyping it is a chance to get it wrong.
    var email by rememberSaveable { mutableStateOf(state.methods.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }

    // The same checks the sign-up screen makes, so an address or password
    // rejected there cannot be accepted here.
    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val emailError = stringResource(R.string.email_invalid).takeIf { email.isNotBlank() && !emailValid }
    val passwordError = stringResource(R.string.password_too_short)
        .takeIf { password.isNotEmpty() && !PasswordPolicy.isAccepted(password) }

    AccountDialog(
        title = stringResource(R.string.account_add_email_title),
        body = stringResource(R.string.account_add_email_body),
        confirmLabel = stringResource(R.string.account_add),
        confirmEnabled = emailValid && PasswordPolicy.isAccepted(password),
        isBusy = state.busyWith == AccountFlow.AddEmail,
        error = state.error.takeIf { state.errorFrom == AccountFlow.AddEmail },
        flow = AccountFlow.AddEmail,
        onConfirm = { onConfirm(email, password) },
        onDismiss = onDismiss,
    ) {
        KitTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.email_label),
            placeholder = stringResource(R.string.email_placeholder),
            error = emailError,
            enabled = state.busyWith == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        KitTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.password_label),
            placeholder = stringResource(R.string.password_placeholder_new),
            error = passwordError,
            enabled = state.busyWith == null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    state: AccountUiState,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    val newError = stringResource(R.string.password_too_short)
        .takeIf { new.isNotEmpty() && !PasswordPolicy.isAccepted(new) }

    AccountDialog(
        title = stringResource(R.string.account_change_password),
        body = stringResource(R.string.account_change_password_body),
        confirmLabel = stringResource(R.string.account_change_password_confirm),
        confirmEnabled = current.isNotBlank() && PasswordPolicy.isAccepted(new),
        isBusy = state.busyWith == AccountFlow.ChangePassword,
        error = state.error.takeIf { state.errorFrom == AccountFlow.ChangePassword },
        flow = AccountFlow.ChangePassword,
        onConfirm = { onConfirm(current, new) },
        onDismiss = onDismiss,
    ) {
        KitTextField(
            value = current,
            onValueChange = { current = it },
            label = stringResource(R.string.account_password_current),
            placeholder = stringResource(R.string.password_placeholder_existing),
            enabled = state.busyWith == null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        KitTextField(
            value = new,
            onValueChange = { new = it },
            label = stringResource(R.string.account_password_new),
            placeholder = stringResource(R.string.password_placeholder_new),
            error = newError,
            enabled = state.busyWith == null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
    }
}

/**
 * Number, then code, in one dialog.
 *
 * The country list replaces the dialog's own body rather than opening a sheet
 * on top of it, so there is never a second window over this one competing for
 * the keyboard.
 */
@Composable
private fun AddPhoneDialog(
    state: AccountUiState,
    onSendCode: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var countryIso by rememberSaveable { mutableStateOf(Country.fromLocale().isoCode) }
    val country = Country.sorted.firstOrNull { it.isoCode == countryIso } ?: Country.fromLocale()
    var number by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var pickingCountry by rememberSaveable { mutableStateOf(false) }

    val awaitingCode = state.verificationId != null
    val isBusy = state.busyWith == AccountFlow.AddPhone

    val otpFocusRequester = remember { FocusRequester() }
    LaunchedEffect(awaitingCode) {
        if (awaitingCode) runCatching { otpFocusRequester.requestFocus() }
    }

    if (pickingCountry) {
        CountryPickerDialog(
            selected = country,
            onSelect = {
                countryIso = it.isoCode
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
        confirmLabel = stringResource(if (awaitingCode) R.string.common_confirm else R.string.phone_send_code),
        // Gated on a country-appropriate digit count, because every send costs
        // a real message and a half-typed number would simply waste one.
        confirmEnabled = if (awaitingCode) code.length == OTP_LENGTH else number.length in country.nationalDigits,
        isBusy = isBusy,
        error = state.error.takeIf { state.errorFrom == AccountFlow.AddPhone },
        flow = AccountFlow.AddPhone,
        onConfirm = { if (awaitingCode) onConfirmCode(code) else onSendCode(country.dialCode + number) },
        onDismiss = onDismiss,
        // A code has been paid for and sent, so a mistap on the scrim must not
        // be what throws it away.
        dismissOnClickOutside = !awaitingCode,
    ) {
        if (awaitingCode) {
            OtpBoxes(
                code = code,
                // Frozen while the code is being checked, matching the sign-in
                // code screen: editing mid-verification changes what is on
                // screen without changing what is being verified.
                onCodeChange = { if (!isBusy) code = it },
                hasError = state.errorFrom == AccountFlow.AddPhone,
                focusRequester = otpFocusRequester,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                // Dial code only, never a flag: emoji are banned product wide
                // and this reads correctly to a screen reader.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(FieldShape)
                        .background(Kit.colors.field)
                        .border(1.5.dp, Kit.colors.fieldBorder, FieldShape)
                        .clickable(enabled = !isBusy, role = Role.Button) { pickingCountry = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(country.dialCode, style = Kit.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                }
                KitTextField(
                    value = number,
                    // Capped to the country's own longest valid length, same as
                    // the sign-in phone screen.
                    onValueChange = { number = it.filter(Char::isDigit).take(country.nationalDigits.last) },
                    label = stringResource(R.string.phone_label),
                    placeholder = stringResource(R.string.phone_placeholder),
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The country list, as a dialog rather than the sheet the sign-in screen uses,
 * so it stays inside the dialog window the add-phone flow already owns.
 */
@Composable
private fun CountryPickerDialog(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { Country.search(query) }

    KitDialog(
        title = stringResource(R.string.phone_select_country),
        confirmText = stringResource(R.string.common_back),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        KitTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.phone_search_country),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        // Bounded, so the list scrolls inside the dialog rather than pushing
        // its buttons off the screen.
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            results.forEach { country ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) { onSelect(country) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        country.name,
                        style = Kit.type.bodyStrong.copy(
                            fontWeight = if (country.isoCode == selected.isoCode) FontWeight.ExtraBold else FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(country.dialCode, style = Kit.type.body, color = Kit.colors.muted)
                    if (country.isoCode == selected.isoCode) {
                        Icon(
                            painter = painterResource(PamojaIcons.Check),
                            contentDescription = stringResource(R.string.common_selected),
                            tint = Kit.colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
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
