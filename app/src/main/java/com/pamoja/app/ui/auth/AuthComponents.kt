package com.pamoja.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.sp
import com.pamoja.app.R
import com.pamoja.app.ui.kit.FieldShape
import com.pamoja.app.ui.kit.Kit

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
fun AuthLegalLine(
    modifier: Modifier = Modifier,
    textColor: Color = Kit.colors.muted,
    linkColor: Color = Kit.colors.ink,
    style: TextStyle = Kit.type.caption.copy(textAlign = TextAlign.Center),
) {
    val terms = stringResource(R.string.auth_legal_terms)
    val privacy = stringResource(R.string.auth_legal_privacy)
    val sentence = stringResource(R.string.auth_legal_line, terms, privacy)

    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )

    // Resolved outside, remembered on the resolved values, so switching
    // language rebuilds it rather than freezing the old sentence.
    val annotated = remember(sentence, terms, privacy, linkColor) {
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
        style = style,
        color = textColor,
        textAlign = TextAlign.Center,
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
 * Shared, because adding a phone number on the Account screen has to verify it
 * and needs exactly this control.
 */
@Composable
fun OtpBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    hasError: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = Kit.colors

    BasicTextField(
        value = code,
        onValueChange = { new -> onCodeChange(new.filter { it.isDigit() }.take(OTP_LENGTH)) },
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        // Transparent so the real boxes below are what the user sees, while this
        // field keeps focus, the keyboard and autofill.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(OTP_LENGTH) { index ->
                    val char = code.getOrNull(index)
                    val isCursor = index == code.length
                    val borderColor = when {
                        hasError -> colors.danger
                        isCursor -> colors.ink
                        else -> colors.fieldBorder
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(FieldShape)
                            .background(colors.field)
                            .border(if (isCursor || hasError) 2.dp else 1.5.dp, borderColor, FieldShape),
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = Kit.type.headline.copy(fontSize = 26.sp),
                            color = if (hasError) colors.danger else colors.ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}
