package com.pamoja.app.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pamoja.app.domain.error.AppError
import java.io.IOException

/**
 * Translates Firebase exceptions into [AppError] at the data layer boundary.
 *
 * This is the only place that should know Firebase's error vocabulary. Nothing
 * above it should ever see a FirebaseFirestoreException, which is what stops
 * `PERMISSION_DENIED` and index-creation URLs reaching a snackbar.
 *
 * Mapping happens here rather than in the UI because the UI cannot tell the
 * difference between UNAVAILABLE (retry, probably offline) and FAILED_PRECONDITION
 * (a missing composite index, which no amount of retrying will fix).
 */
/**
 * Applies [toFirebaseAppError] to a failed Result.
 *
 * Lets a `runCatching { }` block keep its shape while still handing an
 * [AppError] upwards, rather than every method growing a try/catch.
 */
fun <T> Result<T>.mapFirebaseError(): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it.toFirebaseAppError()) },
    )

fun Throwable.toFirebaseAppError(): AppError = when (this) {

    is AppError -> this

    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            AppError.PermissionDenied(message ?: "Firestore permission denied", this)

        FirebaseFirestoreException.Code.NOT_FOUND ->
            AppError.NotFound(message ?: "Document not found", this)

        FirebaseFirestoreException.Code.ALREADY_EXISTS ->
            AppError.Conflict(message ?: "Already exists", this)

        // The SDK reports UNAVAILABLE both for a dead connection and for a
        // backend blip. Treated as offline because that is overwhelmingly the
        // real cause on a phone, and the copy for it is the more useful one.
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            AppError.Offline(this)

        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            AppError.Network(message ?: "Request timed out", this)

        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            AppError.SessionExpired(message ?: "Not authenticated", this)

        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            AppError.RateLimited(message ?: "Quota exhausted", this)

        // Nearly always a missing composite index in this codebase. Not
        // retryable: it needs a deploy, so offering Retry would be a lie.
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            AppError.Unknown(message ?: "Failed precondition, check Firestore indexes", this)

        FirebaseFirestoreException.Code.ABORTED ->
            AppError.Conflict(message ?: "Transaction aborted", this)

        else -> AppError.Unknown(message ?: "Firestore error: $code", this)
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    is FirebaseAuthRecentLoginRequiredException ->
        AppError.SessionExpired(message ?: "Recent login required", this)

    is FirebaseAuthInvalidUserException ->
        AppError.NotFound(message ?: "No such account", this)

    // Was previously mapped to Validation carrying an English sentence, which
    // put user-facing copy in the data layer and made it untranslatable.
    is FirebaseAuthInvalidCredentialsException -> when {
        message?.contains("expired", ignoreCase = true) == true ->
            AppError.Expired(message ?: "Credential expired", this)

        else -> AppError.InvalidCredentials(message ?: "Credential rejected", this)
    }

    is FirebaseAuthUserCollisionException ->
        AppError.Conflict(message ?: "Account already exists", this)

    is FirebaseTooManyRequestsException ->
        AppError.RateLimited(message ?: "Too many requests", this)

    // ── Transport ───────────────────────────────────────────────────────────

    is FirebaseNetworkException -> AppError.Offline(this)
    is IOException -> AppError.Offline(this)

    else -> AppError.Unknown(message ?: "Unexpected error", this)
}
