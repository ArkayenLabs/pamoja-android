package com.pamoja.app.domain.model

import kotlin.math.roundToInt

/**
 * Which units height and weight are shown in.
 *
 * A display choice only. Everything is stored in metric, always: `User.height`
 * is centimetres and `User.weight` is kilograms, in Kotlin and in Firestore.
 * Storing whatever the user happened to be looking at would mean a value's
 * meaning depended on a setting held somewhere else, and the first person to
 * switch units would silently rewrite their own history.
 *
 * So conversion happens at the edge, on the way to a text field and back, and
 * nothing below the UI layer knows this type exists.
 */
enum class UnitSystem {
    Metric,
    Imperial;

    companion object {
        /**
         * Falls back to Metric for an unknown name, which covers a value
         * written by a newer build and read by an older one.
         */
        fun fromName(name: String?): UnitSystem =
            entries.firstOrNull { it.name == name } ?: Metric
    }
}

/**
 * Height and weight conversions.
 *
 * Deliberately pure functions on plain numbers rather than a wrapper type. The
 * app has exactly two quantities to convert and they are only ever converted at
 * one boundary; a units library, or a Measurement class threaded through the
 * domain, would be more machinery than the problem has.
 */
object UnitConverter {

    private const val CM_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12
    private const val KG_PER_POUND = 0.45359237

    // ── Height ──────────────────────────────────────────────────────────────

    /** Centimetres to whole feet and the remaining whole inches. */
    fun cmToFeetInches(cm: Float): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        // 11.6 inches rounds to 12, which must read as the next foot rather
        // than as 5 feet 12 inches.
        val feet = totalInches / INCHES_PER_FOOT
        val inches = totalInches % INCHES_PER_FOOT
        return feet to inches
    }

    fun feetInchesToCm(feet: Int, inches: Int): Float =
        ((feet * INCHES_PER_FOOT + inches) * CM_PER_INCH).toFloat()

    // ── Weight ──────────────────────────────────────────────────────────────

    fun kgToPounds(kg: Float): Int = (kg / KG_PER_POUND).roundToInt()

    fun poundsToKg(pounds: Int): Float = (pounds * KG_PER_POUND).toFloat()
}
