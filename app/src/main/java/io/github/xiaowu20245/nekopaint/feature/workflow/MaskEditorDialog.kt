package io.github.xiaowu20245.nekopaint.feature.workflow

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import kotlin.math.min
import kotlin.math.roundToInt

private data class MaskStroke(
    val points: List<Offset>,
    val widthFraction: Float,
    val eraser: Boolean,
)

/**
 * 单图蒙版编辑器。
 *
 * 不修改原图，只记录归一化笔迹；上传时按原图尺寸（最大 4096）导出黑白 PNG。
 */
@Composable
fun MaskEditorDialog(
    images: List<HistoryImage>,
    uploading: Boolean,
    uploadError: String?,
    onDismiss: () -> Unit,
    onUpload: (Bitmap) -> Unit,
) {
    var selectedImage by remember { mutableStateOf<HistoryImage?>(null) }
    var imageSize by remember { mutableStateOf<IntSize?>(null) }
    var imageLoadFailed by remember { mutableStateOf(false) }
    var brushSize by remember { mutableStateOf(28f) }
    var eraser by remember { mutableStateOf(false) }
    var currentStroke by remember { mutableStateOf<MaskStroke?>(null) }
    val strokes = remember { mutableStateListOf<MaskStroke>() }
    val candidates = remember(images) { images.filter { !it.url.isNullOrBlank() } }

    LaunchedEffect(selectedImage?.url) {
        imageSize = null
        imageLoadFailed = false
        currentStroke = null
        strokes.clear()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mask_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.mask_editor_close),
                        )
                    }
                }

                if (selectedImage == null) {
                    ImageSourcePicker(
                        images = candidates,
                        onSelect = { selectedImage = it },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MaskCanvas(
                        image = selectedImage!!,
                        strokes = strokes,
                        currentStroke = currentStroke,
                        brushSize = brushSize,
                        eraser = eraser,
                        imageSize = imageSize,
                        imageLoadFailed = imageLoadFailed,
                        onImageSize = { imageSize = it },
                        onImageFailed = { imageLoadFailed = true },
                        onStrokeChange = { currentStroke = it },
                        onStrokeCommit = { stroke ->
                            strokes.add(stroke)
                            currentStroke = null
                        },
                        modifier = Modifier.weight(1f),
                    )

                    MaskTools(
                        brushSize = brushSize,
                        onBrushSizeChange = { brushSize = it },
                        eraser = eraser,
                        onEraserChange = { eraser = it },
                        canUndo = strokes.isNotEmpty(),
                        onUndo = {
                            if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                        },
                        onClear = {
                            strokes.clear()
                            currentStroke = null
                        },
                        uploading = uploading,
                        canUpload = strokes.isNotEmpty() && imageSize != null,
                        uploadError = uploadError,
                        onPickAnother = { selectedImage = null },
                        onUpload = {
                            val size = imageSize ?: return@MaskTools
                            val bitmap = renderMaskBitmap(size.width, size.height, strokes)
                            onUpload(bitmap)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSourcePicker(
    images: List<HistoryImage>,
    onSelect: (HistoryImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.mask_editor_pick_image),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (images.isEmpty()) {
            Text(
                text = stringResource(R.string.mask_editor_no_images),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images, key = { it.url ?: it.filename }) { image ->
                    Card(onClick = { onSelect(image) }) {
                        AsyncImage(
                            model = image.url,
                            contentDescription = image.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                        Text(
                            text = image.filename,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaskCanvas(
    image: HistoryImage,
    strokes: List<MaskStroke>,
    currentStroke: MaskStroke?,
    brushSize: Float,
    eraser: Boolean,
    imageSize: IntSize?,
    imageLoadFailed: Boolean,
    onImageSize: (IntSize) -> Unit,
    onImageFailed: () -> Unit,
    onStrokeChange: (MaskStroke?) -> Unit,
    onStrokeCommit: (MaskStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp),
    ) {
        val density = LocalDensity.current
        val source = imageSize?.takeIf { it.width > 0 && it.height > 0 }
        val maxWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val scale = if (source != null) {
            min(
                maxWidthPx / source.width.toFloat(),
                maxHeightPx / source.height.toFloat(),
            )
        } else {
            1f
        }
        val displayWidthPx = if (source != null) {
            (source.width * scale).coerceAtLeast(1f)
        } else {
            maxWidthPx
        }
        val displayHeightPx = if (source != null) {
            (source.height * scale).coerceAtLeast(1f)
        } else {
            maxHeightPx
        }
        val displayWidthDp = with(density) { displayWidthPx.toDp() }
        val displayHeightDp = with(density) { displayHeightPx.toDp() }
        val brushFraction = if (displayWidthDp.value > 0f) {
            brushSize / displayWidthDp.value
        } else {
            0.02f
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(displayWidthDp, displayHeightDp),
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = image.filename,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { success ->
                    val size = IntSize(
                        success.result.image.width,
                        success.result.image.height,
                    )
                    if (size != imageSize) onImageSize(size)
                },
                onError = { onImageFailed() },
            )

            if (source != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .pointerInput(brushFraction, eraser) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val points = mutableListOf(
                                    Offset(
                                        (down.position.x / size.width).coerceIn(0f, 1f),
                                        (down.position.y / size.height).coerceIn(0f, 1f),
                                    ),
                                )
                                onStrokeChange(
                                    MaskStroke(
                                        points = points.toList(),
                                        widthFraction = brushFraction,
                                        eraser = eraser,
                                    ),
                                )
                                drag(down.id) { change ->
                                    points += Offset(
                                        (change.position.x / size.width).coerceIn(0f, 1f),
                                        (change.position.y / size.height).coerceIn(0f, 1f),
                                    )
                                    onStrokeChange(
                                        MaskStroke(
                                            points = points.toList(),
                                            widthFraction = brushFraction,
                                            eraser = eraser,
                                        ),
                                    )
                                    change.consume()
                                }
                                onStrokeCommit(
                                    MaskStroke(
                                        points = points.toList(),
                                        widthFraction = brushFraction,
                                        eraser = eraser,
                                    ),
                                )
                            }
                        },
                ) {
                    strokes.forEach { drawMaskStroke(it) }
                    currentStroke?.let { drawMaskStroke(it) }
                }
            }

            if (source == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageLoadFailed) {
                        Text(
                            text = stringResource(R.string.mask_editor_image_failed),
                            color = Color.White,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMaskStroke(stroke: MaskStroke) {
    val color = if (stroke.eraser) Color.Transparent else Color.Red.copy(alpha = 0.45f)
    val blendMode = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
    val width = (stroke.widthFraction * size.width).coerceAtLeast(1f)
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = width / 2f,
            center = Offset(point.x * size.width, point.y * size.height),
            blendMode = blendMode,
        )
    } else {
        val path = Path().apply {
            val first = stroke.points.first()
            moveTo(first.x * size.width, first.y * size.height)
            stroke.points.drop(1).forEach { point ->
                lineTo(point.x * size.width, point.y * size.height)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = blendMode,
        )
    }
}

@Composable
private fun MaskTools(
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    eraser: Boolean,
    onEraserChange: (Boolean) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    uploading: Boolean,
    canUpload: Boolean,
    uploadError: String?,
    onPickAnother: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !eraser,
                onClick = { onEraserChange(false) },
                label = { Text(stringResource(R.string.mask_editor_brush)) },
            )
            FilterChip(
                selected = eraser,
                onClick = { onEraserChange(true) },
                label = { Text(stringResource(R.string.mask_editor_eraser)) },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPickAnother) {
                Text(stringResource(R.string.mask_editor_change_image))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mask_editor_brush_size),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 4f..80f,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onUndo, enabled = canUndo && !uploading) {
                Text(stringResource(R.string.mask_editor_undo))
            }
            OutlinedButton(onClick = onClear, enabled = canUndo && !uploading) {
                Text(stringResource(R.string.mask_editor_clear))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onUpload, enabled = canUpload && !uploading) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (uploading) {
                            R.string.mask_editor_uploading
                        } else {
                            R.string.mask_editor_upload
                        },
                    ),
                )
            }
        }
        if (uploadError != null) {
            Text(
                text = stringResource(R.string.workflow_image_upload_failed, uploadError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.mask_editor_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 导出黑白蒙版：白色=重绘区域，黑色=保留区域。
 */
private fun renderMaskBitmap(
    sourceWidth: Int,
    sourceHeight: Int,
    strokes: List<MaskStroke>,
): Bitmap {
    val maxDimension = 4096f
    val scale = min(
        1f,
        maxDimension / maxOf(sourceWidth, sourceHeight).toFloat(),
    )
    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.BLACK)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    strokes.forEach { stroke ->
        val color = if (stroke.eraser) AndroidColor.BLACK else AndroidColor.WHITE
        val strokeWidth = (stroke.widthFraction * width).coerceAtLeast(1f)
        strokePaint.color = color
        strokePaint.strokeWidth = strokeWidth
        dotPaint.color = color
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            canvas.drawCircle(
                point.x * width,
                point.y * height,
                strokeWidth / 2f,
                dotPaint,
            )
        } else {
            val path = AndroidPath()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            canvas.drawPath(path, strokePaint)
        }
    }
    return bitmap
}
