package com.pamoja.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// PAMOJA. COLOR TOKENS
// Ported 1:1 from the Pamoja Design System (tokens/colors.css).
// Palette ramps below are static; semantic tokens resolve per-theme through the
// PamojaColors system further down (dark = native, full light theme supported).
// Legacy flat `val`s are preserved at the bottom so un-migrated screens compile
// unchanged, migrate screen-by-screen from those onto LocalPamojaColors.
// ============================================================================

// ── Brand ramp (indigo / violet, primary) ──────────────────────────────────
val Brand50  = Color(0xFFF1F0FE)
val Brand100 = Color(0xFFE3E1FD)
val Brand200 = Color(0xFFC7C4FB)
val Brand300 = Color(0xFFA6A1F8)
val Brand400 = Color(0xFF8B85F5)
val Brand500 = Color(0xFF7B7FF2) // primary
val Brand600 = Color(0xFF6C63E8)
val Brand700 = Color(0xFF5A4FD1)
val Brand800 = Color(0xFF4839A8)
val Brand900 = Color(0xFF362C7C)
val Brand950 = Color(0xFF211C52)

// ── Ink (deep navy from the logo mark) ──────────────────────────────────────
val Ink50  = Color(0xFFEEEEF5)
val Ink100 = Color(0xFFD3D3E4)
val Ink200 = Color(0xFFA6A6C6)
val Ink300 = Color(0xFF7A7AA8)
val Ink400 = Color(0xFF4B4B85)
val Ink500 = Color(0xFF2E2C63)
val Ink600 = Color(0xFF22214A)
val Ink700 = Color(0xFF1B1B3A) // logo navy
val Ink800 = Color(0xFF13132A)
val Ink900 = Color(0xFF0B0B1C)
val Ink950 = Color(0xFF060610)

// ── Amber (streaks, medals, warmth) ─────────────────────────────────────────
val Amber100 = Color(0xFFFDECC8)
val Amber300 = Color(0xFFF9CB6B)
val Amber500 = Color(0xFFF5A623) // medal / streak accent
val Amber600 = Color(0xFFDB8B12)
val Amber700 = Color(0xFFA9690C)

// ── Teal / emerald (progress, growth, success) ──────────────────────────────
val Teal100 = Color(0xFFC9F3E1)
val Teal300 = Color(0xFF6FDFB0)
val Teal500 = Color(0xFF22C58B) // progress ring / join-link accent
val Teal600 = Color(0xFF17A472)
val Teal700 = Color(0xFF0F7D58)

// ── Orange (secondary logo hue, group avatars) ──────────────────────────────
val Orange100 = Color(0xFFFCE3C6)
val Orange300 = Color(0xFFF5AE68)
val Orange500 = Color(0xFFEB8A2F)
val Orange600 = Color(0xFFC96E1C)

// ── Red (errors) ────────────────────────────────────────────────────────────
val Red100 = Color(0xFFFBDADA)
val Red300 = Color(0xFFF08585)
val Red500 = Color(0xFFE5484D)
val Red600 = Color(0xFFC4383D)

// ── Neutrals (cool, slightly violet-tinted grays) ───────────────────────────
val Gray0   = Color(0xFFFFFFFF)
val Gray50  = Color(0xFFF7F7FB)
val Gray100 = Color(0xFFEEEDF6)
val Gray200 = Color(0xFFDFDDEC)
val Gray300 = Color(0xFFC4C1D6)
val Gray400 = Color(0xFF9C98B4)
val Gray500 = Color(0xFF757092)
val Gray600 = Color(0xFF575371)
val Gray700 = Color(0xFF403C58)
val Gray800 = Color(0xFF2A2740)
val Gray900 = Color(0xFF16141F)
val Gray950 = Color(0xFF0A0A11)

// ============================================================================
// SEMANTIC COLOR SYSTEM
// Screens should read these via LocalPamojaColors.current so they follow the
// active theme. Never hardcode a raw hex in a screen.
// ============================================================================
@Immutable
data class PamojaColors(
    val isDark: Boolean,
    // Surfaces
    val surfaceApp: Color,
    val surfaceCanvas: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceSunken: Color,
    val surfaceInput: Color,
    val surfaceInputFocus: Color,
    val overlay: Color,
    val glass: Color,
    val glassStrong: Color,
    // Borders
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textOnBrand: Color,
    val textInverse: Color,
    // Brand / accents
    val accentPrimary: Color,
    val accentPrimaryHover: Color,
    val accentPrimaryPress: Color,
    val accentPrimarySubtle: Color,
    val accentPrimaryBorder: Color,
    val accentTeal: Color,
    val accentTealSubtle: Color,
    val accentAmber: Color,
    val accentAmberSubtle: Color,
    val accentOrange: Color,
    val accentOrangeSubtle: Color,
    // Status
    val statusSuccess: Color,
    val statusSuccessSubtle: Color,
    val statusWarning: Color,
    val statusWarningSubtle: Color,
    val statusDanger: Color,
    val statusDangerSubtle: Color,
    val statusInfo: Color,
    val statusInfoSubtle: Color,
    // Progress ring
    val progressTrack: Color,
    val progressFill: Color,
)

