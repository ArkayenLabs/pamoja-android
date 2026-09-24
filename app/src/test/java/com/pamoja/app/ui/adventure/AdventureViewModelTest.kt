package com.pamoja.app.ui.adventure

import androidx.lifecycle.SavedStateHandle
import com.pamoja.app.domain.model.*
import com.pamoja.app.domain.repository.AdventureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdventureViewModelTest {
    @Test fun `disabled rollout makes no backend request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Fake()
            val vm = AdventureViewModel(repo, SavedStateHandle(mapOf("groupId" to "group")), false)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(0, repo.reads)
            assertEquals("feature_disabled", vm.state.value.error)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `lost start response preserves request and target across recreation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Fake()
            val saved = SavedStateHandle(mapOf("groupId" to "group"))
            val first = AdventureViewModel(repo, saved, true)
            first.start(200000)
            advanceUntilIdle()
            assertEquals(200000L, first.state.value.pendingTarget)
            val second = AdventureViewModel(repo, saved, true)
            second.start(400000)
            advanceUntilIdle()
            assertEquals(repo.starts[0], repo.starts[1])
            assertEquals(200000L, repo.starts[1].second)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `refresh replaces paid coverage and revocation clears private state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Fake()
            val vm = AdventureViewModel(repo, SavedStateHandle(mapOf("groupId" to "group")), true)
            vm.refresh()
            advanceUntilIdle()
            assertFalse(vm.state.value.entry!!.covered)
            repo.covered = true
            vm.refresh()
            advanceUntilIdle()
            assertTrue(vm.state.value.entry!!.covered)
            repo.denied = true
            vm.refresh()
            advanceUntilIdle()
            assertNull(vm.state.value.entry)
            assertNull(vm.state.value.adventure)
        } finally { Dispatchers.resetMain() }
    }
    private class Fake : AdventureRepository {
        var reads = 0
        var covered = false
        var denied = false
        val starts = mutableListOf<Pair<String, Long>>()
        override suspend fun entry(groupId: String): AdventureEntry {
            reads++
            if (denied) throw AdventureFailure("access_denied")
            return AdventureEntry(true, covered, null)
        }
        override suspend fun read(groupId: String, adventureId: String): Adventure = error("No adventure")
        override suspend fun start(groupId: String, requestId: String, target: Long, timeZone: String) {
            starts += requestId to target
            throw AdventureFailure("unavailable")
        }
        override suspend fun sync(groupId: String, adventureId: String, join: Boolean) = Unit
        override suspend fun pause(groupId: String, adventureId: String) = Unit
        override suspend fun commit(groupId: String, adventure: Adventure, steps: Long) = Unit
        override suspend fun finish(groupId: String, adventureId: String) = Unit
    }
}
