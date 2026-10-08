package com.pamoja.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The notification strings, read straight from the resource files.
 *
 * The push_* names are sent by the server, so a string missing from one
 * language, or with different placeholders, would not fail any build: the
 * phone would quietly show English, or FCM would drop the text.
 */
class NotificationStringsTest {

    private fun strings(folder: String): Map<String, String> {
        // Unit tests run from the module directory.
        val xml = File("src/main/res/$folder/strings_notifications.xml").readText()
        return Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String): List<String> =
        Regex("%\\d\\\$s").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryStringWithTheSamePlaceholders() {
        val english = strings("values")
        assertTrue(english.keys.any { it.startsWith("push_") })
        listOf("values-es", "values-hi").forEach { folder ->
            val other = strings(folder)
            assertEquals(folder, english.keys, other.keys)
            english.forEach { (key, text) ->
                assertEquals("$folder $key", placeholders(text), placeholders(other.getValue(key)))
            }
        }
    }

    @Test
    fun noEmDashesNoEmojiAndNoFirstPerson() {
        listOf("values", "values-es", "values-hi").forEach { folder ->
            strings(folder).forEach { (key, text) ->
                assertFalse("$folder $key", '—' in text || '–' in text)
                assertFalse("$folder $key", text.codePoints().anyMatch { it >= 0x1F000 })
            }
        }
        strings("values").forEach { (key, text) ->
            assertFalse(key, Regex("""\bI\b""").containsMatchIn(text))
        }
    }
}
