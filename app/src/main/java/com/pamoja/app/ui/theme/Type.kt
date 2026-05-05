package com.pamoja.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pamoja.app.R

// ─── Outfit Font Family ──────────────────────────────────────────────────────
// Geometric sans-serif — feels iOS-native without being SF Pro.
// Great for fitness/social apps: clean, modern, slightly rounded.
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_light,    FontWeight.Light),
    Font(R.font.outfit_regular,  FontWeight.Normal),
    Font(R.font.outfit_medium,   FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold,     FontWeight.Bold)
)

// ─── Type Scale ──────────────────────────────────────────────────────────────
// Follows a tight, modern scale with negative letter-spacing on large headings
// (the "Apple feel"). Labels use slight positive tracking for readability.
val PamojaTypography = Typography(

    // Hero numbers — used for large step counts on the progress ring
    displayLarge = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 48.sp,
        lineHeight   = 52.sp,
        letterSpacing = (-1.5).sp
    ),

    // Large heading — screen titles, group name on detail page
    headlineLarge = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = (-0.5).sp
    ),

    // Medium heading — section titles, dialog titles
    headlineMedium = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = (-0.3).sp
    ),

    // Small heading — card titles
    headlineSmall = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 18.sp,
        lineHeight   = 24.sp,
        letterSpacing = (-0.2).sp
    ),

    // Body large — primary content text
    bodyLarge = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.sp
    ),

    // Body medium — descriptions, subtitles, leaderboard names
    bodyMedium = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.sp
    ),

    // Body small — helper text, secondary info
    bodySmall = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Light,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.sp
    ),

    // Label large — important labels, button text
    labelLarge = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 15.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.sp
    ),

    // Label medium — stat values, leaderboard rank numbers
    labelMedium = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 13.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.sp
    ),

    // Label small — caps labels, section headers ("LEADERBOARD")
    // Slight positive tracking for legibility at small caps
    labelSmall = TextStyle(
        fontFamily   = OutfitFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 14.sp,
        letterSpacing = 0.6.sp
    )
)