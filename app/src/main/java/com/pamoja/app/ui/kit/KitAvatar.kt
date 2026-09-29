package com.pamoja.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pamoja.app.ui.theme.PamojaIcons
import kotlin.math.absoluteValue

/**
 * A member's face. Faces come first in this design, so this is the most
 * repeated component in the app.
 *
 * Without a photo it shows initials on a soft colour chosen from [seed],
 * which is stable per person so someone keeps their colour across screens and
 * phones. Pass the user id as [seed] where there is one: two people called
 * Priya should not share a colour. The fallback is drawn under the photo as
 * well, so a slow or failed load shows initials rather than a blank circle.
 *
 * [walked] adds the lime check used on today's faces; [dimmed] greys out a
 * face that has not walked yet today, so the row reads at a glance.
 */
@Composable
fun Avatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    seed: String = name,
    walked: Boolean = false,
    dimmed: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = Kit.colors
    val background = if (dimmed) {
        DimmedBackground
    } else {
        colors.avatarBackgrounds[seed.hashCode().absoluteValue % colors.avatarBackgrounds.size]
    }

    Box(modifier = modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .alpha(if (dimmed) DimmedAlpha else 1f)
                .clip(CircleShape)
                .background(background),
        ) {
            Text(
                text = initialsOf(name),
                color = colors.onAvatar,
                // Scaled to the circle, not to the user's font size: initials
                // that outgrow their circle help nobody, and the name is always
                // written out next to a face.
                fontSize = (size.value * 0.38f / LocalDensity.current.fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Kit.type.bodyStrong.fontFamily,
            )
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (dimmed) Greyscale else null,
                    modifier = Modifier.size(size),
                )
            }
        }
        if (walked) {
            val badge = (size.value * 0.44f).coerceAtLeast(16f).dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(badge)
                    .clip(CircleShape)
                    .background(colors.pop)
                    .border(2.dp, colors.surface, CircleShape),
            ) {
                Icon(
                    painter = painterResource(PamojaIcons.Check),
                    contentDescription = null,
                    tint = colors.onPop,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

private const val DimmedAlpha = 0.5f

/**
 * A neutral grey rather than a theme token: at half opacity it has to stay
 * visible on both the white and the deep violet card. The dark track colour
 * all but vanished there in the first screenshots.
 */
private val DimmedBackground = Color(0xFFDCDAE3)

private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** "Priya" -> "P", "Ravi Sharma" -> "RS", "" -> "?". */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}
