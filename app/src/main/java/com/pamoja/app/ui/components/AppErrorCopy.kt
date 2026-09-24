package com.pamoja.app.ui.components

import android.content.Context
import androidx.annotation.StringRes
import com.pamoja.app.R
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.BillingFailure
import com.pamoja.app.domain.error.SponsorshipFailure
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

    is AppError.Planning -> ErrorCopy(
        title = R.string.planning_unavailable_title,
        body = when (error.reason) {
            AppError.PlanningReason.NotEnabled -> R.string.planning_not_enabled_body
            AppError.PlanningReason.OrganizerSetup -> R.string.planning_organizer_setup_body
            AppError.PlanningReason.PremiumRequired -> R.string.planning_premium_required_body
            AppError.PlanningReason.ExistingPlan -> R.string.planning_existing_plan_body
            AppError.PlanningReason.RefreshRequired -> R.string.planning_refresh_body
        },
    )

    is AppError.Sponsorship -> when (error.reason) {
        SponsorshipFailure.InvalidGroup -> ErrorCopy(
            title = R.string.sponsorship_error_group_required_title,
            body = R.string.sponsorship_error_group_required_body,
        )
        SponsorshipFailure.GroupUnavailable -> ErrorCopy(
            title = R.string.sponsorship_error_group_unavailable_title,
            body = R.string.sponsorship_error_group_unavailable_body,
        )
        SponsorshipFailure.NotCurrentMember -> ErrorCopy(
            title = R.string.sponsorship_error_member_title,
            body = R.string.sponsorship_error_member_body,
        )
        SponsorshipFailure.NoActiveSubscription -> ErrorCopy(
            title = R.string.sponsorship_error_subscription_title,
            body = R.string.sponsorship_error_subscription_body,
        )
        SponsorshipFailure.SubscriptionAssignedElsewhere -> ErrorCopy(
            title = R.string.sponsorship_error_assigned_title,
            body = R.string.sponsorship_error_assigned_body,
        )
        SponsorshipFailure.AssignmentChanged -> ErrorCopy(
            title = R.string.sponsorship_assignment_changed_title,
            body = R.string.sponsorship_assignment_changed_body,
        )
        SponsorshipFailure.GroupAlreadySponsored -> ErrorCopy(
            title = R.string.sponsorship_error_already_title,
            body = R.string.sponsorship_error_already_body,
        )
    }

    is AppError.Billing -> when (error.reason) {
        BillingFailure.UserCancelled -> ErrorCopy(
            title = R.string.billing_cancelled_title,
            body = R.string.billing_cancelled_body,
        )
        BillingFailure.PaymentPending -> ErrorCopy(
            title = R.string.billing_pending_title,
            body = R.string.billing_pending_body,
        )
        BillingFailure.PurchaseNotAllowed -> ErrorCopy(
            title = R.string.billing_not_allowed_title,
            body = R.string.billing_not_allowed_body,
        )
        BillingFailure.ProductUnavailable -> ErrorCopy(
            title = R.string.billing_unavailable_title,
            body = R.string.billing_unavailable_body,
        )
        BillingFailure.AlreadyOwned -> ErrorCopy(
            title = R.string.billing_owned_title,
            body = R.string.billing_owned_body,
        )
        BillingFailure.NothingToRestore -> ErrorCopy(
            title = R.string.billing_nothing_to_restore_title,
            body = R.string.billing_nothing_to_restore_body,
        )
    }

    is AppError.ProviderUnavailable -> ErrorCopy(
        title = R.string.auth_error_google_unavailable_title,
        body = R.string.auth_error_google_unavailable_body,
        retryLabel = R.string.common_try_again,
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
    ValidationField.GroupNameDuplicate -> R.string.validation_group_name_duplicate
    ValidationField.WeeklyTargetInvalid -> R.string.validation_weekly_target
    ValidationField.MemberCapTooSmall -> R.string.validation_member_cap
    ValidationField.MemberCapBelowMemberCount -> R.string.validation_member_cap_below_count
    ValidationField.GroupNameTooLong -> R.string.validation_group_name_too_long
    ValidationField.InviteCodeMissing -> R.string.validation_invite_missing
    ValidationField.InviteCodeMalformed -> R.string.validation_invite_malformed
    ValidationField.DisplayNameMissing -> R.string.validation_display_name_missing
    ValidationField.NotAllowedToEditTarget -> R.string.validation_not_allowed_target
    ValidationField.AvatarUnreadable -> R.string.validation_avatar_unreadable
}
