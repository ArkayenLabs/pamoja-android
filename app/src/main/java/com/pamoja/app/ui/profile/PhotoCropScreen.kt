package com.pamoja.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pamoja.app.R
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PillShape
import com.pamoja.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Frames a picked photo before it is uploaded.
 *
 * The avatar is rendered as a circle everywhere in the app, and until now the
 * uploaded image kept whatever aspect ratio the source had, so the app decided
 * which part of someone's photo to show. A portrait shot of a person standing
 * up would be centre-cropped to their chest. This hands that choice back.
 *
 * Deliberately built here rather than pulled in as a cropping library. Every
 * such library ships its own Activity and its own Material theming, which would
 * arrive in the middle of a terracotta app looking like a different product,
 * and this needs one circular viewport and two gestures.
 */
@Composable
fun PhotoCropScreen(
    sourceUri: String,
    onCancel: () -> Unit,
    onCropped: (String) -> Unit,
) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var source by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(sourceUri) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    // Box size in pixels, needed to convert gesture offsets into source pixels.
    var boxSize by remember { mutableStateOf(Size.Zero) }

    // Float-specialised: this changes on every frame of a pinch, and the boxed
    // form allocates an Integer-style wrapper per update.
    var scale by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offset by remember(sourceUri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceUri) {
        source = withContext(Dispatchers.IO) { decodeForCrop(context, sourceUri) }
        failed = source == null
    }

    // The circular viewport, and the square the crop is taken from. Kept to the
    // narrower axis so it fits any screen in either orientation.
    val viewport = remember(boxSize) { min(boxSize.width, boxSize.height) * VIEWPORT_FRACTION }

    // Cover fit: the smaller image edge exactly fills the viewport at scale 1,
    // so there is never a gap inside the circle before the user touches it.
    val baseScale = remember(source, viewport) {
        val bmp = source
        if (bmp == null || viewport <= 0f) 1f
        else max(viewport / bmp.width, viewport / bmp.height)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        val bmp = source

        when {
            failed -> {
                Text(
                    text = stringResource(R.string.crop_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.x6),
                )
            }

            bmp == null -> {
                CircularProgressIndicator(
                    color = colors.accentPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                val effScale = baseScale * scale

                // Panning is clamped so the image always covers the circle.
                // Without this the photo can be dragged off the viewport and
                // the crop would include blank space the user never saw.
                val maxOffsetX = max(0f, (bmp.width * effScale - viewport) / 2f)
                val maxOffsetY = max(0f, (bmp.height * effScale - viewport) / 2f)

                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                    val next = offset + panChange
                    // Recomputed against the new scale, so zooming out cannot
                    // strand the image outside its own bounds.
                    val eff = baseScale * scale
                    val limitX = max(0f, (bmp.width * eff - viewport) / 2f)
                    val limitY = max(0f, (bmp.height * eff - viewport) / 2f)
                    offset = Offset(
                        next.x.coerceIn(-limitX, limitX),
                        next.y.coerceIn(-limitY, limitY),
                    )
                }

                // ContentScale.Fit has already drawn the bitmap letterboxed
                // inside the box at fitScale, so the layer only has to supply
                // the difference between that and the scale the crop assumes.
                //
                // One uniform factor, not one per axis: size here is the box,
                // not the drawn image, so a per-axis figure would both distort
                // the photo and disagree with the crop rectangle for any image
                // whose aspect ratio differs from the screen's.
                val fitScale = min(
                    boxSize.width / bmp.width,
                    boxSize.height / bmp.height,
                )
                val layerScale = if (fitScale > 0f) effScale / fitScale else 1f

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = layerScale
                            scaleY = layerScale
                            translationX = offset.x.coerceIn(-maxOffsetX, maxOffsetX)
                            translationY = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                        }
                        .transformable(transformState),
                )

                // Scrim with a hole punched in it. Drawn on its own offscreen
                // layer, because BlendMode.Clear against the window would clear
                // everything behind it rather than just the scrim.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
                            drawCircle(
                                color = Color.Black,
                                radius = viewport / 2f,
                                center = center,
                                blendMode = BlendMode.Clear,
                            )
                        },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Spacing.x6, vertical = Spacing.x5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.crop_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.x1))
            Text(
                text = stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.x6, vertical = Spacing.x6),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }

            Button(
                onClick = {
                    val bitmap = source ?: return@Button
                    working = true
                    // Off the main thread: this allocates a bitmap and JPEG
                    // encodes it, which is tens to hundreds of milliseconds on
                    // a real photo and would freeze the button mid press.
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            cropToCache(
                                context = context,
                                source = bitmap,
                                viewport = viewport,
                                effScale = baseScale * scale,
                                offset = offset,
                            )
                        }
                        working = false
                        if (saved != null) onCropped(saved) else onCancel()
                    }
                },
                enabled = source != null && !working,
                modifier = Modifier.weight(1f),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.textOnBrand,
                ),
            ) {
                Text(
                    text = stringResource(R.string.crop_confirm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Decodes the picked image at a size worth cropping from.
 *
 * Downsampled on the way in, for the same reason the upload path does it: a
 * full-size decode of a modern camera photo is tens of megabytes and this one
 * is held in composition while the user drags it around.
 *
 * [DECODE_EDGE_PX] is deliberately well above the 512px the avatar is finally
 * stored at, so that zooming in still has real pixels to crop from rather than
 * upscaling a thumbnail.
 */
private fun decodeForCrop(context: Context, imageUri: String): Bitmap? = runCatching {
    val uri = imageUri.toUri()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null

    // decodeStream returns null by contract while inJustDecodeBounds is set, so
    // the dimensions it wrote are the only usable signal. Null-checking its
    // result here is the bug that once rejected every photo ever picked.
    val longestEdge = max(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = generateSequence(1) { it * 2 }
            .first { longestEdge / it <= DECODE_EDGE_PX }
    }

    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()

/**
 * Maps the on-screen circle back to a square of source pixels and writes it out.
 *
 * The viewport is centred, so its centre in source coordinates is the image
 * centre shifted by the pan, divided back out of the display scale. Everything
 * is clamped to the bitmap because a rounding error at the edge would otherwise
 * throw out of Bitmap.createBitmap.
 *
 * Written as a square, not a circle. The circle is how the avatar is displayed,
 * and baking transparent corners into a JPEG would render them black.
 */
private fun cropToCache(
    context: Context,
    source: Bitmap,
    viewport: Float,
    effScale: Float,
    offset: Offset,
): String? = runCatching {
    if (viewport <= 0f || effScale <= 0f) return null

    val halfSrc = (viewport / 2f) / effScale
    val centreX = source.width / 2f - offset.x / effScale
    val centreY = source.height / 2f - offset.y / effScale

    val left = (centreX - halfSrc).toInt().coerceIn(0, source.width - 1)
    val top = (centreY - halfSrc).toInt().coerceIn(0, source.height - 1)
    val size = (halfSrc * 2f).toInt()
        .coerceAtLeast(1)
        .coerceAtMost(min(source.width - left, source.height - top))

    val cropped = Bitmap.createBitmap(source, left, top, size, size)

    val file = File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        cropped.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, out)
    }
    if (cropped !== source) cropped.recycle()

    Uri.fromFile(file).toString()
}.getOrNull()

private const val VIEWPORT_FRACTION = 0.78f
private const val SCRIM_ALPHA = 0.72f
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DECODE_EDGE_PX = 2048

/**
 * Higher than the upload's 85. This file is an intermediate that gets
 * recompressed by the upload path, and compressing twice at the same quality
 * compounds the loss.
 */
private const val CROP_QUALITY = 95
