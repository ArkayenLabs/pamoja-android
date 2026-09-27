package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveUpdateRequiredUseCaseTest {

    private fun useCase(vararg minimums: Long) = ObserveUpdateRequiredUseCase(
        object : AppConfigRepository {
            override fun minimumSupportedVersionCode(): Flow<Long> = flowOf(*minimums.toTypedArray())
        },
    )

    @Test
    fun `nothing is gated while no minimum is set`() = runTest {
        assertEquals(listOf(false), useCase(0L)(installedVersionCode = 16).toList())
    }

    @Test
    fun `an install at the minimum keeps running`() = runTest {
        assertEquals(listOf(false), useCase(16L)(installedVersionCode = 16).toList())
    }

    @Test
    fun `an install below the minimum is gated`() = runTest {
        assertEquals(listOf(true), useCase(17L)(installedVersionCode = 16).toList())
    }

    /** The cached value comes first; a fresh fetch can then raise it. */
    @Test
    fun `a fetched minimum overrides the cached one`() = runTest {
        assertEquals(listOf(false, true), useCase(0L, 17L)(installedVersionCode = 16).toList())
    }

    @Test
    fun `an unchanged answer is not repeated`() = runTest {
        assertEquals(listOf(false), useCase(0L, 10L)(installedVersionCode = 16).toList())
    }
}
