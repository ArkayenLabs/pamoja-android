package com.pamoja.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.ui.components.ErrorCopy

/**
 * Sign-in flavoured wording for the shared error taxonomy.
 *
 * The generic copy is deliberately vague, because it has to work anywhere. In a
 * sign-in flow that is not good enough: "someone got there first" is meaningless
 * when what actually happened is that the email is already registered, and the
 * useful thing to say is which of the three methods to try instead.
 *
 * So the classification stays shared and only the phrasing is specialised.
 */
@Composable
fun Throwable.toAuthErrorCopy(): ErrorCopy = when (toAppError()) {

    is AppError.Conflict -> ErrorCopy(
        title = R.string.auth_error_email_in_use_title,
        body = R.string.auth_error_email_in_use_body,
    )

    is AppError.NotFound -> ErrorCopy(
        title = R.string.auth_error_no_account_title,
        body = R.string.auth_error_no_account_body,
    )

    is AppError.InvalidCredentials -> ErrorCopy(
        title = R.string.auth_error_credentials_title,
        body = R.string.auth_error_credentials_body,
    )

    is AppError.RateLimited -> ErrorCopy(
        title = R.string.auth_error_rate_limited_title,
        body = R.string.auth_error_rate_limited_body,
    )

    is AppError.Offline, is AppError.Network -> ErrorCopy(
        title = R.string.auth_error_offline_title,
        body = R.string.auth_error_offline_body,
    )

    // Validation carries wording written for a human by whoever raised it,
    // which for auth is the use cases checking email shape and OTP length.
    is AppError.Validation -> ErrorCopy(
        title = R.string.auth_error_generic_title,
        body = R.string.auth_error_generic_body,
        bodyOverride = (toAppError() as AppError.Validation).userMessage,
    )

    else -> ErrorCopy(
        title = R.string.auth_error_generic_title,
        body = R.string.auth_error_generic_body,
    )
}

/** Title and body as one line, for the inline notice cards. */
@Composable
fun Throwable.authErrorTitle(): String = stringResource(toAuthErrorCopy().title)

@Composable
fun Throwable.authErrorBody(): String {
    val copy = toAuthErrorCopy()
    return copy.bodyOverride ?: stringResource(copy.body)
}
