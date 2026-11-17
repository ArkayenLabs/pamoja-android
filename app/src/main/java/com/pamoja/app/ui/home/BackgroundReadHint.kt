package com.pamoja.app.ui.home

/**
 * Steps are connected but Health Connect's background read is not, so they
 * only reach the group while Pamoja is open. Settings has the permanent
 * switch; this is the one visible nudge towards it, until it is dismissed.
 * Never where the phone cannot grant it (Health Connect reports the feature
 * as unavailable), since the button would lead nowhere.
 */
internal fun backgroundHintNeeded(
    inAGroup: Boolean,
    stepsGranted: Boolean,
    backgroundSupported: Boolean,
    backgroundGranted: Boolean,
    dismissed: Boolean?,
): Boolean = inAGroup && stepsGranted && backgroundSupported && !backgroundGranted && dismissed == false
