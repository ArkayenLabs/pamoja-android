package com.pamoja.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pamoja.app.ui.theme.PamojaRadii

/**
 * The invite link as a scannable code.
 *
 * Drawn straight onto a Canvas from the module matrix rather than rendered to a
 * Bitmap, so it stays sharp at any size and there is no bitmap to allocate,
 * scale or leak.
 *
 * **Always light, in both themes.** A QR on a dark background is the inverse of
 * what scanners expect, and many refuse to read it. This is one of the few
 * places where ignoring the theme is the correct choice, so the white card is
 * deliberate rather than an oversight.
 */
@Composable
fun PamojaQrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    // Remembered against the content only. Encoding is cheap but not free, and
    // this would otherwise run on every recomposition of the invite screen.
    val matrix = remember(content) { encodeQr(content) } ?: return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(PamojaRadii.md))
            .background(Color.White)
            // Quiet zone. A QR needs clear margin around it or scanners fail to
            // find the finder patterns against whatever is behind the card.
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val modules = matrix.size
            val moduleSize = this.size.width / modules

            for (row in 0 until modules) {
                for (col in 0 until modules) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * moduleSize, row * moduleSize),
                            // Half a pixel of overlap, otherwise antialiasing
                            // leaves hairline gaps between modules that soften
                            // the edges a scanner is looking for.
                            size = Size(moduleSize + 0.5f, moduleSize + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Encodes to a square boolean matrix, or null if the content cannot be encoded.
 *
 * Null rather than a throw: a missing QR is a degraded invite screen, and the
 * link and share button next to it still work.
 */
private fun encodeQr(content: String): Array<BooleanArray>? = runCatching {
    val hints = mapOf(
        // Medium recovers from ~15% damage. Enough for a screen being
        // photographed at an angle, without inflating the module count the way
        // High would and making it harder to scan from a distance.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )

    // 0x0 asks zxing for the natural size, so the matrix has exactly one cell
    // per module and the Canvas decides the scale.
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)

    Array(bitMatrix.height) { row ->
        BooleanArray(bitMatrix.width) { col -> bitMatrix.get(col, row) }
    }
}.getOrNull()
