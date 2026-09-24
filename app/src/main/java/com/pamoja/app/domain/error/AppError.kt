package com.pamoja.app.domain.error

/**
 * Every failure the app can surface, as a closed set.
 *
 * Before this, repositories returned `Result.failure(Exception(e.message))` and
 * screens pushed `exception.message` straight into a snackbar, so users were
 * shown things like PERMISSION_DENIED, UNAVAILABLE, and Firestore console URLs
 * telling them to create a composite index. Those strings are useless to a user
 * and they leak the shape of the backend.
 *
 * The split is deliberate:
 *
 *  - [technicalMessage] is for logs and Crashlytics. It keeps the original
 *    detail so a crash report is still diagnosable.
 *  - The user-facing wording lives in the UI layer, in `AppErrorCopy`, because
 *    copy is a design decision and the domain should not own it.
 *
 * Extends Exception so it drops into the existing `Result<T>` convention with
 * no change to any signature.
 */
sealed class AppError(
    val technicalMessage: String,
    cause: Throwable? = null,
) : Exception(technicalMessage, cause) {

    /** Whether offering a Retry button makes sense. Retrying NotFound does not. */
    open val isRetryable: Boolean get() = false

    /** The device has no usable connection. */
    class Offline(cause: Throwable? = null) :
        AppError("No network connection", cause) {
        override val isRetryable get() = true
    }

    /** Reached the network but the request failed or timed out. */
    class Network(detail: String = "Network request failed", cause: Throwable? = null) :
        AppError(detail, cause) {
        override val isRetryable get() = true
    }

    /**
     * The security rules refused this. Either a genuine bug in the rules or a
     * client attempting something it should not, so it is worth logging loudly
     * rather than quietly retrying.
     */
    class PermissionDenied(detail: String = "Permission denied by security rules", cause: Throwable? = null) :
        AppError(detail, cause)

    /** The document, group or invite code does not exist. */
    class NotFound(detail: String = "Not found", cause: Throwable? = null) :
        AppError(detail, cause)

    /** The action collided with the current state, e.g. the group filled up first. */
    class Conflict(detail: String, cause: Throwable? = null) :
        AppError(detail, cause)

    enum class PlanningReason { NotEnabled, OrganizerSetup, PremiumRequired, ExistingPlan, RefreshRequired }
    class Planning(val reason: PlanningReason, cause: Throwable? = null) :
        AppError("Planning unavailable: $reason", cause) {
        override val isRetryable get() = reason == PlanningReason.RefreshRequired
    }

    /** No signed-in user, or the token is no longer valid. */
    class SessionExpired(detail: String = "Session expired", cause: Throwable? = null) :
        AppError(detail, cause)

    /**
     * The credential presented was rejected: wrong password, wrong SMS code.
     *
     * Distinct from [Validation], which is about input we can judge ourselves
     * before asking a server. Only the server can say a password is wrong.
     */
    class InvalidCredentials(detail: String = "Credential rejected", cause: Throwable? = null) :
        AppError(detail, cause)

    /** A credential expired before it was used, typically an SMS code. */
    class Expired(detail: String = "Credential expired", cause: Throwable? = null) :
        AppError(detail, cause)

    /** Firebase is throttling us, typically SMS verification. */
    class RateLimited(detail: String = "Rate limited", cause: Throwable? = null) :
        AppError(detail, cause) {
        override val isRetryable get() = true
    }

    /**
     * Input the user can fix.
     *
     * Carries [field] rather than a sentence. The domain knows which rule
     * failed; the UI decides how to say it, which is what makes these messages
     * translatable and keeps copy out of pure Kotlin.
     */
    class Validation(val field: ValidationField) :
        AppError("Validation failed: $field")

    /**
     * A known business result from assigning subscription capacity to a group.
     *
     * [reason] is safe for UI decisions; [technicalMessage] is never shown to
     * the user. Keeping this typed prevents the paywall from parsing a backend
     * English sentence to distinguish a missing purchase from a group conflict.
     */
    class Sponsorship(
        val reason: SponsorshipFailure,
        detail: String = "Sponsorship failed: $reason",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val isRetryable: Boolean
            get() = reason == SponsorshipFailure.NoActiveSubscription ||
                reason == SponsorshipFailure.AssignmentChanged
    }

    /** A known Google Play / RevenueCat outcome with product-specific recovery. */
    class Billing(
        val reason: BillingFailure,
        detail: String = "Billing outcome: $reason",
        cause: Throwable? = null,
    ) : AppError(detail, cause)

    /**
     * A device sign-in provider could not open or return a credential.
     *
     * This deliberately does not claim that an account is missing. Credential
     * Manager can report the same condition when accounts need
     * reauthentication, Play services is unavailable, or provider settings
     * suppress a passive sign-in prompt.
     */
    class ProviderUnavailable(
        detail: String = "Authentication provider unavailable",
        cause: Throwable? = null,
    ) : AppError(detail, cause) {
        override val isRetryable get() = true
    }

    /** Anything unrecognised. Always worth reporting. */
    class Unknown(detail: String = "Unexpected error", cause: Throwable? = null) :
        AppError(detail, cause) {
        override val isRetryable get() = true
    }
}

/**
 * Widens any Throwable to an AppError.
 *
 * Anything already mapped passes through untouched, so this is safe to call at
 * a layer boundary without double-wrapping.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    else -> AppError.Unknown(message ?: "Unexpected error", this)
}
