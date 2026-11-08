package io.github.xiaowu20245.nekopaint.feature.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoRadius
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale

/**
 * 1:1 图标裁剪弹窗。
 *
 * 源图先采样解码，再通过缩放/平移在方形裁剪框内定位，最终导出 256×256 PNG。
 */
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var frameSize by remember(uri) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        bitmap = decodeSampledBitmap(context, uri)
        loadFailed = bitmap == null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(NekoSpacing.md),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NekoSpacing.xxs)) {
                    Text(
                        text = stringResource(R.string.server_avatar_crop_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.server_avatar_crop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(NekoRadius.md))
                        .background(Color(0xFF121318)),
                    contentAlignment = Alignment.Center,
                ) {
                    val source = bitmap
                    if (source == null) {
                        if (loadFailed) {
                            Text(
                                text = stringResource(R.string.server_avatar_crop_failed),
                                color = Color.White,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    } else {
                        val imageBitmap = remember(source) { source.asImageBitmap() }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { frameSize = it }
                                .pointerInput(source, frameSize) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (frameSize.width <= 0 || frameSize.height <= 0) {
                                            return@detectTransformGestures
                                        }
                                        val newScale = (scale * zoom).coerceIn(1f, 3f)
                                        val nextOffset = clampOffset(
                                            offset = offset + pan,
                                            scale = newScale,
                                            frameSize = frameSize,
                                            bitmap = source,
                                        )
                                        scale = newScale
                                        offset = nextOffset
                                    }
                                },
                        ) {
                            val frame = cropSide(size.width, size.height)
                            if (frame <= 0f) return@Canvas
                            val base = coverScale(size.width, size.height, source)
                            val effective = base * scale
                            val drawWidth = source.width * effective
                            val drawHeight = source.height * effective
                            val left = size.width / 2f - drawWidth / 2f + offset.x
                            val top = size.height / 2f - drawHeight / 2f + offset.y
                            drawImage(
                                image = imageBitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(source.width, source.height),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(
                                    drawWidth.roundToInt(),
                                    drawHeight.roundToInt(),
                                ),
                            )

                            // 裁剪框外压暗，突出 1:1 取样范围
                            val mask = Color.Black.copy(alpha = 0.55f)
                            val frameLeft = (size.width - frame) / 2f
                            val frameTop = (size.height - frame) / 2f
                            drawRect(
                                color = mask,
                                topLeft = Offset.Zero,
                                size = Size(size.width, frameTop),
                            )
                            drawRect(
                                color = mask,
                                topLeft = Offset(0f, frameTop + frame),
                                size = Size(size.width, size.height - frameTop - frame),
                            )
                            drawRect(
                                color = mask,
                                topLeft = Offset(0f, frameTop),
                                size = Size(frameLeft, frame),
                            )
                            drawRect(
                                color = mask,
                                topLeft = Offset(frameLeft + frame, frameTop),
                                size = Size(
                                    size.width - frameLeft - frame,
                                    frame,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(CROP_FRAME_RATIO)
                                .aspectRatio(1f)
                                .border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(NekoRadius.md),
                                ),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.server_avatar_zoom),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = scale,
                        onValueChange = { value ->
                            val source = bitmap ?: return@Slider
                            scale = value
                            offset = clampOffset(offset, value, frameSize, source)
                        },
                        valueRange = 1f..3f,
                        modifier = Modifier.weight(1f),
                        enabled = bitmap != null,
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", scale),
                        style = NekoMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    ) {
                        Text(stringResource(R.string.server_avatar_reset))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.server_cancel))
                    }
                    Spacer(Modifier.size(NekoSpacing.sm))
                    Button(
                        onClick = {
                            val source = bitmap ?: return@Button
                            if (frameSize.width <= 0) return@Button
                            onCropped(
                                cropToAvatar(
                                    source = source,
                                    viewportWidth = frameSize.width.toFloat(),
                                    viewportHeight = frameSize.height.toFloat(),
                                    scale = scale,
                                    offset = offset,
                                ),
                            )
                        },
                        enabled = bitmap != null && frameSize.width > 0,
                    ) {
                        Text(stringResource(R.string.server_avatar_crop_confirm))
                    }
                }
            }
        }
    }
}

private fun coverScale(width: Float, height: Float, bitmap: Bitmap): Float =
    max(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())

private fun cropSide(width: Float, height: Float): Float =
    min(width, height) * CROP_FRAME_RATIO

private fun clampOffset(
    offset: Offset,
    scale: Float,
    frameSize: IntSize,
    bitmap: Bitmap,
): Offset {
    val frame = cropSide(frameSize.width.toFloat(), frameSize.height.toFloat())
    val effective = coverScale(
        frameSize.width.toFloat(),
        frameSize.height.toFloat(),
        bitmap,
    ) * scale
    val maxX = max(0f, (bitmap.width * effective - frame) / 2f)
    val maxY = max(0f, (bitmap.height * effective - frame) / 2f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

private fun cropToAvatar(
    source: Bitmap,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offset: Offset,
): Bitmap {
    val frameSize = cropSide(viewportWidth, viewportHeight)
    val effective = coverScale(viewportWidth, viewportHeight, source) * scale
    val drawWidth = source.width * effective
    val drawHeight = source.height * effective
    val left = frameSize / 2f - drawWidth / 2f + offset.x
    val top = frameSize / 2f - drawHeight / 2f + offset.y
    val requested = (frameSize / effective).roundToInt().coerceAtLeast(1)
    val rawX = (-left / effective).roundToInt()
    val rawY = (-top / effective).roundToInt()
    val side = min(
        requested,
        min(source.width - rawX, source.height - rawY),
    ).coerceAtLeast(1)
    val sx = rawX.coerceIn(0, max(0, source.width - side))
    val sy = rawY.coerceIn(0, max(0, source.height - side))
    val safeSide = min(side, min(source.width - sx, source.height - sy)).coerceAtLeast(1)

    val output = createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        source,
        AndroidRect(sx, sy, sx + safeSide, sy + safeSide),
        AndroidRect(0, 0, 256, 256),
        paint,
    )
    return output
}

private suspend fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val imageSource = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > MAX_SOURCE_SIZE) {
                        decoder.setTargetSampleSize(
                            ceil(longest.toDouble() / MAX_SOURCE_SIZE).toInt(),
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SOURCE_SIZE) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        }.getOrNull()
    }

private const val MAX_SOURCE_SIZE = 1600
private const val CROP_FRAME_RATIO = 0.78f
