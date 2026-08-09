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
// PAMOJA. TYPOGRAPHY  (Pamoja Design System: Baloo 2 display + Nunito body)
// Both rounded families are fetched via the downloadable Google Fonts provider
// (certs in res/values/font_certs.xml). No bundled TTFs, the first launch
// downloads + caches them. Display roles use Baloo 2 (chunky rounded terminals,
// matches the "Pamoja" wordmark); body/label roles use Nunito.
// ============================================================================

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val baloo2 = GoogleFont("Baloo 2")
private val nunito = GoogleFont("Nunito")

/** Display / headings. Baloo 2. */
val DisplayFontFamily = FontFamily(
    Font(googleFont = baloo2, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = baloo2, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = baloo2, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = baloo2, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
)

/** Body / UI text. Nunito. */
val BodyFontFamily = FontFamily(
    Font(googleFont = nunito, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = nunito, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = nunito, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = nunito, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = nunito, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
)

// M3 type scale mapped to the design system's scale (tokens/typography.css).
val PamojaTypography = Typography(
    // Hero stats, large step counts on the progress ring.
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 46.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp,
    ),
    // Screen titles / group names.
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    // Card titles.
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Body copy.
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    // Buttons / labels.
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    // Tracked-out caps section labels ("YOUR GROUPS", "LEADERBOARD").
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)
