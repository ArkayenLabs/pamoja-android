package com.pamoja.app.domain.model

/** The UUID v4 format used by every Pamoja group and by the sponsorship API. */
object PamojaGroupId {
    private val pattern = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE,
    )

    fun isValid(value: String): Boolean = pattern.matches(value)
}
