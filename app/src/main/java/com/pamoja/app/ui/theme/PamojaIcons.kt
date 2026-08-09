package com.pamoja.app.ui.theme

import androidx.annotation.DrawableRes
import com.pamoja.app.R

/**
 * Pamoja's custom icon set. Lucide (MIT) line icons imported as vector drawables
 * in res/drawable/ic_*. Use with `Icon(painterResource(PamojaIcons.Xxx), ...)`;
 * they tint to the current content color like any Material icon.
 *
 * Rule: never use emoji as an icon, label, or list marker anywhere in the app.
 * Add a new glyph here (and a matching res/drawable) rather than reaching for one.
 */
object PamojaIcons {
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Link = R.drawable.ic_link
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val ArrowLeft = R.drawable.ic_arrow_left
    @DrawableRes val ArrowRight = R.drawable.ic_arrow_right
    @DrawableRes val Users = R.drawable.ic_users
    @DrawableRes val Share = R.drawable.ic_share_2
    @DrawableRes val Footprints = R.drawable.ic_footprints
    @DrawableRes val Edit = R.drawable.ic_pencil
    @DrawableRes val Copy = R.drawable.ic_copy
    @DrawableRes val Shield = R.drawable.ic_shield
    @DrawableRes val ShieldCheck = R.drawable.ic_shield_check
    @DrawableRes val Star = R.drawable.ic_star
    @DrawableRes val LogOut = R.drawable.ic_log_out
    @DrawableRes val Trash = R.drawable.ic_trash_2
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Add = R.drawable.ic_plus
    @DrawableRes val Trophy = R.drawable.ic_trophy
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val Camera = R.drawable.ic_camera
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Medal = R.drawable.ic_medal
    @DrawableRes val Smartphone = R.drawable.ic_smartphone
    @DrawableRes val Mail = R.drawable.ic_mail
    @DrawableRes val Eye = R.drawable.ic_eye
    @DrawableRes val EyeOff = R.drawable.ic_eye_off
    @DrawableRes val AlertCircle = R.drawable.ic_alert_circle
    @DrawableRes val Clock = R.drawable.ic_clock
    @DrawableRes val Refresh = R.drawable.ic_refresh
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Close = R.drawable.ic_x
    @DrawableRes val Notification = R.drawable.ic_notification

    /**
     * The Google "G". Unlike everything else here it is multi-colour and must be
     * rendered untinted, so pass `tint = Color.Unspecified`. Google's brand rules
     * forbid redrawing or recolouring the mark.
     */
    @DrawableRes val Google = R.drawable.ic_google
}
