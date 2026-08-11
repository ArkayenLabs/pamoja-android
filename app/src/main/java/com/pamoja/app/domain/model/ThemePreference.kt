package com.pamoja.app.domain.model

/**
 * Which theme the user asked for, as distinct from which one is showing.
 *
 * [System] is the default and is not the same as storing whichever mode the
 * phone happened to be in at install: it keeps following the phone afterwards,
 * including the automatic evening switch.
 */
enum class ThemePreference {
    System,
    Light,
    Dark;

    companion object {
        /** Unknown or absent values fall back to [System] rather than throwing. */
        fun fromName(value: String?): ThemePreference =
            entries.firstOrNull { it.name == value } ?: System
    }
}
