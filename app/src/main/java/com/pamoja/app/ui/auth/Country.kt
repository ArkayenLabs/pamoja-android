package com.pamoja.app.ui.auth

import java.util.Locale

/**
 * A dialling country.
 *
 * Identified by ISO code and dial code rather than a flag, deliberately. The
 * product bans emoji outright, flag emoji render inconsistently across Android
 * versions, and a screen reader announces "IN, plus 91" far more usefully than
 * it announces a flag glyph.
 */
data class Country(
    val isoCode: String,
    val dialCode: String,
    /** Digits expected after the dial code, used for on-blur validation. */
    val nationalDigits: IntRange,
) {
    /**
     * The country's name in the reader's own language.
     *
     * Taken from the platform rather than shipped as strings, so this list is
     * localised in every language Android supports without us translating
     * anything. A German device shows "Deutschland", a French one "Allemagne".
     * Hardcoding English names here would have been 65 strings to translate and
     * would still have been wrong in any language we skipped.
     */
    val name: String get() = Locale("", isoCode).getDisplayCountry(Locale.getDefault())

    companion object {
        val India = Country("IN", "+91", 10..10)

        /**
         * Falls back to India rather than a random first entry, since that is
         * the primary market. [fromLocale] overrides this per device.
         */
        val Default = India

        fun fromLocale(locale: Locale = Locale.getDefault()): Country =
            all.firstOrNull { it.isoCode.equals(locale.country, ignoreCase = true) } ?: Default

        /**
         * Declaration order only. Never render this directly.
         *
         * Sorting lives in [sorted] because names are resolved from the current
         * locale, and a list sorted once at class-init would keep an English
         * ordering after the user switched language, since a configuration
         * change recreates activities but not loaded classes.
         */
        private val all: List<Country> = listOf(
            Country("AR", "+54", 10..11),
            Country("AU", "+61", 9..9),
            Country("AT", "+43", 10..13),
            Country("BD", "+880", 10..10),
            Country("BE", "+32", 9..9),
            Country("BR", "+55", 10..11),
            Country("CA", "+1", 10..10),
            Country("CL", "+56", 9..9),
            Country("CN", "+86", 11..11),
            Country("CO", "+57", 10..10),
            Country("CZ", "+420", 9..9),
            Country("DK", "+45", 8..8),
            Country("EG", "+20", 10..10),
            Country("ET", "+251", 9..9),
            Country("FI", "+358", 9..10),
            Country("FR", "+33", 9..9),
            Country("DE", "+49", 10..11),
            Country("GH", "+233", 9..9),
            Country("GR", "+30", 10..10),
            Country("HK", "+852", 8..8),
            Country("HU", "+36", 9..9),
            India,
            Country("ID", "+62", 9..12),
            Country("IE", "+353", 9..9),
            Country("IL", "+972", 9..9),
            Country("IT", "+39", 9..10),
            Country("JP", "+81", 10..10),
            Country("KE", "+254", 9..9),
            Country("MY", "+60", 9..10),
            Country("MX", "+52", 10..10),
            Country("MA", "+212", 9..9),
            Country("NP", "+977", 10..10),
            Country("NL", "+31", 9..9),
            Country("NZ", "+64", 8..10),
            Country("NG", "+234", 10..10),
            Country("NO", "+47", 8..8),
            Country("PK", "+92", 10..10),
            Country("PE", "+51", 9..9),
            Country("PH", "+63", 10..10),
            Country("PL", "+48", 9..9),
            Country("PT", "+351", 9..9),
            Country("QA", "+974", 8..8),
            Country("RO", "+40", 9..9),
            Country("RU", "+7", 10..10),
            Country("SA", "+966", 9..9),
            Country("SG", "+65", 8..8),
            Country("ZA", "+27", 9..9),
            Country("KR", "+82", 9..10),
            Country("ES", "+34", 9..9),
            Country("LK", "+94", 9..9),
            Country("SE", "+46", 9..9),
            Country("CH", "+41", 9..9),
            Country("TW", "+886", 9..9),
            Country("TZ", "+255", 9..9),
            Country("TH", "+66", 9..9),
            Country("TR", "+90", 10..10),
            Country("UG", "+256", 9..9),
            Country("UA", "+380", 9..9),
            Country("AE", "+971", 9..9),
            Country("GB", "+44", 10..10),
            Country("US", "+1", 10..10),
            Country("VN", "+84", 9..10),
            Country("ZM", "+260", 9..9),
            Country("ZW", "+263", 9..9),
        )

        /** Alphabetical in the reader's language, resolved on every read. */
        val sorted: List<Country> get() = all.sortedBy { it.name }

        fun search(query: String): List<Country> {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return sorted
            return sorted.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.dialCode.contains(trimmed) ||
                    it.isoCode.equals(trimmed, ignoreCase = true)
            }
        }
    }
}
