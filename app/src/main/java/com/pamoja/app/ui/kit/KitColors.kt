package com.pamoja.app.ui.kit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// PAMOJA KIT. LILAC COLOURS
//
// The rebuild's palette, "B · Family album", taken token for token from the
// mockup canvas (rows C and D, REBUILD_PLAN.md §5). The app's only palette
// since the terracotta theme was deleted (2026-09-29); the widgets repeat it
// in res/values/colors_widget.xml because RemoteViews cannot read this.
//
// Rules the palette carries (REBUILD_PLAN.md §5): lavender is only ever a
// surface or an accent, lime is the only bright colour, and there is no
// saturated indigo and no purple gradient anywhere.
// ============================================================================

@Immutable
data class KitColors(
    val isDark: Boolean,
    /** Screen background. */
    val page: Color,
    /** Cards, rows, round icon buttons. */
    val surface: Color,
    /** The lilac block: the week card, the welcome header, Premium. */
    val hero: Color,
    val onHero: Color,
    val heroMuted: Color,
    val heroTrack: Color,
    val heroFill: Color,
    /** Main text and icons. */
    val ink: Color,
    /** Secondary text. Still 4.5:1 on page and surface. */
    val muted: Color,
    /** Hairlines between rows. */
    val line: Color,
    /** Progress track and fill on a surface. */
    val track: Color,
    val bar: Color,
    /** Primary button: near-black in light mode, lilac in dark. */
    val button: Color,
    val onButton: Color,
    /** Secondary button: a surface with a border. */
    val button2: Color,
    val button2Border: Color,
    val field: Color,
    val fieldBorder: Color,
    /** The one bright colour. Always with dark ink on it, in both themes. */
    val pop: Color,
    val onPop: Color,
    /** Emphasis inside running text. */
    val emphasis: Color,
    val danger: Color,
    val scrim: Color,
    /** Small tinted discs behind icons, e.g. the Google mark. */
    val iconBackground: Color,
    val dotOn: Color,
    val dotOff: Color,
    /**
     * Backgrounds for members without a photo. Same in both themes: they sit
     * behind dark initials, and a face should not change colour with the
     * phone's theme.
     */
    val avatarBackgrounds: List<Color>,
    /** Initials on [avatarBackgrounds]. */
    val onAvatar: Color,
)

private val AvatarBackgrounds = listOf(
    Color(0xFFD3E2C7),
    Color(0xFFC9D8E6),
    Color(0xFFE8D5C4),
    Color(0xFFEDE0B8),
    Color(0xFFD9D3EC),
    Color(0xFFE6CFDB),
)

private val Ink = Color(0xFF16132B)

val LightKitColors = KitColors(
    isDark = false,
    page = Color(0xFFF6F4FB),
    surface = Color(0xFFFFFFFF),
    hero = Color(0xFFCFC6FF),
    onHero = Ink,
    heroMuted = Color(0xFF3E3960),
    heroTrack = Color(0xFFB5ABEE),
    heroFill = Ink,
    ink = Ink,
    muted = Color(0xFF5A5670),
    line = Color(0xFFE7E3F2),
    track = Color(0xFFE4DFF3),
    bar = Ink,
    button = Ink,
    onButton = Color(0xFFFFFFFF),
    button2 = Color(0xFFFFFFFF),
    button2Border = Color(0xFFDCD7EC),
    field = Color(0xFFFFFFFF),
    fieldBorder = Color(0xFFCFC9E3),
    pop = Color(0xFFD4F75A),
    onPop = Ink,
    emphasis = Color(0xFF3A22D6),
    danger = Color(0xFFB42318),
    scrim = Color(0x7316132B),
    iconBackground = Color(0xFFEEEAFB),
    dotOn = Ink,
    dotOff = Color(0xFFE4DFF3),
    avatarBackgrounds = AvatarBackgrounds,
    onAvatar = Ink,
)

val DarkKitColors = KitColors(
    isDark = true,
    page = Color(0xFF121026),
    surface = Color(0xFF1D1A36),
    hero = Color(0xFF2B2555),
    onHero = Color(0xFFF4F2FF),
    heroMuted = Color(0xFFC9C3E6),
    heroTrack = Color(0xFF443C78),
    heroFill = Color(0xFFCFC6FF),
    ink = Color(0xFFF4F2FF),
    muted = Color(0xFFB4AED3),
    line = Color(0xFF2C2848),
    track = Color(0xFF332D58),
    bar = Color(0xFFCFC6FF),
    button = Color(0xFFCFC6FF),
    onButton = Ink,
    button2 = Color(0xFF1D1A36),
    button2Border = Color(0xFF3A3560),
    field = Color(0xFF1D1A36),
    fieldBorder = Color(0xFF4A4470),
    pop = Color(0xFFD4F75A),
    onPop = Ink,
    emphasis = Color(0xFFCFC6FF),
    danger = Color(0xFFFF9A8F),
    scrim = Color(0x99000000),
    iconBackground = Color(0xFF2A2650),
    dotOn = Color(0xFFCFC6FF),
    dotOff = Color(0xFF332D58),
    avatarBackgrounds = AvatarBackgrounds,
    onAvatar = Ink,
)

val LocalKitColors = staticCompositionLocalOf { LightKitColors }

/**
 * The weekly card, a picture made to be posted, so its colours are the
 * canvas boards' own (B-WeeklyCard in Lilac, Dark-WeeklyCard) rather than the
 * screen tokens: the dark card sits on a deeper ground and uses lime tags.
 */
@Immutable
data class CardArtColors(
    val ground: Color,
    val onGround: Color,
    val mutedOnGround: Color,
    val emphasis: Color,
    val photoRing: Color,
    val tag: Color,
    val onTag: Color,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    val track: Color,
    val bar: Color,
    val accent: Color,
    val onAccent: Color,
)

val LightCardArt = CardArtColors(
    ground = Color(0xFFCFC6FF),
    onGround = Ink,
    mutedOnGround = Color(0xFF3E3960),
    emphasis = Color(0xFF3A22D6),
    photoRing = Color(0xFFFFFFFF),
    tag = Ink,
    onTag = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    muted = Color(0xFF5A5670),
    track = Color(0xFFEEEBFA),
    bar = Ink,
    accent = Ink,
    onAccent = Color(0xFFFFFFFF),
)

val DarkCardArt = CardArtColors(
    ground = Color(0xFF16122E),
    onGround = Color(0xFFF4F2FF),
    mutedOnGround = Color(0xFFB4AED3),
    emphasis = Color(0xFFCFC6FF),
    photoRing = Color(0xFFF4F2FF),
    tag = Color(0xFFD4F75A),
    onTag = Ink,
    surface = Color(0xFF221E40),
    onSurface = Color(0xFFF4F2FF),
    muted = Color(0xFFB4AED3),
    track = Color(0xFF3A3560),
    bar = Color(0xFFCFC6FF),
    accent = Color(0xFFCFC6FF),
    onAccent = Ink,
)
