package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.Adventure
import com.pamoja.app.domain.model.AdventureEntry

interface AdventureRepository {
    suspend fun entry(groupId: String): AdventureEntry
    suspend fun read(groupId: String, adventureId: String): Adventure
    suspend fun start(groupId: String, requestId: String, target: Long, timeZone: String)
    suspend fun sync(groupId: String, adventureId: String, join: Boolean)
    suspend fun pause(groupId: String, adventureId: String)
    suspend fun commit(groupId: String, adventure: Adventure, steps: Long)
    suspend fun finish(groupId: String, adventureId: String)
}
