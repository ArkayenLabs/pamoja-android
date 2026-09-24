package com.pamoja.app.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.pamoja.app.domain.model.AdventureExportRecord
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Fails the export on an incomplete page or identity change. Never silently
 * emits a supposedly complete export when a backend operation is unavailable. */
class FirebaseAdventureExporter @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) {
    suspend fun export(userId: String, groupIds: List<String>): List<AdventureExportRecord> {
        val result = mutableListOf<AdventureExportRecord>()
        requireIdentity(userId)
        for (groupId in groupIds.distinct()) {
            val base = mapOf("groupId" to groupId)
            for (adventure in pages(userId, base + ("kind" to "adventures"))) {
                val adventureId = adventure["id"] as? String ?: error("Missing adventure ID")
                val scope = base + ("adventureId" to adventureId)
                result += AdventureExportRecord(groupId, adventureId, "adventure", fields = adventure)
                for (kind in listOf("participant", "commitments", "segments")) {
                    for (record in pages(userId, scope + ("kind" to kind))) {
                        result += AdventureExportRecord(groupId, adventureId, kind, fields = record)
                        if (kind == "segments") {
                            val segmentId = record["id"] as? String ?: error("Missing segment ID")
                            for (credit in pages(userId, scope + mapOf("kind" to "credits", "segmentId" to segmentId))) {
                                result += AdventureExportRecord(groupId, adventureId, "credits", segmentId, credit)
                            }
                        }
                    }
                }
            }
        }
        requireIdentity(userId)
        return result
    }

    private suspend fun pages(userId: String, input: Map<String, String>): List<Map<String, Any?>> {
        val records = mutableListOf<Map<String, Any?>>()
        var after: String? = null
        val seen = mutableSetOf<String>()
        do {
            requireIdentity(userId)
            val request = after?.let { input + ("after" to it) } ?: input
            val page = functions.getHttpsCallable("exportTogetherTrailPage").call(request).await().data as? Map<*, *>
                ?: error("Invalid adventure export page")
            requireIdentity(userId)
            val values = page["records"] as? List<*> ?: error("Missing export records")
            records += values.map { value ->
                val record = value as? Map<*, *> ?: error("Invalid export record")
                record.entries.associate { (key, field) ->
                    (key as? String ?: error("Invalid export field")) to field
                }
            }
            check(page.containsKey("next")) { "Missing export cursor" }
            after = page["next"]?.let { it as? String ?: error("Invalid export cursor") }
            after?.let { check(seen.add(it)) { "Repeated export cursor" } }
        } while (after != null)
        return records
    }

    private fun requireIdentity(userId: String) {
        check(auth.currentUser?.uid == userId) { "Account changed during export" }
    }
}
