package com.pamoja.app.ui.join

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.ui.onboarding.EnterInviteContent
import com.pamoja.app.ui.onboarding.inviteCodeFrom
import com.pamoja.app.util.QrScanner

/**
 * "Join with a link" from the group switcher, for someone already signed in.
 * Paste a link or scan the QR on someone's invite screen; either opens the
 * invite preview, which shows the group and asks before joining.
 */
@Composable
fun JoinWithLinkScreen(onOpenInvite: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    val notAnInvite = stringResource(R.string.home_join_qr_not_pamoja)
    val scanFailed = stringResource(R.string.home_join_qr_failed)

    EnterInviteContent(
        onSubmit = onOpenInvite,
        onBack = onBack,
        body = stringResource(R.string.join_link_body),
        scanError = scanError,
        onScan = {
            scanError = null
            QrScanner.scan(context) { result ->
                result.onSuccess { raw ->
                    // Someone else's wifi or website QR says so, never crashes.
                    val code = inviteCodeFrom(raw)
                    if (code != null) onOpenInvite(code) else scanError = notAnInvite
                }
                // A cancelled scan is a choice, so it says nothing.
                result.onFailure { e -> if (e !is QrScanner.Cancelled) scanError = scanFailed }
            }
        },
    )
}
