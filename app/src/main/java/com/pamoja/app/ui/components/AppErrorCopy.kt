package com.pamoja.app.ui.components

import android.content.Context
import androidx.annotation.StringRes
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.ValidationField
import com.pamoja.app.domain.error.toAppError

/**
 * What the user actually reads when something fails.
 *
 * Resource IDs rather than resolved strings, so the copy is translatable and so
 * nothing here can accidentally carry an exception message. [title] states what
 * happened, [body] says what to do about it.
 */
data class ErrorCopy(
    @StringRes val title: Int,
    @StringRes val body: Int,
    /** Null when retrying cannot help, which is when Retry is hidden. */
    @StringRes val retryLabel: Int? = null,
    /**
     * Only set for validation failures, where the message was written for a
     * human by whoever raised it and there is no resource for it. Overrides
     * [body] when present.
     */
    val bodyOverride: String? = null,
) {
    fun body(context: Context): String = bodyOverride ?: context.getString(body)
}

/**
 * Maps an error to its wording.
 *
 * Deliberately a UI-layer concern. The domain classifies failures; how they are
 * phrased is a design decision, and keeping it here means copy can be reworded
 * or translated without touching a repository.
 */
fun Throwable.toErrorCopy(): ErrorCopy = when (val error = toAppError()) {

    is AppError.Offline -> ErrorCopy(
        title = R.string.error_offline_title,
        body = R.string.error_offline_body,
        retryLabel = R.string.common_try_again,
    )

    is AppError.Network -> ErrorCopy(
        title = R.string.error_network_title,
        body = R.string.error_network_body,
        retryLabel = R.string.common_try_again,
    )

    is AppError.PermissionDenied -> ErrorCopy(
        title = R.string.error_permission_title,
        body = R.string.error_permission_body,
    )

    is AppError.NotFound -> ErrorCopy(
        title = R.string.error_not_found_title,
        body = R.string.error_not_found_body,
    )

    is AppError.Conflict -> ErrorCopy(
        title = R.string.error_conflict_title,
        body = R.string.error_conflict_body,
        retryLabel = R.string.common_refresh,
    )

    is AppError.SessionExpired -> ErrorCopy(
        title = R.string.error_session_title,
        body = R.string.error_session_body,
        retryLabel = R.string.common_sign_in,
    )

    is AppError.RateLimited -> ErrorCopy(
        title = R.string.error_rate_limited_title,
        body = R.string.error_rate_limited_body,
        retryLabel = R.string.common_try_again,
    )

    is AppError.InvalidCredentials -> ErrorCopy(
        title = R.string.error_credentials_title,
        body = R.string.error_credentials_body,
    )

    is AppError.Expired -> ErrorCopy(
        title = R.string.error_expired_title,
        body = R.string.error_expired_body,
        retryLabel = R.string.common_try_again,
    )

    is AppError.Validation -> ErrorCopy(
        title = R.string.error_validation_title,
        body = error.field.messageRes(),
    )

    is AppError.NoProviderAccount -> ErrorCopy(
        title = R.string.auth_error_no_google_account_title,
        body = R.string.auth_error_no_google_account_body,
    )

    is AppError.Unknown -> ErrorCopy(
        title = R.string.error_unknown_title,
        body = R.string.error_unknown_body,
        retryLabel = R.string.common_try_again,
    )
}

/**
 * One line for a snackbar, where there is no room for a title and a body.
 *
 * Takes a Context because snackbars are shown from LaunchedEffect, which is not
 * a composable scope and cannot call stringResource.
 */
fun Throwable.toSnackbarMessage(context: Context): String {
    val copy = toErrorCopy()
    return when (toAppError()) {
        is AppError.Validation -> copy.body(context)
        else -> context.getString(
            R.string.error_snackbar_format,
            context.getString(copy.title),
            copy.body(context),
        )
    }
}

/**
 * The sentence for each validation failure.
 *
 * This mapping is the whole point of ValidationField: the rule is decided in
 * pure Kotlin, and the wording lives here where it can be translated. Exhaustive
 * on purpose, with no else branch, so adding a field to the enum fails the build
 * until someone writes copy for it.
 */
@StringRes
fun ValidationField.messageRes(): Int = when (this) {
    ValidationField.EmailMissing -> R.string.validation_email_missing
    ValidationField.EmailMalformed -> R.string.validation_email_malformed
    ValidationField.PasswordMissing -> R.string.validation_password_missing
    ValidationField.PasswordTooShort -> R.string.validation_password_too_short
    ValidationField.PhoneMissingCountryCode -> R.string.validation_phone_country_code
    ValidationField.PhoneMalformed -> R.string.validation_phone_malformed
    ValidationField.OtpIncomplete -> R.string.validation_otp_incomplete
    ValidationField.GroupNameMissing -> R.string.validation_group_name_missing
    ValidationField.WeeklyTargetInvalid -> R.string.validation_weekly_target
    ValidationField.MemberCapTooSmall -> R.string.validation_member_cap
    ValidationField.InviteCodeMissing -> R.string.validation_invite_missing
    ValidationField.InviteCodeMalformed -> R.string.validation_invite_malformed
    ValidationField.DisplayNameMissing -> R.string.validation_display_name_missing
    ValidationField.NotAllowedToEditTarget -> R.string.validation_not_allowed_target
}
