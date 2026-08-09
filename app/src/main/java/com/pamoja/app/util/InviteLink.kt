package com.pamoja.app.util

/**
 * Parsing and building of Pamoja invite links.
 *
 * Canonical (verified Android App Link):
 *     https://www.arkayenlabs.com/pamoja/join/{code}
 *
 * Legacy (custom scheme, still accepted so links shared during the closed test
 * keep working, never generated for new invites):
 *     pamoja://join/{code}
 *
 * `code` is opaque here on purpose. Today it is the group's UUID; it will become
 * a short, rotatable invite code. Keeping this parser agnostic means the switch
 * needs no change to link handling.
 */
object InviteLink {

    const val HOST = "www.arkayenlabs.com"
    private const val PATH_PREFIX = "/pamoja/join"

    /** Builds the canonical shareable link for an invite code. */
    fun build(code: String): String = "https://$HOST$PATH_PREFIX/$code"

    /**
     * The legacy `pamoja://join/{code}` form.
     *
     * Firestore stores each group's `inviteLink` field in this format, so a
     * lookup has to be done with it regardless of which link form the user
     * actually arrived from.
     */
    fun legacyLink(code: String): String = "pamoja://join/$code"

    /**
     * Extracts the invite code from any supported link form, or null if the
     * input is not a Pamoja invite link.
     *
     * Tolerates the messy reality of pasted links: surrounding whitespace,
     * a missing scheme, query strings, trailing slashes, and the apex host.
     */
    fun parseCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val input = raw.trim()

        // Legacy: pamoja://join/{code}
        if (input.startsWith("pamoja://join/", ignoreCase = true)) {
            return input.removePrefix("pamoja://join/")
                .substringBefore('?')
                .substringBefore('#')
                .trim('/')
                .takeIf { it.isNotBlank() }
        }

        // https(://)www.arkayenlabs.com/pamoja/join/{code}, scheme optional
        val withoutScheme = input
            .removePrefix("https://")
            .removePrefix("http://")

        val hostAndPath = withoutScheme.substringBefore('?').substringBefore('#')
        val slash = hostAndPath.indexOf('/')
        if (slash == -1) return null

        val host = hostAndPath.substring(0, slash).lowercase()
        if (host != HOST && host != "arkayenlabs.com") return null

        val path = hostAndPath.substring(slash)
        if (!path.startsWith(PATH_PREFIX)) return null

        return path.removePrefix(PATH_PREFIX)
            .trim('/')
            .takeIf { it.isNotBlank() && !it.contains('/') }
    }

    /** True if [raw] is any recognised Pamoja invite link. */
    fun isInviteLink(raw: String?): Boolean = parseCode(raw) != null
}
