package com.pamoja.app.ui.update

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.BuildConfig
import com.pamoja.app.R
import com.pamoja.app.domain.usecase.ObserveUpdateRequiredUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** False until the server says this install is too old. */
@HiltViewModel
class UpdateGateViewModel @Inject constructor(
    observeUpdateRequired: ObserveUpdateRequiredUseCase,
) : ViewModel() {
    val updateRequired: StateFlow<Boolean> =
        observeUpdateRequired(BuildConfig.VERSION_CODE.toLong())
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

/**
 * Covers the whole app when this version has been retired.
 *
 * Deliberately built from MaterialTheme roles only, not the kit and not the
 * terracotta components: it has to render under whichever theme a given
 * release ships, including the 1.0 line, so it can be the same code in both.
 *
 * Back leaves the app rather than revealing what is underneath.
 */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit) {
    val activity = LocalActivity.current
    BackHandler { activity?.finish() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Text(stringResource(R.string.update_required_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.update_required_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.update_required_action))
                }
            }
        }
    }
}
