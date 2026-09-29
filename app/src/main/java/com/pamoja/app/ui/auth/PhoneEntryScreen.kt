package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.ui.components.rememberSingleClick
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.FieldShape
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSheet
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

@Composable
fun PhoneEntryScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onCodeSent: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    // Defaulted from the device rather than hardcoded, so the app is not
    // implicitly single-market.
    LaunchedEffect(Unit) {
        if (uiState.phoneNumber.isBlank()) {
            viewModel.onCountryChange(Country.fromLocale())
        }
        // Shared ViewModel: a failure from the landing screen would otherwise
        // still be on display here, attached to a method the user has left.
        viewModel.clearError()
    }

    LaunchedEffect(uiState.codeSent) {
        if (uiState.codeSent) {
            viewModel.consumeCodeSent()
            onCodeSent()
        }
    }

    PhoneEntryContent(
        country = uiState.country,
        number = uiState.phoneNumber,
        busy = uiState.busyWith == AuthMethod.Phone,
        blocked = uiState.busyWith != null,
        error = uiState.error,
        onCountry = viewModel::onCountryChange,
        onNumber = viewModel::onPhoneNumberChange,
        onSend = { activity?.let(viewModel::sendCode) },
        onBack = onBack,
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun PhoneEntryContent(
    country: Country,
    number: String,
    busy: Boolean,
    blocked: Boolean,
    error: AppError?,
    onCountry: (Country) -> Unit,
    onNumber: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) = KitInAppTheme {
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }
    var lengthError by rememberSaveable { mutableStateOf<String?>(null) }

    val digits = number.length
    val isComplete = digits in country.nationalDigits
    // Two messages, not one. digits could fail isComplete by being either
    // side of the country's valid range, and the field has no max-length
    // cap, so someone can type 12 digits into a 10-digit country and see
    // "too short" printed at them. That happened, was reported, and is
    // exactly the kind of copy bug that makes an app look untrustworthy.
    val lengthMessage = if (digits > country.nationalDigits.last) {
        stringResource(R.string.phone_too_long, country.name)
    } else {
        stringResource(R.string.phone_too_short, country.name)
    }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(
                text = stringResource(if (busy) R.string.phone_requesting_code else R.string.phone_send_code),
                // Every send is a paid SMS and a step toward Firebase's rate
                // limit, so a double tap must not become two of them.
                onClick = rememberSingleClick {
                    if (!isComplete) lengthError = lengthMessage else onSend()
                },
                enabled = !blocked && number.isNotBlank(),
                loading = busy,
            )
        },
    ) {
        Text(stringResource(R.string.phone_title), style = Kit.type.headline)
        Text(stringResource(R.string.phone_subtitle), style = Kit.type.body, color = Kit.colors.muted)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            // ISO code and dial code, never a flag: emoji are banned product
            // wide, and this reads correctly to a screen reader.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_label_country), style = Kit.type.label)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(FieldShape)
                        .background(Kit.colors.field)
                        .border(1.5.dp, Kit.colors.fieldBorder, FieldShape)
                        .clickable(role = Role.Button, onClick = { showCountryPicker = true })
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(country.isoCode, style = Kit.type.caption, color = Kit.colors.muted)
                    Text(
                        country.dialCode,
                        style = Kit.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Icon(
                        painter = painterResource(PamojaIcons.ChevronRight),
                        contentDescription = null,
                        tint = Kit.colors.muted,
                        modifier = Modifier.size(16.dp).rotate(90f),
                    )
                }
            }
            KitTextField(
                value = number,
                onValueChange = {
                    lengthError = null
                    onNumber(it)
                },
                label = stringResource(R.string.phone_label),
                placeholder = stringResource(R.string.phone_placeholder),
                // The length check is about the number just typed, so it wins
                // over a server failure.
                error = lengthError ?: error?.authErrorBody(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
        }

        if (busy) {
            Text(stringResource(R.string.phone_security_check), style = Kit.type.caption, color = Kit.colors.muted)
        }
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            selected = country,
            onSelect = {
                onCountry(it)
                lengthError = null
            },
            onDismiss = { showCountryPicker = false },
        )
    }
}

/**
 * Every country, searchable by name or dial code. The phone's own country
 * sits first while nothing is typed, since it is almost always the answer.
 */
@Composable
private fun CountryPickerSheet(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val deviceCountry = remember { Country.fromLocale() }
    val results = remember(query) { Country.search(query) }

    KitSheet(onDismiss = onDismiss, title = stringResource(R.string.phone_select_country)) { close ->
        KitTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.phone_search_country),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val shown = if (query.isBlank()) listOf(deviceCountry) + results.filter { it.isoCode != deviceCountry.isoCode } else results
        shown.forEach { country ->
            CountryRow(
                country = country,
                isSelected = country.isoCode == selected.isoCode,
                onClick = { close { onSelect(country) } },
            )
        }
    }
}

@Composable
private fun CountryRow(country: Country, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(40.dp)
                .height(24.dp)
                .clip(FieldShape)
                .background(Kit.colors.track),
        ) {
            Text(country.isoCode, style = Kit.type.caption.copy(fontWeight = FontWeight.Bold))
        }
        Text(
            country.name,
            style = Kit.type.bodyStrong.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Text(country.dialCode, style = Kit.type.body, color = Kit.colors.muted)
        if (isSelected) {
            Icon(
                painter = painterResource(PamojaIcons.Check),
                contentDescription = stringResource(R.string.common_selected),
                tint = Kit.colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
