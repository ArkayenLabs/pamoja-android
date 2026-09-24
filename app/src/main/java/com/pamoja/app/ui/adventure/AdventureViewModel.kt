package com.pamoja.app.ui.adventure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pamoja.app.di.TogetherTrailEnabled
import com.pamoja.app.domain.model.*
import com.pamoja.app.domain.repository.AdventureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class AdventureUiState(val busy: Boolean = false, val entry: AdventureEntry? = null,
    val adventure: Adventure? = null, val error: String? = null, val pendingTarget: Long? = null)

@HiltViewModel
class AdventureViewModel @Inject constructor(
    private val repository: AdventureRepository,
    private val saved: SavedStateHandle,
    @TogetherTrailEnabled private val enabled: Boolean,
) : ViewModel() {
    private val groupId = saved.get<String>("groupId").orEmpty()
    private val mutable = MutableStateFlow(AdventureUiState(pendingTarget = saved.get<Long>("startTarget")))
    val state = mutable.asStateFlow()

    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (!enabled) throw AdventureFailure("feature_disabled")
                block()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                val reason = (error as? AdventureFailure)?.reason ?: "unavailable"
                mutable.update { it.copy(error = reason,
                    entry = if (reason in listOf("access_denied", "signed_out")) null else it.entry,
                    adventure = if (reason in listOf("access_denied", "signed_out")) null else it.adventure) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun reload() {
        val entry = repository.entry(groupId)
        val adventure = entry.adventureId?.let { repository.read(groupId, it) }
        mutable.update { it.copy(entry = entry, adventure = adventure) }
    }
    fun refresh() = action { reload() }
    fun start(target: Long) = action {
        require(target in 1_000L..11_200_000L)
        // A pending start retains BOTH its ID and its target across process death.
        // Editing the form cannot silently retarget a retry of a committed start.
        val id = saved.get<String>("startRequestId") ?: UUID.randomUUID().toString().also {
            saved["startRequestId"] = it
            saved["startTarget"] = target
        }
        mutable.update { it.copy(pendingTarget = saved.get<Long>("startTarget")) }
        repository.start(groupId, id, saved.get<Long>("startTarget") ?: target, ZoneId.systemDefault().id)
        saved.remove<String>("startRequestId")
        saved.remove<Long>("startTarget")
        mutable.update { it.copy(pendingTarget = null) }
        reload()
    }
    fun sync() = action {
        val adventure = mutable.value.adventure ?: return@action
        try {
            repository.sync(groupId, adventure.id, adventure.participation?.active != true)
        } catch (error: AdventureFailure) {
            // Recovery may have saved some readable days. Show confirmed totals
            // alongside the missing-data message instead of stale progress.
            if (error.reason == "health_unavailable") reload()
            throw error
        }
        reload()
    }
    fun pause() = action {
        val adventure = mutable.value.adventure ?: return@action
        repository.pause(groupId, adventure.id)
        reload()
    }
    fun commit(steps: Long) = action {
        val adventure = mutable.value.adventure ?: return@action
        repository.commit(groupId, adventure, steps)
        reload()
    }
    fun finish() = action {
        val adventure = mutable.value.adventure ?: return@action
        repository.finish(groupId, adventure.id)
        reload()
    }
}
