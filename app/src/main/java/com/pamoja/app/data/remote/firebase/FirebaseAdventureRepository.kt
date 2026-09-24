package com.pamoja.app.data.remote.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.domain.model.*
import com.pamoja.app.domain.repository.AdventureRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pamoja.app.domain.usecase.adventureRecoveryWindows
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAdventureRepository @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
    private val health: HealthConnectReader,
    @param:ApplicationContext private val context: Context,
) : AdventureRepository {
    private val syncMutex = Mutex()
    private fun scope(groupId: String, id: String) = mapOf("groupId" to groupId, "adventureId" to id)
    private suspend fun call(name: String, input: Map<String, Any>): Map<*, *> {
        val uid = auth.currentUser?.uid ?: throw AdventureFailure("signed_out")
        val result = try { functions.getHttpsCallable(name).call(input).await().data as? Map<*, *>
            ?: throw AdventureFailure("invalid_response")
        } catch (error: FirebaseFunctionsException) {
            val reason = (error.details as? Map<*, *>)?.get("reason") as? String
            throw AdventureFailure(reason ?: when (error.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> "access_denied"
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "signed_out"
                else -> "unavailable"
            })
        }
        if (auth.currentUser?.uid != uid) throw AdventureFailure("signed_out")
        return result
    }

    override suspend fun entry(groupId: String): AdventureEntry {
        val data = call("getTogetherTrailEntry", mapOf("groupId" to groupId))
        return AdventureEntry(data["organizer"] == true, data["covered"] == true,
            (data["activeAdventureId"] ?: data["lastAdventureId"]) as? String)
    }
    override suspend fun read(groupId: String, adventureId: String): Adventure {
        val data = call("getTogetherTrail", scope(groupId, adventureId))
        val week = data["nextWeek"] as? Map<*, *> ?: throw AdventureFailure("invalid_response")
        val pace = data["pace"] as? Map<*, *> ?: throw AdventureFailure("invalid_response")
        val person = data["participation"] as? Map<*, *>
        fun Map<*, *>.number(key: String) = (get(key) as? Number)?.toLong() ?: throw AdventureFailure("invalid_response")
        val target = data.number("targetSteps")
        val steps = data.number("creditedSteps")
        val chapters = data.number("earnedChapters").toInt()
        if (target <= 0 || steps < 0 || chapters !in 0..5 || data["adventureId"] != adventureId) throw AdventureFailure("invalid_response")
        return Adventure(adventureId, target, steps, chapters, data["status"] == "completed",
            data["completedAtMillis"] != null, week["weekStart"] as String, week["timeZone"] as String,
            pace.number("plannedSteps"), (pace["approximateWeeks"] as? Number)?.toLong(),
            pace.number("awaitingMembers").toInt(), person?.let {
                AdventureParticipation(it["status"] == "active", it.number("lastSyncAtMillis"),
                    (it["commitmentSteps"] as? Number)?.toLong(), it.number("commitmentRevision"), it.number("baselineWindowStartMillis"))
            }, data.number("serverNowMillis"))
    }
    override suspend fun start(groupId: String, requestId: String, target: Long, timeZone: String) {
        call("startTogetherTrailAdventure", scope(groupId, requestId) + mapOf("targetSteps" to target, "timeZone" to timeZone))
    }
    override suspend fun sync(groupId: String, adventureId: String, join: Boolean) = withContext(Dispatchers.IO) {
      syncMutex.withLock {
        val uid = auth.currentUser?.uid ?: throw AdventureFailure("signed_out")
        val binding = deviceBinding()
        val input = scope(groupId, adventureId) + mapOf("bindingId" to binding, "join" to join)
        val windows: List<Long?> = if (join) listOf(null) else {
            val latest = read(groupId, adventureId)
            val person = latest.participation ?: throw AdventureFailure("join_required")
            if (!person.active || latest.completed) throw AdventureFailure("participation_changed")
            adventureRecoveryWindows(latest.serverNowMillis, person.baselineWindowStart)
        }
        var missing = false
        for ((index, windowStart) in windows.withIndex()) {
            if (index > 0) delay(2100) // Respect the server's ticket issuance limit.
            if (auth.currentUser?.uid != uid) throw AdventureFailure("signed_out")
            val request = if (windowStart == null) input else input + ("windowStartMillis" to windowStart)
            val ticket = try { call("prepareTogetherTrailSync", request) } catch (error: AdventureFailure) {
                // An older recovered window may have completed the trail; later
                // windows beyond its fixed finish cutoff cannot add new credit.
                if (!join && error.reason == "sync_not_ready") break
                if (error.reason != "sync_too_soon") throw error
                delay(2100)
                if (auth.currentUser?.uid != uid) throw AdventureFailure("signed_out")
                call("prepareTogetherTrailSync", request)
            }
            val start = Instant.ofEpochMilli((ticket["windowStartMillis"] as Number).toLong())
            val through = Instant.ofEpochMilli((ticket["observedThroughMillis"] as Number).toLong())
            val window = AdventureSourceWindow.containing(start)
            if (window.start != start) throw AdventureFailure("invalid_response")
            val observation = health.readAdventureSteps(window, through)
            if (observation == null) { missing = true; continue }
            if (auth.currentUser?.uid != uid) throw AdventureFailure("signed_out")
            call("submitTogetherTrailSync", input + mapOf("token" to (ticket["token"] as String), "steps" to observation.steps))
        }
        if (missing) throw AdventureFailure("health_unavailable")
        Unit
      }
    }
    @Synchronized private fun deviceBinding(): String {
        // Excluded from backup so a restored phone cannot impersonate the old source.
        val file = File(context.noBackupFilesDir, "adventure-device-binding")
        val existing = if (file.exists()) file.readText().trim() else null
        if (existing != null && runCatching { UUID.fromString(existing).toString() == existing }.getOrDefault(false)) return existing
        return UUID.randomUUID().toString().also { file.writeText(it) }
    }
    override suspend fun pause(groupId: String, adventureId: String) {
        call("pauseTogetherTrailContribution", scope(groupId, adventureId))
    }
    override suspend fun commit(groupId: String, adventure: Adventure, steps: Long) {
        call("saveTogetherTrailCommitment", scope(groupId, adventure.id) + mapOf("weekStart" to adventure.nextWeek,
            "steps" to steps, "expectedRevision" to (adventure.participation?.revision ?: 0L)))
    }
    override suspend fun finish(groupId: String, adventureId: String) {
        call("finalizeTogetherTrailAdventure", scope(groupId, adventureId))
    }
}
