package com.pamoja.app.ui.components

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.toAppError

/**
 * What the user actually reads when something fails.
 *
 * [title] states what happened, [body] says what to do about it. Both are
 * plain language: no error codes, no "an error occurred", and never the
 * original exception text.
 */
data class ErrorCopy(
    val title: String,
    val body: String,
    /** Null when retrying cannot help, which is when the Retry button is hidden. */
    val retryLabel: String? = null,
)

/**
 * Maps an error to its wording.
 *
 * Deliberately a UI-layer concern. The domain classifies failures; how they are
 * phrased is a design decision, and keeping it here means copy can be reworded
 * without touching a repository.
 */
fun Throwable.toErrorCopy(): ErrorCopy = when (val error = toAppError()) {

    is AppError.Offline -> ErrorCopy(
        title = "You are offline",
        body = "Check your connection. Your steps keep counting and will sync when you are back.",
        retryLabel = "Try again",
    )

    is AppError.Network -> ErrorCopy(
        title = "That took too long",
        body = "The connection dropped partway. Give it another go.",
        retryLabel = "Try again",
    )

    is AppError.PermissionDenied -> ErrorCopy(
        title = "You cannot do that",
        body = "You may have been removed from this group, or it no longer exists.",
    )

    is AppError.NotFound -> ErrorCopy(
        title = "Not found",
        body = "This may have been deleted, or the link may be wrong.",
    )

    is AppError.Conflict -> ErrorCopy(
        title = "Something changed",
        body = "Someone got there first. Refresh to see how things stand now.",
        retryLabel = "Refresh",
    )

    is AppError.SessionExpired -> ErrorCopy(
        title = "Please sign in again",
        body = "For your security, sessions do not last forever. Nothing has been lost.",
        retryLabel = "Sign in",
    )

    is AppError.RateLimited -> ErrorCopy(
        title = "Too many attempts",
        body = "Wait a few minutes before trying again.",
        retryLabel = "Try again",
    )

    // Already written for a human by whoever raised it.
    is AppError.Validation -> ErrorCopy(
        title = "Check that again",
        body = error.userMessage,
    )

    is AppError.Unknown -> ErrorCopy(
        title = "Something went wrong",
        body = "That did not work, and we are not sure why. Trying again often helps.",
        retryLabel = "Try again",
    )
}

/** One-liner for a snackbar, where there is no room for a title and a body. */
fun Throwable.toSnackbarMessage(): String = toErrorCopy().let { copy ->
    when (toAppError()) {
        is AppError.Validation -> copy.body
        else -> "${copy.title}. ${copy.body}"
    }
}