// ── Dark theme (the app's native mode) ──────────────────────────────────────
val PamojaDarkColors = PamojaColors(
    isDark = true,
    surfaceApp        = Color(0xFF0B0B11),
    surfaceCanvas     = Color(0xFF0E0E16),
    surface1          = Color(0xFF14141F),
    surface2          = Color(0xFF191A28),
    surface3          = Color(0xFF212236),
    surfaceSunken     = Color(0xFF08080D),
    surfaceInput      = Color(0xFF14141F),
    surfaceInputFocus = Color(0xFF181933),
    overlay           = Color(0xB80A0A10),
    glass             = Color(0x0BFFFFFF),
    glassStrong       = Color(0x14FFFFFF),
    borderSubtle      = Color(0x0FFFFFFF),
    borderDefault     = Color(0x1AFFFFFF),
    borderStrong      = Color(0x29FFFFFF),
    textPrimary       = Color(0xFFF5F4FA),
    textSecondary     = Color(0xFFA9A6C0),
    textTertiary      = Color(0xFF746F91),
    textDisabled      = Color(0xFF4B4763),
    textOnBrand       = Color(0xFFFFFFFF),
    textInverse       = Color(0xFF16141F),
    accentPrimary       = Brand500,
    accentPrimaryHover  = Brand400,
    accentPrimaryPress  = Brand600,
    accentPrimarySubtle = Color(0x297B7FF2),
    accentPrimaryBorder = Color(0x667B7FF2),
    accentTeal        = Teal500,
    accentTealSubtle  = Color(0x2922C58B),
    accentAmber       = Amber500,
    accentAmberSubtle = Color(0x29F5A623),
    accentOrange       = Orange500,
    accentOrangeSubtle = Color(0x29EB8A2F),
    statusSuccess       = Teal500,
    statusSuccessSubtle = Color(0x2422C58B),
    statusWarning       = Amber500,
    statusWarningSubtle = Color(0x24F5A623),
    statusDanger        = Red500,
    statusDangerSubtle  = Color(0x24E5484D),
    statusInfo          = Brand400,
    statusInfoSubtle    = Color(0x248B85F5),
    progressTrack     = Color(0x2422C58B),
    progressFill      = Teal500,
)

// ── Light theme ─────────────────────────────────────────────────────────────
val PamojaLightColors = PamojaColors(
    isDark = false,
    surfaceApp        = Color(0xFFF8F7FC),
    surfaceCanvas     = Color(0xFFFFFFFF),
    surface1          = Color(0xFFFFFFFF),
    surface2          = Color(0xFFFFFFFF),
    surface3          = Color(0xFFFFFFFF),
    surfaceSunken     = Color(0xFFEFEDF6),
    surfaceInput      = Color(0xFFF1EFF8),
    surfaceInputFocus = Color(0xFFFFFFFF),
    overlay           = Color(0x6616141F),
    glass             = Color(0x8CFFFFFF),
    glassStrong       = Color(0xBFFFFFFF),
    borderSubtle      = Color(0x0F16141F),
    borderDefault     = Color(0x1A16141F),
    borderStrong      = Color(0x2E16141F),
    textPrimary       = Color(0xFF16141F),
    textSecondary     = Color(0xFF575371),
    textTertiary      = Color(0xFF8B87A3),
    textDisabled      = Color(0xFFC4C1D6),
    textOnBrand       = Color(0xFFFFFFFF),
    textInverse       = Color(0xFFF5F4FA),
    accentPrimary       = Brand600,
    accentPrimaryHover  = Brand700,
    accentPrimaryPress  = Brand800,
    accentPrimarySubtle = Color(0x1A6C63E8),
    accentPrimaryBorder = Color(0x4D6C63E8),
    accentTeal        = Teal600,
    accentTealSubtle  = Color(0x1A17A472),
    accentAmber       = Amber600,
    accentAmberSubtle = Color(0x1FDB8B12),
    accentOrange       = Orange600,
    accentOrangeSubtle = Color(0x1AC96E1C),
    statusSuccess       = Teal600,
    statusSuccessSubtle = Color(0x1A17A472),
    statusWarning       = Amber600,
    statusWarningSubtle = Color(0x1FDB8B12),
    statusDanger        = Red600,
    statusDangerSubtle  = Color(0x1AC4383D),
    statusInfo          = Brand600,
    statusInfoSubtle    = Color(0x1A6C63E8),
    progressTrack     = Color(0x1F17A472),
    progressFill      = Teal600,
)

/** Provides the active [PamojaColors]. Defaults to dark (the native mode). */
val LocalPamojaColors = staticCompositionLocalOf { PamojaDarkColors }
