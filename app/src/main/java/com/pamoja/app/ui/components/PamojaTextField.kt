package com.pamoja.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * Text field with the validation states the design system asks for: error text
 * sits inline beneath its own field rather than in a snackbar, and validation
 * runs on blur so it never fires while the user is still mid-word.
 *
 * Pass [validate] to get on-blur checking. The returned message is displayed
 * and also cleared automatically once the user edits the field again, because
 * leaving a stale error under a field the user is actively fixing is worse than
 * showing nothing.
 */
@Composable
fun PamojaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    /** Error supplied by the caller, for example a server response. */
    error: String? = null,
    /** Runs when the field loses focus. Return null when valid. */
    validate: ((String) -> String?)? = null,
) {
    val colors = LocalPamojaColors.current

    var blurError by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }

    val shownError = error ?: blurError
    val borderColor = when {
        shownError != null -> colors.statusDanger
        else -> colors.borderDefault
    }

    Column(modifier = modifier) {
        // The design system's `.lbl`: mono, tracked out, uppercase, and small.
        // Set this way it reads as a tag on the field rather than as a sentence
        // competing with the heading above it.
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Spacing.x2))

        OutlinedTextField(
            value = value,
            onValueChange = {
                blurError = null
                onValueChange(it)
            },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Layout.fieldHeight)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        wasFocused = true
                    } else if (wasFocused) {
                        blurError = validate?.invoke(value)
                    }
                },
            enabled = enabled,
            shape = RoundedCornerShape(PamojaRadii.lg),
            singleLine = true,
            isError = shownError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            visualTransformation = when {
                !isPassword || revealed -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            trailingIcon = if (isPassword) {
                {
                    // 48dp so it clears the accessibility minimum; the icon inside
                    // stays 20dp so it does not overpower the field.
                    IconButton(
                        onClick = { revealed = !revealed },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (revealed) PamojaIcons.EyeOff else PamojaIcons.Eye
                            ),
                            contentDescription = stringResource(
                                if (revealed) R.string.common_hide_password
                                else R.string.common_show_password
                            ),
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            // Border weight is not settable here: focusedBorderThickness lives on
            // OutlinedTextFieldDefaults.Container, not on this composable. M3
            // already draws 2dp on focus against 1dp at rest, which is the
            // design's ratio, so this is left to the default rather than
            // rebuilding the field on a decoration box to hard-code it.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (shownError != null) colors.statusDanger else colors.accentPrimary,
                unfocusedBorderColor = borderColor,
                errorBorderColor = colors.statusDanger,
                focusedContainerColor = colors.surfaceInputFocus,
                unfocusedContainerColor = colors.surfaceInput,
                // Tinted, not neutral. The design fills an invalid field so the
                // problem is visible without reading the message under it.
                errorContainerColor = colors.statusDangerSubtle,
                cursorColor = colors.accentPrimary,
            ),
        )

        val helper = shownError ?: supportingText
        if (helper != null) {
            Spacer(modifier = Modifier.height(Spacing.x2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shownError != null) {
                    Icon(
                        painter = painterResource(PamojaIcons.AlertCircle),
                        contentDescription = null,
                        tint = colors.statusDanger,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.x2))
                }
                Text(
                    text = helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (shownError != null) colors.statusDanger else colors.textTertiary,
                )
            }
        }
    }
}
