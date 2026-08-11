package com.pamoja.app.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.ui.components.NoticeTone
import com.pamoja.app.ui.components.OfflineBanner
import com.pamoja.app.ui.components.PamojaErrorState
import com.pamoja.app.ui.components.PamojaNotice
import com.pamoja.app.ui.components.PamojaTextField
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.components.toErrorCopy
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The profile editor that never existed.
 *
 * `ProfileSetupScreen` collects these same four fields during onboarding and is
 * then unreachable, so until now a typo in your name or a mistyped height was
 * permanent. Deliberately shares its validation rules with that screen: a value
 * that onboarding would have rejected should not become acceptable later.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Hoisted: the validate lambdas run on focus change, outside composable scope.
    val nameRequired = stringResource(R.string.profile_name_required)
    val nameTooLong = stringResource(R.string.profile_name_too_long)
    val ageField = stringResource(R.string.profile_field_age)
    val heightField = stringResource(R.string.profile_field_height)
    val weightField = stringResource(R.string.profile_field_weight)
    val ageNaN = stringResource(R.string.profile_number_invalid, ageField)
    val ageRange = stringResource(R.string.profile_number_range, ageField)
    val heightNaN = stringResource(R.string.profile_number_invalid, heightField)
    val heightRange = stringResource(R.string.profile_number_range, heightField)
    val weightNaN = stringResource(R.string.profile_number_invalid, weightField)
    val weightRange = stringResource(R.string.profile_number_range, weightField)
    val savedMessage = stringResource(R.string.profile_edit_saved)

    val nameErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> nameRequired
            input.trim().length > 50 -> nameTooLong
            else -> null
        }
    }
    val ageErrorFor: (String) -> String? = { it.validOptionalNumber(13..120, ageNaN, ageRange) }
    val heightErrorFor: (String) -> String? = { it.validOptionalNumber(50..250, heightNaN, heightRange) }
    val weightErrorFor: (String) -> String? = { it.validOptionalNumber(20..300, weightNaN, weightRange) }

    val firstError = nameErrorFor(uiState.name)
        ?: ageErrorFor(uiState.age)
        ?: heightErrorFor(uiState.height)
        ?: weightErrorFor(uiState.weight)

    // A save that changed something deserves saying so, and the screen stays put
    // rather than navigating away, because editing is often several passes.
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.clearSaved()
            snackbarHostState.showSnackbar(savedMessage)
        }
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
                    text = stringResource(R.string.profile_edit_title),
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

                uiState.isLoading -> EditProfileSkeleton()

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.x6)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Spacer(modifier = Modifier.height(Spacing.x4))

                    Text(
                        text = stringResource(R.string.profile_edit_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.x7))

                    ProfileAvatar(
                        name = uiState.name,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(modifier = Modifier.height(Spacing.x3))

                    // Says what it is rather than offering a camera button that
                    // does nothing, which is what onboarding still does.
                    Text(
                        text = stringResource(R.string.profile_edit_photo_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(modifier = Modifier.height(Spacing.x7))

                    PamojaTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = stringResource(R.string.profile_name_label),
                        placeholder = stringResource(R.string.profile_name_placeholder),
                        enabled = !uiState.isSaving,
                        validate = nameErrorFor,
                    )

                    Spacer(modifier = Modifier.height(Spacing.x3))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                    ) {
                        PamojaTextField(
                            value = uiState.age,
                            onValueChange = viewModel::onAgeChange,
                            label = stringResource(R.string.profile_age_label),
                            placeholder = stringResource(R.string.profile_optional_placeholder),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            validate = ageErrorFor,
                        )
                        PamojaTextField(
                            value = uiState.height,
                            onValueChange = viewModel::onHeightChange,
                            label = stringResource(R.string.profile_height_label),
                            placeholder = stringResource(R.string.profile_optional_placeholder),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            validate = heightErrorFor,
                        )
                        PamojaTextField(
                            value = uiState.weight,
                            onValueChange = viewModel::onWeightChange,
                            label = stringResource(R.string.profile_weight_label),
                            placeholder = stringResource(R.string.profile_optional_placeholder),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            validate = weightErrorFor,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.x2))

                    Text(
                        text = stringResource(R.string.profile_optional_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.x6))

                    if (uiState.isOffline) {
                        PamojaNotice(
                            icon = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.profile_edit_offline_title),
                            body = stringResource(R.string.profile_edit_offline_body),
                            tone = NoticeTone.Warning,
                        )
                        Spacer(modifier = Modifier.height(Spacing.x3))
                    } else if (uiState.saveError != null) {
                        PamojaNotice(
                            icon = PamojaIcons.AlertCircle,
                            title = stringResource(R.string.profile_edit_failed_title),
                            body = uiState.saveError!!.toErrorCopy().body(context),
                            tone = NoticeTone.Danger,
                        )
                        Spacer(modifier = Modifier.height(Spacing.x3))
                    }

                    Button(
                        onClick = { viewModel.save() },
                        enabled = firstError == null && uiState.canSave,
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
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.textTertiary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(Spacing.x2))
                        }
                        Text(
                            text = stringResource(
                                if (uiState.isSaving) R.string.profile_edit_saving
                                else R.string.profile_edit_save
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (firstError != null) {
                        Spacer(modifier = Modifier.height(Spacing.x2))
                        Text(
                            text = firstError,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.statusDanger,
                        )
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

/**
 * Initials in a circle.
 *
 * Stands in until photos exist. There is no Firebase Storage bucket in this
 * project, so an upload would mean a new dependency, its own security rules and
 * a new cost surface, which is more than a placeholder is worth.
 */
@Composable
fun ProfileAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
) {
    val colors = LocalPamojaColors.current

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(2).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textOnBrand,
        )
    }
}

/** Matches the real form's rhythm: avatar, one full field, then the three short ones. */
@Composable
private fun EditProfileSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6),
    ) {
        Spacer(modifier = Modifier.height(Spacing.x4))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.8f), height = 14.dp)

        Spacer(modifier = Modifier.height(Spacing.x7))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SkeletonBlock(modifier = Modifier.size(80.dp), height = 80.dp, shape = CircleShape)
        }

        Spacer(modifier = Modifier.height(Spacing.x8))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.2f), height = 12.dp)
        Spacer(modifier = Modifier.height(Spacing.x2))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 56.dp)

        Spacer(modifier = Modifier.height(Spacing.x5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            repeat(3) {
                SkeletonBlock(modifier = Modifier.weight(1f), height = 56.dp)
            }
        }
    }
}

/**
 * Optional numeric field check, same rule as onboarding.
 *
 * Blank passes, because these fields are genuinely optional and clearing one is
 * a legitimate edit rather than a mistake.
 */
private fun String.validOptionalNumber(
    range: IntRange,
    notANumber: String,
    outOfRange: String,
): String? {
    if (isBlank()) return null
    val value = toIntOrNull() ?: return notANumber
    return if (value in range) null else outOfRange
}
