package com.pamoja.app.util

import android.content.Context
import android.util.Log
import com.pamoja.app.BuildConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    enum class SessionState {
        Disabled,
        SignedOut,
        Connecting,
        Ready,
        Unavailable,
    }

    private val hasApiKey: Boolean
        get() = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    /** Sales require an explicit switch as well as valid SDK configuration. */
    val isSalesEnabled: Boolean
        get() = BuildConfig.SUBSCRIPTION_SALES_ENABLED && hasApiKey

    @Volatile
    private var signedInUserId: String? = null
    @Volatile private var sessionGeneration: Long = 0

    val sessionToken: String?
        get() = if (isReady) "$sessionGeneration:$signedInUserId" else null

    private val _sessionState = MutableStateFlow(initialSessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /** True only when RevenueCat is pointed at the currently authenticated user. */
    val isReady: Boolean
        get() {
            val userId = signedInUserId ?: return false
            return _sessionState.value == SessionState.Ready &&
                Purchases.isConfigured &&
                Purchases.sharedInstance.appUserID == userId
        }

    /**
     * Configures the SDK for a known Firebase user, or switches an existing
     * SDK session to that user.
     *
     * Pamoja requires authentication before purchase, so creating an anonymous
     * RevenueCat customer first adds identity-merging risk without adding any
     * useful capability. The Firebase UID is supplied on the first configure.
     */
    fun onSignedIn(context: Context, userId: String) {
        if (userId.isBlank()) return
        if (signedInUserId != userId) sessionGeneration++
        signedInUserId = userId

        if (!BuildConfig.SUBSCRIPTION_SALES_ENABLED) {
            _sessionState.value = SessionState.Disabled
            Log.i(TAG, "Subscription sales switch is off; everyone stays on Free")
            return
        }
        if (!hasApiKey) {
            _sessionState.value = SessionState.Disabled
            Log.w(TAG, "Subscription sales were enabled without a RevenueCat key")
            return
        }

        _sessionState.value = SessionState.Connecting

        if (!Purchases.isConfigured) {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY)
                    .appUserID(userId)
                    .build()
            )
            _sessionState.value = SessionState.Ready
            return
        }

        if (Purchases.sharedInstance.appUserID == userId) {
            _sessionState.value = SessionState.Ready
            return
        }

        Purchases.sharedInstance.logIn(
            newAppUserID = userId,
            callback = object : com.revenuecat.purchases.interfaces.LogInCallback {
                override fun onReceived(
                    customerInfo: com.revenuecat.purchases.CustomerInfo,
                    created: Boolean,
                ) {
                    if (signedInUserId == userId &&
                        Purchases.sharedInstance.appUserID == userId
                    ) {
                        _sessionState.value = SessionState.Ready
                    }
                }

                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                    if (signedInUserId == userId) {
                        _sessionState.value = SessionState.Unavailable
                    }
                    Log.w(TAG, "RevenueCat logIn failed: ${error.message}")
                }
            },
        )
    }

    /**
     * Makes billing unavailable immediately on sign-out.
     *
     * We deliberately do not call RevenueCat `logOut()`: that creates a new
     * anonymous customer. The repository observes [sessionState], drops to Free
     * immediately, and the next authenticated user is selected by [onSignedIn].
     */
    fun onSignedOut() {
        sessionGeneration++
        signedInUserId = null
        _sessionState.value = initialSessionState()
    }

    private fun initialSessionState(): SessionState =
        if (isSalesEnabled) SessionState.SignedOut else SessionState.Disabled
}
