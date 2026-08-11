package com.pamoja.app.ui.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.data.local.licenses.OssLicense
import com.pamoja.app.data.local.licenses.OssLicenseReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LicensesUiState(
    val isLoading: Boolean = true,
    val licenses: List<OssLicense> = emptyList(),
    /** Non-null while one licence is open, which is the detail view. */
    val selected: OssLicense? = null,
)

/**
 * The open source licences the app is obliged to show.
 *
 * A ViewModel rather than reading in the composable, because the release blob is
 * around half a megabyte and parsing it on the main thread would drop frames on
 * the way in. It survives configuration changes too, so rotating inside a
 * licence does not send the reader back to the top of a 300-entry list.
 */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val reader: OssLicenseReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                licenses = reader.read(),
            )
        }
    }

    fun select(license: OssLicense) {
        _uiState.value = _uiState.value.copy(selected = license)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selected = null)
    }
}
