package com.pamoja.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// PAMOJA. COLOR TOKENS
//
// Ported from design/pamoja-ui, the `.ph` custom properties shared by all nine
// screen decks (Auth, Onboarding, Home, Dashboard, Features, Invite and Join,
// States, Components, index). Verified byte-identical across them.
//
// Note for anyone comparing against the bundle: "Pamoja Brand.dc.html" carries a
// DIFFERENT identity, indigo #6C63E8 on cool grays with Sora/Manrope, and the app
// was previously built against that one file. Prefer these values, because
// "Pamoja.dc.html" is the system's own documentation deck and its sections 01 and
// 02 specify this palette by name with contrast ratios worked out
// ("primary ember #C34A21 4.8:1"). Indigo appears in no overview and no contrast
// table. File timestamps cannot settle it, the bundle was exported in one batch.
//
// Palette ramps below are static; semantic tokens resolve per-theme through the
// PamojaColors system further down.
// ============================================================================

// ── Brand ramp (terracotta, primary) ────────────────────────────────────────
// Ramp names are unchanged on purpose. They are consumed by Theme.kt's M3
// ColorScheme and by screens not yet rebuilt, so revaluing in place moves the
// whole app onto the new identity without a rename touching every file.
val Brand50  = Color(0xFFFEF4EF)
val Brand100 = Color(0xFFFAE6DB) // --pris, light primary tint
val Brand200 = Color(0xFFF6CDB8)
val Brand300 = Color(0xFFFBAA85)
val Brand400 = Color(0xFFFF8A5B) // --prib dark, primary hover
val Brand500 = Color(0xFFFF7A47) // --pri dark, primary
val Brand600 = Color(0xFFC34A21) // --pri light, primary
val Brand700 = Color(0xFFA83D18)
val Brand800 = Color(0xFF8A3113)
val Brand900 = Color(0xFF6B250E)
val Brand950 = Color(0xFF2A1207) // --prii dark, text on primary

// ── Ink (warm brown-black, replaces the cool navy) ──────────────────────────
val Ink50  = Color(0xFFF4EBE2) // --ink dark
val Ink100 = Color(0xFFE2D6CA)
val Ink200 = Color(0xFFBCAEA3) // --ink2 dark
val Ink300 = Color(0xFF9C8F84) // --ink3 dark
val Ink400 = Color(0xFF877B70) // --ink3 light
val Ink500 = Color(0xFF6B5F55) // --ink2 light
val Ink600 = Color(0xFF4A3F37)
val Ink700 = Color(0xFF342B25) // --s3 dark
val Ink800 = Color(0xFF2A231E) // --s2 dark
val Ink900 = Color(0xFF1E1815) // --s1 dark
val Ink950 = Color(0xFF141110) // --bg dark

// ── Amber (streaks, warmth) ─────────────────────────────────────────────────
val Amber100 = Color(0xFFFAEEDA) // --ambs light
val Amber300 = Color(0xFFF5B463) // --amb dark
val Amber500 = Color(0xFFE9A13E) // --ambg light
val Amber600 = Color(0xFFA9701A) // --amb light
val Amber700 = Color(0xFF7C5212)

// ── Jade (progress, growth, success). Ramp keeps the Teal* names ────────────
val Teal100 = Color(0xFFDDF2EB) // --jads light
val Teal300 = Color(0xFF42D6A4) // --jad dark
val Teal500 = Color(0xFF17A67C) // --jadg light
val Teal600 = Color(0xFF0E8563) // --jad light
val Teal700 = Color(0xFF0B5C45) // --celbi

// ── Orange (the lighter brand tone, group avatars) ──────────────────────────
val Orange100 = Color(0xFFFBE3D3)
val Orange300 = Color(0xFFF0A878)
val Orange500 = Color(0xFFDE5F35) // --prib light
val Orange600 = Color(0xFFB8461D)

