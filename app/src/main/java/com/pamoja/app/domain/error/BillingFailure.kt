package com.pamoja.app.domain.error

/** Stable purchase outcomes that need different user recovery. */
enum class BillingFailure {
    UserCancelled,
    PaymentPending,
    PurchaseNotAllowed,
    ProductUnavailable,
    AlreadyOwned,
    NothingToRestore,
}
