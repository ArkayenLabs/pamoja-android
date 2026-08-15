package com.pamoja.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.pamoja.app.R

// ============================================================================
// PAMOJA. TYPOGRAPHY
//
// Ported from design/pamoja-ui, the nine screen decks:
//   .h1/.h2/.num  Bricolage Grotesque   display, headings, every number
//   .bd/.btn/.fld Plus Jakarta Sans     body, buttons, inputs
//   .lbl          IBM Plex Mono         tracked-out caps section labels
//
// All three come from the downloadable Google Fonts provider (certs in
// res/values/font_certs.xml), so nothing is bundled and the first launch
// downloads and caches them.
//
// Sizes are the decks' px values read as sp. The mockups are drawn on a 392px
// phone frame, which is about a 392dp screen, so the two scales line up close
// enough that the numbers transfer directly.
// ============================================================================

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val bricolage = GoogleFont("Bricolage Grotesque")
private val jakarta = GoogleFont("Plus Jakarta Sans")
private val plexMono = GoogleFont("IBM Plex Mono")

// Only the weights the scale below actually asks for are declared. Every entry
// is a separate query to the Play Services font provider on first run, so an
// unused weight is a network round trip bought for nothing. Adding a weight
// here means adding it to the scale too, otherwise it is dead freight.

/** Display / headings / numbers. Bricolage Grotesque. */
val DisplayFontFamily = FontFamily(
    Font(googleFont = bricolage, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = bricolage, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

/** Body / UI text / buttons. Plus Jakarta Sans. */
val BodyFontFamily = FontFamily(
    Font(googleFont = jakarta, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jakarta, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jakarta, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = jakarta, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

/**
 * Section labels only. IBM Plex Mono.
 *
 * The mono, tracked-out, uppercase label is one of the loudest signals of this
 * identity ("YOUR GROUPS", "LEADERBOARD"), so it gets its own family rather
 * than being approximated with letter-spacing on the body face.
 */
val LabelFontFamily = FontFamily(
    Font(googleFont = plexMono, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    // Bold is the active slider tick on the create-group screen.
    Font(googleFont = plexMono, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

/** Tabular figures, so a counting number does not jitter as digits change. */
private const val TABULAR = "tnum"

val PamojaTypography = Typography(
    // .num at 52px. The progress ring total, the largest thing in the app.
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.8).sp,
        fontFeatureSettings = TABULAR,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp,
        fontFeatureSettings = TABULAR,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1.1).sp,
        fontFeatureSettings = TABULAR,
    ),
    // .h1, 30px / 1.1 / -.025em. Screen titles.
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.75).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp,
    ),
    // .h2, 20px / 1.22 / -.015em.
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.25).sp,
    ),
    // Group card names, 16.5px / 700 on the body face.
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // .fld, 16px / 500.
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 25.sp,
    ),
    // .bd, 15px / 1.55.
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    // .btn, 16px / 700.
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp,
    ),
    // .lbl, IBM Plex Mono 11px / 500 / .15em. Callers supply the uppercasing.
    labelSmall = TextStyle(
        fontFamily = LabelFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.65.sp,
    ),
)