// ── Red (errors) ────────────────────────────────────────────────────────────
val Red100 = Color(0xFFFBE3E0) // --dngs light
val Red300 = Color(0xFFFF8E7E) // --dng dark
val Red500 = Color(0xFFD13228)
val Red600 = Color(0xFFB4271F) // --dng light

/**
 * Group avatar gradients, picked deterministically from the group's name.
 *
 * Decorative and deliberately theme-independent: a group keeps its colour in
 * both themes, which is what lets someone recognise it at a glance in a list.
 * White text always sits on top, so every pair is dark enough to carry it.
 *
 * Here rather than in a screen because two screens draw group avatars and the
 * same group must not be ember on one and olive on the other.
 */
val GroupAvatarGradients: List<List<Color>> = listOf(
    listOf(Color(0xFFC34A21), Color(0xFFA83D18)), // ember, the brand
    listOf(Color(0xFF2E7D6E), Color(0xFF1F5F52)), // deep teal
    listOf(Color(0xFFA9701A), Color(0xFF8A5A08)), // ochre
    listOf(Color(0xFFB4544A), Color(0xFF94413A)), // clay
    listOf(Color(0xFF6B7A3A), Color(0xFF55632C)), // olive
    listOf(Color(0xFF8A5A7A), Color(0xFF6B4460)), // mulberry
)

// ── Neutrals (warm cream, replaces the violet-tinted grays) ─────────────────
val Gray0   = Color(0xFFFFFFFF)
val Gray50  = Color(0xFFFAF5EF) // --bg light
val Gray100 = Color(0xFFF2EBE1) // --s2 light
val Gray200 = Color(0xFFE5DACC)
val Gray300 = Color(0xFFC9BCAE)
val Gray400 = Color(0xFFA99C8F)
val Gray500 = Color(0xFF877B70)
val Gray600 = Color(0xFF6B5F55)
val Gray700 = Color(0xFF4A3F37)
val Gray800 = Color(0xFF2A231E)
val Gray900 = Color(0xFF1E1815)
val Gray950 = Color(0xFF141110)

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
    // Leaderboard medals. Tokens because the design system defines all three
    // per theme; GroupScreen previously carried a raw bronze hex and borrowed a
    // neutral ramp value for silver, so neither followed the theme.
    val medalGold: Color,
    val medalSilver: Color,
    val medalBronze: Color,
)

// ── Dark theme (the app's native mode) ──────────────────────────────────────
val PamojaDarkColors = PamojaColors(
    isDark = true,
    surfaceApp        = Color(0xFF141110), // --bg
    surfaceCanvas     = Color(0xFF1E1815), // --s1
    surface1          = Color(0xFF1E1815), // --s1
    surface2          = Color(0xFF2A231E), // --s2
    surface3          = Color(0xFF342B25), // --s3
    surfaceSunken     = Color(0xFF0E0B0A),
    surfaceInput      = Color(0xFF2A231E), // --s2, .fld background
    surfaceInputFocus = Color(0xFF342B25), // --s3
    overlay           = Color(0xA8080605), // --scrim
    // The dark card sheen the design system paints over every card:
    // linear-gradient(rgba(255,242,232,.04) -> transparent).
    glass             = Color(0x0AFFF2E8),
    glassStrong       = Color(0x14FFF2E8),
    borderSubtle      = Color(0x1AFFEEE2), // --line
    borderDefault     = Color(0x2EFFEEE2), // --line2
    borderStrong      = Color(0x47FFEEE2),
    textPrimary       = Color(0xFFF4EBE2), // --ink
    textSecondary     = Color(0xFFBCAEA3), // --ink2
    textTertiary      = Color(0xFF9C8F84), // --ink3
    textDisabled      = Color(0xFF6B6058),
    textOnBrand       = Color(0xFF2A1207), // --prii
    textInverse       = Color(0xFF231C17),
    accentPrimary       = Brand500,        // --pri
    accentPrimaryHover  = Brand400,        // --prib
    accentPrimaryPress  = Color(0xFFE8663A),
    accentPrimarySubtle = Color(0xFF3A2015), // --pris
    accentPrimaryBorder = Color(0x66FF7A47),
    accentTeal        = Teal300,           // --jad
    accentTealSubtle  = Color(0xFF123329), // --jads
    accentAmber       = Amber300,          // --amb
    accentAmberSubtle = Color(0xFF3A2C15), // --ambs
    accentOrange       = Amber300,         // --ambg
    accentOrangeSubtle = Color(0xFF3A2C15),
    statusSuccess       = Teal300,
    statusSuccessSubtle = Color(0xFF123329),
    statusWarning       = Amber300,
    statusWarningSubtle = Color(0xFF3A2C15),
    statusDanger        = Red300,          // --dng
    statusDangerSubtle  = Color(0xFF3E1C18), // --dngs
    statusInfo          = Brand500,
    statusInfoSubtle    = Color(0xFF3A2015),
    progressTrack     = Color(0xFF123329),
    progressFill      = Teal300,
    medalGold         = Color(0xFFE8B93F), // --gold
    medalSilver       = Color(0xFFC3C6D2), // --silv
    medalBronze       = Color(0xFFD08C5C), // --brnz
)

