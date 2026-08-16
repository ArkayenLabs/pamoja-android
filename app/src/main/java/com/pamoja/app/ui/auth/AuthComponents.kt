package com.pamoja.app.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pamoja.app.R
import com.pamoja.app.ui.theme.Layout
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

const val OTP_LENGTH = 6

/** The published legal pages. Both are live and linked from Settings too. */
private const val TERMS_URL = "https://www.arkayenlabs.com/terms/pamoja"
private const val PRIVACY_URL = "https://www.arkayenlabs.com/privacy/pamoja"

/**
 * "By continuing you agree to our Terms of Use and Privacy Policy", with both
 * actually openable.
 *
 * It previously rendered as plain grey text. Naming two documents and giving no
 * way to read them is not consent in any meaningful sense, and Play reviewers
 * do click these.
 *
 * The link ranges are found by searching the formatted sentence for the two
 * labels rather than being hardcoded offsets, so a translation may reorder or
 * rephrase around them and the links still land in the right place.
 */
@Composable
fun AuthLegalLine(modifier: Modifier = Modifier) {
    val colors = LocalPamojaColors.current

    val terms = stringResource(R.string.auth_legal_terms)
    val privacy = stringResource(R.string.auth_legal_privacy)
    val sentence = stringResource(R.string.auth_legal_line, terms, privacy)

    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = colors.accentPrimary,
            textDecoration = TextDecoration.Underline,
        ),
    )

    // Resolved outside, remembered on the resolved values, so switching
    // language rebuilds it rather than freezing the old sentence.
    val annotated = remember(sentence, terms, privacy, colors.accentPrimary) {
        buildAnnotatedString {
            append(sentence)
            listOf(terms to TERMS_URL, privacy to PRIVACY_URL).forEach { (label, url) ->
                val start = sentence.indexOf(label)
                if (start >= 0) {
                    addLink(LinkAnnotation.Url(url, linkStyles), start, start + label.length)
                }
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = modifier,
    )
}

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

/**
 * Back control shared by every auth screen.
 *
 * The design system's `.ic`: a filled 44dp chip, not a bare arrow floating on
 * the background. It reads as a control at a glance and gives the touch target
 * a visible edge.
 */
@Composable
fun AuthBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPamojaColors.current
    val shape = RoundedCornerShape(PamojaRadii.md)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(colors.surface2)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(PamojaIcons.ArrowLeft),
            contentDescription = "Back",
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The design system's `.tbar`: the back chip with the step's name beside it.
 *
 * The label is what tells someone three screens into a sign-up which flow they
 * are in, which a bare arrow never does. Mono caps rather than a title, because
 * it is a marker for the screen and not its heading.
 */
@Composable
fun AuthTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = LocalPamojaColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        AuthBackButton(onBack = onBack)
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
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
            .height(58.dp),
        shape = PillShape,
        color = container,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (filled) Modifier
                    else Modifier.border(
                        width = Layout.strokeThick,
                        color = colors.borderDefault,
                        shape = PillShape,
                    )
                )
                .background(
                    if (filled) Color.Transparent else colors.surfaceCanvas,
                    PillShape,
                )
                .padding(horizontal = Spacing.x5),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = content,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (isGoogleMark) {
                    // The G sits on its own white disc rather than straight on
                    // the button.
                    //
                    // Google's brand rules require the coloured mark to sit on
                    // white or a light neutral, and this is also the visual fix:
                    // full-colour blue, green, yellow and red on saturated
                    // terracotta muddies every one of them, and in dark mode the
                    // blue leg all but disappears. The disc restores the contrast
                    // the mark was drawn for and is the treatment Google's own
                    // guidance prescribes for coloured buttons.
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = content,
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
