package com.pamoja.app.domain.error

/**
 * What a user got wrong, as a value rather than a sentence.
 *
 * Use cases know *which* rule failed but must not know how to phrase it: the
 * domain is pure Kotlin with no access to string resources, and copy is a
 * design decision besides. Naming the failure here and phrasing it in the UI
 * keeps both sides honest and makes every message translatable.
 *
 * Only for things a user can actually fix by editing what they typed.
 * Impossible states such as a blank user ID are not validation, they are bugs
 * or an expired session, and are reported as such.
 */
enum class ValidationField {
    EmailMissing,
    EmailMalformed,
    PasswordMissing,
    PasswordTooShort,
    PhoneMissingCountryCode,
    PhoneMalformed,
    OtpIncomplete,
    GroupNameMissing,
    WeeklyTargetInvalid,
    MemberCapTooSmall,
    InviteCodeMissing,
    InviteCodeMalformed,
    DisplayNameMissing,
    NotAllowedToEditTarget,

    /** The chosen image could not be decoded, so it is not a photo we can use. */
    AvatarUnreadable,
}
