package com.pamoja.app.data.local.licenses

import android.content.Context
import com.pamoja.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One dependency and where its licence lives.
 *
 * [body] is whatever the dependency's POM declared, which is usually a link
 * rather than the licence in full. That is not a shortcut on our part: it is
 * the only thing the build has to work with.
 */
data class OssLicense(
    val name: String,
    val body: String,
) {
    /**
     * True when the body is only a link, which is the common case.
     *
     * Decided by looking at the content rather than by guessing from length,
     * since a few dependencies do ship their full text and those must render as
     * text rather than as a broken-looking button.
     */
    val isLink: Boolean
        get() = body.lineSequence()
            .filter { it.isNotBlank() }
            .let { lines ->
                val list = lines.toList()
                list.isNotEmpty() && list.all { it.trim().startsWith("http") }
            }

    /** The links in [body], for the open action. Empty when this is real text. */
    val links: List<String>
        get() = if (isLink) body.lines().map { it.trim() }.filter { it.isNotBlank() } else emptyList()
}

/**
 * Reads the licence data the OSS licences Gradle plugin generates at build time.
 *
 * Generated rather than hand-maintained on purpose: a hand-written list is out
 * of date the moment a transitive dependency changes, and being out of date here
 * is a licence violation rather than a cosmetic problem. The plugin reads the
 * POM of everything that actually ships, so the list cannot drift from the APK.
 *
 * **Debug builds contain a placeholder, not the real list.** The plugin only has
 * a dependency list to work from on a variant where AGP produces one, so a debug
 * build shows a single entry explaining exactly that. Verify this screen on a
 * release build.
 *
 * Format, which is the plugin's own and is not otherwise documented:
 * `third_party_license_metadata` holds one `<byteOffset>:<byteLength> <name>`
 * per line, indexing into the single `third_party_licenses` blob.
 */
class OssLicenseReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun read(): List<OssLicense> = withContext(Dispatchers.IO) {
        val blob = context.resources
            .openRawResource(R.raw.third_party_licenses)
            .use { it.readBytes() }

        context.resources
            .openRawResource(R.raw.third_party_license_metadata)
            .use { it.bufferedReader().readLines() }
            .mapNotNull { parseEntry(it, blob) }
            // Several dependencies can declare the same name, and repeating it
            // in the list tells the reader nothing.
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Returns null for anything malformed rather than throwing.
     *
     * A single unparseable line must not take the whole screen down: this is a
     * legally required disclosure, so showing all but one entry beats showing an
     * error.
     */
    private fun parseEntry(line: String, blob: ByteArray): OssLicense? {
        val separator = line.indexOf(' ')
        if (separator <= 0) return null

        val span = line.substring(0, separator).split(':')
        if (span.size != 2) return null

        val offset = span[0].toIntOrNull() ?: return null
        val length = span[1].toIntOrNull() ?: return null
        if (offset < 0 || length <= 0 || offset + length > blob.size) return null

        val name = line.substring(separator + 1).trim()
        if (name.isEmpty()) return null

        return OssLicense(
            name = name,
            body = String(blob, offset, length, Charsets.UTF_8).trim(),
        )
    }
}
