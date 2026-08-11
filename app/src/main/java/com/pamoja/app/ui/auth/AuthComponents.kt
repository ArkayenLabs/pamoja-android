package com.pamoja.app.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

const val OTP_LENGTH = 6

/**
 * Six boxes driven by one hidden field.
 *
 * A field per box fights SMS autofill, which delivers the whole code at once,
 * and makes backspace behaviour strange. One field keeps autofill working and
 * the boxes become presentation.
 *
 * Shared, because deleting an account on a phone-only login has to re-verify
 * the number and needs exactly this control inside a dialog.
 */
@Composable
fun OtpBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    hasError: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPamojaColors.current

    BasicTextField(
        value = code,
        onValueChange = { new -> onCodeChange(new.filter { it.isDigit() }.take(OTP_LENGTH)) },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        // Transparent so the real boxes below are what the user sees, while this
        // field keeps focus, the keyboard and autofill.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                repeat(OTP_LENGTH) { index ->
                    val char = code.getOrNull(index)
                    val isCursor = index == code.length

                    val borderColor = when {
                        hasError -> colors.statusDanger
                        isCursor -> colors.accentPrimary
                        char != null -> colors.borderStrong
                        else -> colors.borderDefault
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(colors.surfaceInput, RoundedCornerShape(PamojaRadii.sm))
                            .border(
                                width = if (isCursor || hasError) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(PamojaRadii.sm),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (hasError) colors.statusDanger else colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}

/** Back control shared by every auth screen. */
@Composable
fun AuthBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPamojaColors.current
    IconButton(onClick = onBack, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painterResource(PamojaIcons.ArrowLeft),
            contentDescription = "Back",
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}


/**
 * One sign-in method.
 *
 * [isGoogleMark] renders the icon untinted, because Google's brand rules forbid
 * recolouring the G. Everything else tints to the current content colour.
 *
 * While another method is running this one stays legible but non-interactive,
 * so it is obvious what is happening without blocking the whole screen.
 */
@Composable
fun AuthMethodButton(
    @DrawableRes icon: Int,
    label: String,
    loadingLabel: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier = Modifier,
    isGoogleMark: Boolean = false,
) {
    val colors = LocalPamojaColors.current

    val container = if (filled) colors.accentPrimary else Color.Transparent
    val content = if (filled) colors.textOnBrand else colors.textPrimary

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(PamojaRadii.md),
        color = container,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (filled) Modifier
                    else Modifier.border(
                        width = 1.dp,
                        color = colors.borderDefault,
                        shape = RoundedCornerShape(PamojaRadii.md),
                    )
                )
                .background(
                    if (filled) Color.Transparent else colors.surfaceCanvas,
                    RoundedCornerShape(PamojaRadii.md),
                )
                .padding(horizontal = Spacing.x4),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = content,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (isGoogleMark) Color.Unspecified else content,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.x3))

                Text(
                    text = if (isLoading) loadingLabel else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }

            if (!isLoading) {
                Icon(
                    painter = painterResource(PamojaIcons.ChevronRight),
                    contentDescription = null,
                    tint = if (filled) content else colors.textTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(18.dp),
                )
            }
        }
    }
}
