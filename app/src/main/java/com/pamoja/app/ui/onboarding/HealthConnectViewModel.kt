package com.pamoja.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.pamoja.app.data.local.health.StepCounterManager
import com.pamoja.app.data.local.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    val stepCounterManager: StepCounterManager,
    val userPreferences: UserPreferences
) : ViewModel()