package com.pamoja.app.util

import android.content.Context
import android.util.Log
import com.pamoja.app.BuildConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * RevenueCat setup, and keeping it pointed at the right account.
 *
 * Two jobs, kept together because getting the second one wrong is invisible.
 *
 * **Configuration** is a no-op without an API key. There is no key until the
 * Play merchant chain finishes, so this is the normal state today, and it must
 * not crash or log alarmingly on every launch.
 *
 * **Identity** is the part that quietly breaks things. RevenueCat assigns an
 * anonymous id per install unless told who the user is. Left anonymous, a
 * subscription is attached to a phone rather than an account: reinstalling, or
 * signing in on a second device, loses it, and "Restore purchases" returns
 * nothing while the user is still being charged. Calling [onSignedIn] with the
 * Firebase UID is what makes an entitlement follow the person.
 */
object BillingIdentity {

    private const val TAG = "BillingIdentity"

    /** True when an API key was supplied at build time. */
    val isEnabled: Boolean get() = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    /**
     * Configures the SDK, once, if there is a key.
     *
     * Safe to call unconditionally from Application.onCreate. Without a key it
     * returns immediately and `Purchases.isConfigured` stays false, which
     * `RevenueCatSubscriptionRepository` reads as "free tier, nothing for sale".
     */
    fun configure(context: Context) {
        if (!isEnabled) {
            Log.i(TAG, "No RevenueCat key; billing stays off and everyone is on Free")
            return
        }
        if (Purchases.isConfigured) return

        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY).build()
        )
    }

    /**
     * Points RevenueCat at this account.
     *
     * Called after sign-in and on every launch that starts already signed in,
     * since the SDK does not persist the alias across a reinstall. Idempotent:
     * logging in as the current user is a no-op inside the SDK.
     */
    fun onSignedIn(userId: String) {
        if (!isEnabled || !Purchases.isConfigured || userId.isBlank()) return
        if (Purchases.sharedInstance.appUserID == userId) return

        Purchases.sharedInstance.logIn(
            newAppUserID = userId,
            callback = object : com.revenuecat.purchases.interfaces.LogInCallback {
                override fun onReceived(
                    customerInfo: com.revenuecat.purchases.CustomerInfo,
                    created: Boolean,
                ) = Unit

                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                    // Not fatal. Entitlements resolve to Free until the next
                    // attempt, which is the safe direction to fail in.
                    Log.w(TAG, "RevenueCat logIn failed: ${error.message}")
                }
            },
        )
    }

    /**
     * Detaches the account on sign-out.
     *
     * Without this the next person to sign in on this device inherits the
     * previous user's entitlement, because the SDK is still aliased to them.
     * On a shared or handed-down phone that is someone else's subscription.
     */
    fun onSignedOut() {
        if (!isEnabled || !Purchases.isConfigured) return
        runCatching { Purchases.sharedInstance.logOut() }
            .onFailure { Log.w(TAG, "RevenueCat logOut failed: ${it.message}") }
    }
}
