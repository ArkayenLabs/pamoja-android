package com.pamoja.app.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

@Composable
fun PhoneEntryScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onCodeSent: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    // LocalActivity rather than casting LocalContext, which throws when the
    // context is wrapped rather than being the Activity itself.
    val activity = LocalActivity.current

    var showCountryPicker by remember { mutableStateOf(false) }
    var blurError by remember { mutableStateOf<String?>(null) }

    // Defaulted from the device rather than hardcoded, so the app is not
    // implicitly single-market.
    LaunchedEffect(Unit) {
        if (uiState.phoneNumber.isBlank()) {
            viewModel.onCountryChange(Country.fromLocale())
        }
    }

    LaunchedEffect(uiState.codeSent) {
        if (uiState.codeSent) {
            viewModel.consumeCodeSent()
            onCodeSent()
        }
    }

    val digits = uiState.phoneNumber.length
    val isComplete = digits in uiState.country.nationalDigits
    val tooShortMessage = stringResource(R.string.phone_too_short, uiState.country.name)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.x6)
        ) {
            Spacer(modifier = Modifier.height(Spacing.x2))

            AuthBackButton(onBack = onBack)

            Spacer(modifier = Modifier.height(Spacing.x6))

            Text(
                text = stringResource(R.string.phone_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x3))

            Text(
                text = stringResource(R.string.phone_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x8))

            Text(
                text = stringResource(R.string.phone_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ISO code and dial code, never a flag. Emoji are banned product
                // wide, and this reads correctly to a screen reader.
                Surface(
                    onClick = { showCountryPicker = true },
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    color = colors.surfaceInput,
                    modifier = Modifier.height(56.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = colors.borderDefault,
                                shape = RoundedCornerShape(PamojaRadii.sm),
                            )
                            .padding(horizontal = Spacing.x4)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = uiState.country.isoCode,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.x2))
                        Text(
                            text = uiState.country.dialCode,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.phoneNumber,
                    onValueChange = {
                        blurError = null
                        viewModel.onPhoneNumberChange(it)
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.phone_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(PamojaRadii.sm),
                    singleLine = true,
                    isError = blurError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentPrimary,
                        unfocusedBorderColor = colors.borderDefault,
                        errorBorderColor = colors.statusDanger,
                        focusedContainerColor = colors.surfaceInput,
                        unfocusedContainerColor = colors.surfaceInput,
                        errorContainerColor = colors.surfaceInput,
                        cursorColor = colors.accentPrimary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x3))

            // The local blur message wins, since it is about the number just
            // typed; a server failure is the fallback.
            val helper = blurError ?: uiState.error?.authErrorBody()
            if (helper != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(PamojaIcons.AlertCircle),
                        contentDescription = null,
                        tint = colors.statusDanger,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                    Text(
                        text = helper,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.statusDanger,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.phone_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.x8))

            Button(
                onClick = {
                    if (!isComplete) {
                        // Resolved here rather than inside the lambda, since a
                        // click handler is not a composable scope.
                        blurError = tooShortMessage
                    } else {
                        activity?.let(viewModel::sendCode)
                    }
                },
                enabled = uiState.busyWith == null && uiState.phoneNumber.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(PamojaRadii.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                    disabledContainerColor = colors.accentPrimarySubtle,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                if (uiState.busyWith == AuthMethod.Phone) {
                    CircularProgressIndicator(
                        color = colors.textOnBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(text = stringResource(R.string.phone_send_code), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.x10))
        }
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            selected = uiState.country,
            onSelect = {
                viewModel.onCountryChange(it)
                blurError = null
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val deviceCountry = remember { Country.fromLocale() }
    val results = remember(query) { Country.search(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCanvas,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.x6)) {
            Text(
                text = stringResource(R.string.phone_select_country),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.x4))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.phone_search_country),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(PamojaIcons.Search),
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp),
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
                    cursorColor = colors.accentPrimary,
                ),
            )

            Spacer(modifier = Modifier.height(Spacing.x4))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (query.isBlank()) {
                    item {
                        Text(
                            text = "FROM YOUR DEVICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        CountryRow(
                            country = deviceCountry,
                            isSelected = deviceCountry.isoCode == selected.isoCode,
                            onClick = { onSelect(deviceCountry) },
                        )
                        Spacer(modifier = Modifier.height(Spacing.x4))
                        Text(
                            text = "ALL COUNTRIES",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }

                items(results, key = { it.isoCode }) { country ->
                    CountryRow(
                        country = country,
                        isSelected = country.isoCode == selected.isoCode,
                        onClick = { onSelect(country) },
                    )
                }

                item { Spacer(modifier = Modifier.height(Spacing.x10)) }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPamojaColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(24.dp)
                    .background(colors.surfaceSunken, RoundedCornerShape(PamojaRadii.xs)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = country.isoCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.x3))

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

            if (isSelected) {
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
