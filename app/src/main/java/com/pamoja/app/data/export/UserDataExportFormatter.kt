package com.pamoja.app.data.export

import com.pamoja.app.domain.model.UserDataExport
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Turns an export into JSON.
 *
 * JSON rather than CSV because the data is not one table: a profile, a list of
 * memberships, a list of groups and a year of daily counts do not share a
 * shape, and flattening them into one sheet would lose which group a number
 * belonged to. JSON is also what "machine readable" means to a regulator.
 *
 * Lives in the data layer because it uses org.json, an Android class the domain
 * may not touch. It is a boundary format, in the same sense as a DTO.
 *
 * Written with org.json rather than string concatenation so that a group named
 * `Bob's "Walkers"` produces valid output instead of a broken file the user
 * only discovers is broken later.
 */
class UserDataExportFormatter @Inject constructor() {

    fun toJson(export: UserDataExport): String {
        val root = JSONObject()

        root.put("export_format_version", FORMAT_VERSION)
        root.put("exported_at", isoUtc(export.exportedAt))
        root.put("app", "Pamoja")

        root.put(
            "profile",
            JSONObject().apply {
                put("user_id", export.user.userId)
                put("name", export.user.name)
                // Absent rather than null, since these are genuinely optional
                // and a null reads as "we hold an empty value for you".
                export.user.age?.let { put("age", it) }
                export.user.height?.let { put("height_cm", it) }
                export.user.weight?.let { put("weight_kg", it) }
            }
        )

        root.put(
            "groups",
            JSONArray().apply {
                export.groups.forEach { group ->
                    val membership = export.memberships.firstOrNull { it.groupId == group.groupId }
                    put(
                        JSONObject().apply {
                            put("group_id", group.groupId)
                            put("name", group.name)
                            put("weekly_target_steps", group.weeklyTarget)
                            put("member_count", group.memberCount)
                            put("you_are_admin", group.adminId == export.user.userId)
                            membership?.let { put("joined_at", isoUtc(it.joinedAt)) }
                        }
                    )
                }
            }
        )

        root.put(
            "daily_steps",
            JSONArray().apply {
                export.steps.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("date", entry.date)
                            put("steps", entry.stepCount)
                        }
                    )
                }
            }
        )

        // Indented, because a person opening this file should be able to read
        // it. Machine readability is not the only requirement.
        return root.toString(2)
    }

    private fun isoUtc(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toInstant()
        )

    private companion object {
        /** Bump when the shape changes, so an old file stays interpretable. */
        const val FORMAT_VERSION = 1
    }
}
