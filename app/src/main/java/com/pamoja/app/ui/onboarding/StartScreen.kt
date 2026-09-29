package com.pamoja.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.ui.kit.Avatar
import com.pamoja.app.ui.kit.CardTone
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitInAppTheme
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitTextField
import com.pamoja.app.ui.kit.PopChip
import com.pamoja.app.ui.kit.PrimaryButton
import com.pamoja.app.ui.kit.QuietButton
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.util.InviteLink

/**
 * The first screen of a fresh install: one screen where there used to be two
 * product intros (REBUILD_PLAN.md §6). Asks for nothing; an invite link is the
 * only other way in.
 */
@Composable
fun StartScreen(
    onGetStarted: () -> Unit,
    onHaveInvite: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.onScreenViewed() }
    StartContent(
        onGetStarted = { viewModel.completeIntro(onGetStarted) },
        onHaveInvite = onHaveInvite,
    )
}

/** Stateless rendering boundary used by visual and interaction tests. */
@Composable
internal fun StartContent(onGetStarted: () -> Unit, onHaveInvite: () -> Unit) = KitInAppTheme {
    KitScreen(
        bottom = {
            PrimaryButton(stringResource(R.string.start_get_started), onClick = onGetStarted)
            QuietButton(stringResource(R.string.start_have_invite), onClick = onHaveInvite)
        },
    ) {
        FacesHero()
        Text(stringResource(R.string.start_headline), style = Kit.type.display)
        Text(stringResource(R.string.start_body), style = Kit.type.body, color = Kit.colors.muted)
    }
}

/**
 * Faces first: a loose cluster of people, drawn with the same fallback
 * avatars members get before they add a photo, so the first thing a person
 * sees is what their group will look like. Decorative, so hidden from TalkBack.
 */
@Composable
private fun FacesHero() {
    // Positions and sizes as drawn on the 342dp-wide mockup, scaled to fit.
    val faces = listOf(
        Face("Priya", 8, 34, 100), Face("Ramesh", 124, 0, 80), Face("Sunita", 226, 30, 96),
        Face("Arjun", 78, 138, 78), Face("Meera", 164, 112, 90), Face("Kofi", 262, 160, 68),
    )
    KitCard(tone = CardTone.Hero, padding = 20.dp) {
        Text(stringResource(R.string.app_name).lowercase(), style = Kit.type.title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = maxWidth.value / 342f
            Box(Modifier.fillMaxWidth().height((300 * scale).dp)) {
                faces.forEach { face ->
                    Avatar(
                        name = face.name,
                        photoUrl = null,
                        size = (face.size * scale).dp,
                        modifier = Modifier.offset((face.x * scale).dp, (face.y * scale).dp),
                    )
                }
                PopChip(
                    text = stringResource(R.string.start_chip),
                    icon = PamojaIcons.Flame,
                    modifier = Modifier.offset(0.dp, (262 * scale).dp),
                )
            }
        }
    }
}

private data class Face(val name: String, val x: Int, val y: Int, val size: Int)

/**
 * "I have an invite link": for someone who was sent a link but installed from
 * the store, so the link itself never opened the app. The code is kept and
 * picked up after sign-in, exactly as a tapped link is.
 */
@Composable
fun EnterInviteScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    EnterInviteContent(
        onSubmit = { code -> viewModel.continueWithInvite(code, onContinue) },
        onBack = onBack,
    )
}

/**
 * Also the signed-in "Join with a link" screen, with its own [body] and a QR
 * scan ([onScan]) for someone showing their invite in person. [scanError]
 * explains a scan that did not produce an invite.
 */
@Composable
internal fun EnterInviteContent(
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    body: String = stringResource(R.string.enter_invite_body),
    onScan: (() -> Unit)? = null,
    scanError: String? = null,
) = KitInAppTheme {
    var input by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val code = inviteCodeFrom(input)
    val submit = { if (code != null) onSubmit(code) else showError = true }

    KitScreen(
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
        bottom = {
            PrimaryButton(stringResource(R.string.common_continue), onClick = submit, enabled = input.isNotBlank())
        },
    ) {
        Text(stringResource(R.string.enter_invite_title), style = Kit.type.headline)
        Text(body, style = Kit.type.body, color = Kit.colors.muted)
        KitTextField(
            value = input,
            onValueChange = {
                input = it
                showError = false
            },
            label = stringResource(R.string.enter_invite_label),
            placeholder = InviteLink.build("…"),
            error = if (showError) stringResource(R.string.enter_invite_invalid) else scanError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        onScan?.let {
            SecondaryButton(stringResource(R.string.join_link_scan), onClick = it, icon = PamojaIcons.QrCode)
        }
    }
}

/** A full invite link in any supported form, or a bare group code. */
internal fun inviteCodeFrom(raw: String): String? =
    InviteLink.parseCode(raw) ?: raw.trim().takeIf { PamojaGroupId.isValid(it) }
