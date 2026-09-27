package com.pamoja.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * A labelled text field. The label sits above the box, never inside it, so
 * it stays readable once there is text and at large font sizes.
 *
 * [error] replaces [helper] and turns the border red; it is also exposed to
 * TalkBack as the field's error.
 */
@Composable
fun KitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = Kit.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        error != null -> colors.danger
        focused -> colors.ink
        else -> colors.fieldBorder
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Kit.type.label, color = colors.ink)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            textStyle = Kit.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(FieldShape)
                        .background(colors.field)
                        .border(if (focused || error != null) 2.dp else 1.5.dp, borderColor, FieldShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = Kit.type.body.copy(fontSize = 17.sp), color = colors.muted)
                    }
                    inner()
                }
            },
        )
        val note = error ?: helper
        if (note != null) {
            Text(note, style = Kit.type.caption, color = if (error != null) colors.danger else colors.muted)
        }
    }
}

/**
 * A checkbox and its label as one 44dp+ target, e.g. "I'm 18 or older".
 * The whole row toggles, not just the box.
 */
@Composable
fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Kit.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
    ) {
        val box = RoundedCornerShape(6.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(box)
                .background(if (checked) colors.button else Color.Transparent)
                .border(2.dp, if (checked) colors.button else colors.fieldBorder, box),
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(PamojaIcons.Check),
                    contentDescription = null,
                    tint = colors.onButton,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(text, style = Kit.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
    }
}
