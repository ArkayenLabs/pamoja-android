package com.pamoja.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Backgrounds ────────────────────────────────────────────────────────────
// Near-black, not pure black — easier on eyes, feels more premium (iOS-like)
val PamojaBackground      = Color(0xFF0D0F14)   // App background
val PamojaSurface         = Color(0xFF161920)   // Cards, bottom sheets
val PamojaSurfaceVariant  = Color(0xFF1E2130)   // Leaderboard rows, secondary cards
val PamojaSurfaceHigh     = Color(0xFF252A3A)   // Elevated / highlighted cards

// ─── Borders ────────────────────────────────────────────────────────────────
val PamojaBorder          = Color(0x12FFFFFF)   // Barely-visible iOS-style border
val PamojaDivider         = Color(0x0AFFFFFF)   // Dividers between list items

// ─── Primary Accent — Indigo ─────────────────────────────────────────────────
// Indigo feels iOS-native, more premium than flat blue.
// Used for: CTAs, progress rings, active nav, highlights
val PamojaIndigo          = Color(0xFF6366F1)   // Primary indigo
val PamojaIndigoLight     = Color(0xFF818CF8)   // Softer indigo — secondary labels, icons
val PamojaIndigoDark      = Color(0xFF4F46E5)   // Pressed states, deep accent
val PamojaIndigoSubtle    = Color(0x1A6366F1)   // 10% indigo — card tints, selected bg

// ─── Success — Green ─────────────────────────────────────────────────────────
// Used for: milestone achieved, step goals hit, "you" badge, streaks
val PamojaGreen           = Color(0xFF34D399)   // Emerald green
val PamojaGreenDark       = Color(0xFF059669)   // Deeper green for pressed
val PamojaGreenSubtle     = Color(0x1A34D399)   // 10% green — achievement bg tints

// ─── Warning / Energy ────────────────────────────────────────────────────────
// Used for: mid-goal, "on track" indicators
val PamojaAmber           = Color(0xFFFBBF24)   // Amber / golden
val PamojaAmberSubtle     = Color(0x1AFBBF24)   // 10% amber tint

// ─── Text ───────────────────────────────────────────────────────────────────
val PamojaTextPrimary     = Color(0xFFEDF0FF)   // Almost white, slightly blue-tinted
val PamojaTextSecondary   = Color(0xFF8B91A8)   // Secondary labels (muted)
val PamojaTextTertiary    = Color(0xFF4A5068)   // Placeholder, disabled text
val PamojaWhite           = Color(0xFFFFFFFF)

// ─── Destructive ────────────────────────────────────────────────────────────
val PamojaRed             = Color(0xFFFF4D4D)
val PamojaRedSubtle       = Color(0x1AFF4D4D)

// ─── Legacy aliases — kept so existing screens don't break before full redesign
// These will be removed screen-by-screen as we redesign each one.
val PamojaBlue            = PamojaIndigo
val PamojaBlueDark        = PamojaIndigoDark
val PamojaBlueLight       = PamojaIndigoSubtle
val PamojaGreenLight      = PamojaGreenSubtle
val PamojaRedLight        = PamojaRedSubtle
val PamojaGray            = PamojaTextSecondary
val PamojaGrayLight       = PamojaBorder
val PamojaTextPrimary_    = PamojaTextPrimary    // alias used in old screens
val PamojaDarkBackground  = PamojaBackground
val PamojaDarkSurface     = PamojaSurface
val PamojaDarkSurfaceVariant = PamojaSurfaceVariant