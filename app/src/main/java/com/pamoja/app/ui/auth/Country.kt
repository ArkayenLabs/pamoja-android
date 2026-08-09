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
    val name: String,
    val dialCode: String,
    /** Digits expected after the dial code, used for on-blur validation. */
    val nationalDigits: IntRange,
) {
    companion object {
        val India = Country("IN", "India", "+91", 10..10)

        /**
         * Falls back to India rather than a random first entry, since that is
         * the primary market. [fromLocale] overrides this per device.
         */
        val Default = India

        fun fromLocale(locale: Locale = Locale.getDefault()): Country =
            all.firstOrNull { it.isoCode.equals(locale.country, ignoreCase = true) } ?: Default

        val all: List<Country> = listOf(
            Country("AR", "Argentina", "+54", 10..11),
            Country("AU", "Australia", "+61", 9..9),
            Country("AT", "Austria", "+43", 10..13),
            Country("BD", "Bangladesh", "+880", 10..10),
            Country("BE", "Belgium", "+32", 9..9),
            Country("BR", "Brazil", "+55", 10..11),
            Country("CA", "Canada", "+1", 10..10),
            Country("CL", "Chile", "+56", 9..9),
            Country("CN", "China", "+86", 11..11),
            Country("CO", "Colombia", "+57", 10..10),
            Country("CZ", "Czechia", "+420", 9..9),
            Country("DK", "Denmark", "+45", 8..8),
            Country("EG", "Egypt", "+20", 10..10),
            Country("ET", "Ethiopia", "+251", 9..9),
            Country("FI", "Finland", "+358", 9..10),
            Country("FR", "France", "+33", 9..9),
            Country("DE", "Germany", "+49", 10..11),
            Country("GH", "Ghana", "+233", 9..9),
            Country("GR", "Greece", "+30", 10..10),
            Country("HK", "Hong Kong", "+852", 8..8),
            Country("HU", "Hungary", "+36", 9..9),
            India,
            Country("ID", "Indonesia", "+62", 9..12),
            Country("IE", "Ireland", "+353", 9..9),
            Country("IL", "Israel", "+972", 9..9),
            Country("IT", "Italy", "+39", 9..10),
            Country("JP", "Japan", "+81", 10..10),
            Country("KE", "Kenya", "+254", 9..9),
            Country("MY", "Malaysia", "+60", 9..10),
            Country("MX", "Mexico", "+52", 10..10),
            Country("MA", "Morocco", "+212", 9..9),
            Country("NP", "Nepal", "+977", 10..10),
            Country("NL", "Netherlands", "+31", 9..9),
            Country("NZ", "New Zealand", "+64", 8..10),
            Country("NG", "Nigeria", "+234", 10..10),
            Country("NO", "Norway", "+47", 8..8),
            Country("PK", "Pakistan", "+92", 10..10),
            Country("PE", "Peru", "+51", 9..9),
            Country("PH", "Philippines", "+63", 10..10),
            Country("PL", "Poland", "+48", 9..9),
            Country("PT", "Portugal", "+351", 9..9),
            Country("QA", "Qatar", "+974", 8..8),
            Country("RO", "Romania", "+40", 9..9),
            Country("RU", "Russia", "+7", 10..10),
            Country("SA", "Saudi Arabia", "+966", 9..9),
            Country("SG", "Singapore", "+65", 8..8),
            Country("ZA", "South Africa", "+27", 9..9),
            Country("KR", "South Korea", "+82", 9..10),
            Country("ES", "Spain", "+34", 9..9),
            Country("LK", "Sri Lanka", "+94", 9..9),
            Country("SE", "Sweden", "+46", 9..9),
            Country("CH", "Switzerland", "+41", 9..9),
            Country("TW", "Taiwan", "+886", 9..9),
            Country("TZ", "Tanzania", "+255", 9..9),
            Country("TH", "Thailand", "+66", 9..9),
            Country("TR", "Turkey", "+90", 10..10),
            Country("UG", "Uganda", "+256", 9..9),
            Country("UA", "Ukraine", "+380", 9..9),
            Country("AE", "United Arab Emirates", "+971", 9..9),
            Country("GB", "United Kingdom", "+44", 10..10),
            Country("US", "United States", "+1", 10..10),
            Country("VN", "Vietnam", "+84", 9..10),
            Country("ZM", "Zambia", "+260", 9..9),
            Country("ZW", "Zimbabwe", "+263", 9..9),
        ).sortedBy { it.name }

        fun search(query: String): List<Country> {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return all
            return all.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.dialCode.contains(trimmed) ||
                    it.isoCode.equals(trimmed, ignoreCase = true)
            }
        }
    }
}