// ── Light theme ─────────────────────────────────────────────────────────────
val PamojaLightColors = PamojaColors(
    isDark = false,
    surfaceApp        = Color(0xFFFAF5EF), // --bg
    surfaceCanvas     = Color(0xFFFFFFFF), // --s1
    surface1          = Color(0xFFFFFFFF), // --s1
    surface2          = Color(0xFFF2EBE1), // --s2
    surface3          = Color(0xFFFFFFFF), // --s3
    surfaceSunken     = Color(0xFFF2EBE1), // --s2
    surfaceInput      = Color(0xFFF2EBE1), // --s2, .fld background
    surfaceInputFocus = Color(0xFFFFFFFF), // --s1
    overlay           = Color(0x73231C17), // --scrim
    glass             = Color(0x8CFFFFFF),
    glassStrong       = Color(0xBFFFFFFF),
    borderSubtle      = Color(0x17231C17), // --line
    borderDefault     = Color(0x29231C17), // --line2
    borderStrong      = Color(0x47231C17),
    textPrimary       = Color(0xFF231C17), // --ink
    textSecondary     = Color(0xFF6B5F55), // --ink2
    textTertiary      = Color(0xFF877B70), // --ink3
    textDisabled      = Color(0xFFB0A599),
    textOnBrand       = Color(0xFFFFFFFF), // --prii
    textInverse       = Color(0xFFFAF5EF),
    accentPrimary       = Brand600,          // --pri
    accentPrimaryHover  = Orange500,         // --prib
    accentPrimaryPress  = Brand700,
    accentPrimarySubtle = Color(0xFFFAE6DB), // --pris
    accentPrimaryBorder = Color(0x4DC34A21),
    accentTeal        = Teal600,             // --jad
    accentTealSubtle  = Color(0xFFDDF2EB),   // --jads
    accentAmber       = Amber600,            // --amb
    accentAmberSubtle = Color(0xFFFAEEDA),   // --ambs
    accentOrange       = Amber500,           // --ambg
    accentOrangeSubtle = Color(0xFFFAEEDA),
    statusSuccess       = Teal600,
    statusSuccessSubtle = Color(0xFFDDF2EB),
    statusWarning       = Amber600,
    statusWarningSubtle = Color(0xFFFAEEDA),
    statusDanger        = Red600,            // --dng
    statusDangerSubtle  = Color(0xFFFBE3E0), // --dngs
    statusInfo          = Brand600,
    statusInfoSubtle    = Color(0xFFFAE6DB),
    progressTrack     = Color(0xFFDDF2EB),
    progressFill      = Teal600,
    medalGold         = Color(0xFFB98A17), // --gold
    medalSilver       = Color(0xFF75737C), // --silv
    medalBronze       = Color(0xFF9C5F35), // --brnz
)

/** Provides the active [PamojaColors]. Defaults to dark (the native mode). */
val LocalPamojaColors = staticCompositionLocalOf { PamojaDarkColors }
