package com.pamoja.app.domain.error

/** Stable product outcomes from the server-owned sponsorship operation. */
enum class SponsorshipFailure {
    InvalidGroup,
    GroupUnavailable,
    NotCurrentMember,
    NoActiveSubscription,
    SubscriptionAssignedElsewhere,
    AssignmentChanged,
    GroupAlreadySponsored,
}
