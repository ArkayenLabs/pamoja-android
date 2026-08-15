package com.pamoja.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.model.UnitSystem
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

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onPhotoPicked(it.toString()) } }

    val nameErrorFor: (String) -> String? = { input ->
        when {
            input.isBlank() -> nameRequired
            input.trim().length > 50 -> nameTooLong
            else -> null
        }
    }
    val isImperial = uiState.unitSystem == UnitSystem.Imperial

    // The same human bounds expressed in whichever units are on screen, so the
    // rule does not tighten or loosen just because the display changed.
    val ageErrorFor: (String) -> String? = { it.validOptionalNumber(13..120, ageNaN, ageRange) }
    val heightErrorFor: (String) -> String? = { input ->
        val range = if (isImperial) 1..8 else 50..250
        input.validOptionalNumber(range, heightNaN, heightRange)
    }
    val heightInchesErrorFor: (String) -> String? = { input ->
        input.validOptionalNumber(0..11, heightNaN, heightRange)
    }
    val weightErrorFor: (String) -> String? = { input ->
        val range = if (isImperial) 44..660 else 20..300
        input.validOptionalNumber(range, weightNaN, weightRange)
    }

    val firstError = nameErrorFor(uiState.name)
        ?: ageErrorFor(uiState.age)
        ?: heightErrorFor(uiState.height)
        ?: (if (isImperial) heightInchesErrorFor(uiState.heightInches) else null)
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

                    // The design's avatar: large, with the camera as a filled
                    // disc overlapping its lower right rather than a text link
                    // underneath. The ring around that disc is the page colour,
                    // which is what separates it from the photo behind it.
                    //
                    // Still the photo picker, not the camera permission.
                    // Android's picker runs outside the app and returns one
                    // image, so there is no READ_MEDIA_IMAGES to request, no
                    // rationale screen, and no permanently-denied state.
                    val canPickPhoto = !uiState.isUploadingPhoto && !uiState.isOffline
                    Box(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ProfileAvatar(
                                name = uiState.name,
                                photoUrl = uiState.photoUrl,
                                size = 104.dp,
                            )
                            if (uiState.isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = colors.textOnBrand,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceApp)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canPickPhoto) colors.accentPrimary else colors.surface2
                                )
                                .clickable(enabled = canPickPhoto) {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(PamojaIcons.Camera),
                                // The spinner over the avatar is the only visual
                                // sign of an upload, and it says nothing to a
                                // screen reader. The label carries that state.
                                contentDescription = stringResource(
                                    when {
                                        uiState.isUploadingPhoto -> R.string.profile_edit_photo_uploading
                                        uiState.photoUrl.isNullOrBlank() -> R.string.profile_edit_photo_add
                                        else -> R.string.profile_edit_photo_change
                                    }
                                ),
                                tint = if (canPickPhoto) colors.textOnBrand else colors.textTertiary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }

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
                            label = stringResource(
                                if (isImperial) R.string.profile_height_label_ft
                                else R.string.profile_height_label
                            ),
                            placeholder = stringResource(R.string.profile_optional_placeholder),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            validate = heightErrorFor,
                        )
                        // Imperial height is two numbers. Nobody states a height
                        // in inches alone, so a single field would be unusable.
                        if (isImperial) {
                            PamojaTextField(
                                value = uiState.heightInches,
                                onValueChange = viewModel::onHeightInchesChange,
                                label = stringResource(R.string.profile_height_label_in),
                                placeholder = stringResource(R.string.profile_optional_placeholder),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSaving,
                                validate = heightInchesErrorFor,
                            )
                        }
                        PamojaTextField(
                            value = uiState.weight,
                            onValueChange = viewModel::onWeightChange,
                            label = stringResource(
                                if (isImperial) R.string.profile_weight_label_lb
                                else R.string.profile_weight_label
                            ),
                            placeholder = stringResource(R.string.profile_optional_placeholder),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                            validate = weightErrorFor,
                        )
                    }

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
 * A photo in a circle, with initials underneath it.
 *
 * The initials are not a fallback that swaps in on failure, they are always
 * drawn and the photo is laid over them. That way they show while the image
 * loads and remain if it never arrives, instead of the account appearing as a
 * blank circle on a slow connection.
 */
@Composable
fun ProfileAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    photoUrl: String? = null,
) {
    val colors = LocalPamojaColors.current

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentPrimary),
        contentAlignment = Alignment.Center,
    ) {
        // Initials are drawn first and stay underneath, so they show while the
        // photo loads and remain if it never does. An avatar that renders as a
        // blank circle on a slow connection looks like the account is broken.
        Text(
            text = name.trim().take(2).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textOnBrand,
        )

        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        }
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
