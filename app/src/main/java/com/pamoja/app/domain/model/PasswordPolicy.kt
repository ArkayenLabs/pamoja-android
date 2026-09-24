package com.pamoja.app.domain.model

/**
 * One rule for every place that creates a password.
 *
 * Sign-in deliberately does not use this policy: existing accounts may have
 * been created under Firebase's older six-character minimum and must remain
 * able to sign in. New accounts, linked email credentials and password changes
 * all use the stronger rule below.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    fun isAccepted(password: String): Boolean = password.length >= MIN_LENGTH
}
