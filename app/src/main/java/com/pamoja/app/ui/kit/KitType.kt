package com.pamoja.app.ui.kit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pamoja.app.R

// ============================================================================
// PAMOJA KIT. TYPE
//
// Familjen Grotesk for display and numbers, Figtree for everything else, as
// drawn on the mockup canvas. Sizes are the mockups' px read as sp; they are
// drawn on a 390px frame, which is about a 390dp phone.
//
// Neither font has Devanagari (checked on Google Fonts, 2026-09-27), so Hindi
// uses Hind for both roles. Hind covers Latin too, which keeps numbers and
// names in one face inside a Hindi sentence. Devanagari also needs taller
// lines than the tight Latin display settings, or the vowel marks clip.
// ============================================================================

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

// Only weights the scale uses. Each is its own provider query on first run.
private fun googleFamily(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) },
)

private val GroteskFamily = googleFamily("Familjen Grotesk", FontWeight.Bold)

private val FigtreeFamily = googleFamily(
    "Figtree",
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

// Hind stops at 700; asking for ExtraBold resolves to Bold.
private val HindFamily = googleFamily(
    "Hind",
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

/** Languages the app may show in Devanagari. Only Hindi ships today. */
internal val DevanagariLanguages = setOf("hi", "mr", "ne")

@Immutable
data class KitType(
    /** The one big line on a screen, e.g. "Your people. One weekly goal." */
    val display: TextStyle,
    /** Screen titles. */
    val headline: TextStyle,
    /** Card titles, e.g. "Today". */
    val title: TextStyle,
    /** The one important number on a screen. */
    val bigNumber: TextStyle,
    /** Secondary numbers, e.g. your steps today. */
    val number: TextStyle,
    val body: TextStyle,
    /** Names in lists, row labels. */
    val bodyStrong: TextStyle,
    /** Field labels, chip-sized emphasis. */
    val label: TextStyle,
    /** Helper text, counts, small print. */
    val caption: TextStyle,
    val button: TextStyle,
    val buttonSmall: TextStyle,
)

internal fun kitType(devanagari: Boolean): KitType {
    val display = if (devanagari) HindFamily else GroteskFamily
    val text = if (devanagari) HindFamily else FigtreeFamily
    // Tight display leading suits Latin caps and descenders only.
    fun lead(latin: Float, deva: Float) = if (devanagari) deva else latin
    val tracking = if (devanagari) 0.em else (-0.02).em

    return KitType(
        display = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 40.sp, lineHeight = (40 * lead(1.04f, 1.35f)).sp, letterSpacing = tracking,
        ),
        headline = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 34.sp, lineHeight = (34 * lead(1.06f, 1.35f)).sp, letterSpacing = tracking,
        ),
        title = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, lineHeight = (22 * lead(1.2f, 1.4f)).sp, letterSpacing = tracking,
        ),
        bigNumber = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 64.sp, lineHeight = 64.sp,
            letterSpacing = if (devanagari) 0.em else (-0.03).em,
        ),
        number = TextStyle(
            fontFamily = display, fontWeight = FontWeight.Bold,
            fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = tracking,
        ),
        body = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = (16 * lead(1.45f, 1.6f)).sp,
        ),
        bodyStrong = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, lineHeight = (15 * lead(1.35f, 1.55f)).sp,
        ),
        label = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, lineHeight = (14 * lead(1.35f, 1.55f)).sp,
        ),
        caption = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 13.sp, lineHeight = (13 * lead(1.45f, 1.6f)).sp,
        ),
        button = TextStyle(
            fontFamily = text, fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp, lineHeight = (17 * lead(1.25f, 1.5f)).sp,
        ),
        buttonSmall = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, lineHeight = (15 * lead(1.25f, 1.5f)).sp,
        ),
    )
}

val LocalKitType = staticCompositionLocalOf { kitType(devanagari = false) }
