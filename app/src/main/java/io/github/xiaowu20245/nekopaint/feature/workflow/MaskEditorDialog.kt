package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.ui.component.NekoConfirmDialog
import io.github.xiaowu20245.nekopaint.feature.common.asString
import io.github.xiaowu20245.nekopaint.data.repository.ImageDimensionReader
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 单图蒙版编辑器。
 *
 * 不修改原图，只记录归一化笔迹；上传时按原图尺寸（最大 4096）导出黑白 PNG。
 */
@Composable
fun MaskEditorDialog(
    images: List<HistoryImage>,
    uploading: Boolean,
    uploadError: NekoMessage?,
    dimensionReader: ImageDimensionReader? = null,
    onDismiss: () -> Unit,
    onUpload: (List<MaskStroke>, IntSize, Boolean) -> Unit,
) {
    var selectedImage by remember { mutableStateOf<HistoryImage?>(null) }
    var sourceSize by remember { mutableStateOf<IntSize?>(null) }
    var decodedSize by remember { mutableStateOf<IntSize?>(null) }
    var imageLoadFailed by remember { mutableStateOf(false) }
    var loadTimedOut by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var brushSize by remember { mutableStateOf(28f) }
    var eraser by remember { mutableStateOf(false) }
    var inverted by remember { mutableStateOf(false) }
    var currentStroke by remember { mutableStateOf<MaskStroke?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<MaskStroke>() }
    val redoStrokes = remember { mutableStateListOf<MaskStroke>() }
    val candidates = remember(images) { images.filter { !it.url.isNullOrBlank() } }
    val hasChanges = MaskEditorPolicy.requiresExitConfirmation(strokes, inverted)
    val requestClose: () -> Unit = {
        if (hasChanges) showDiscardConfirm = true else onDismiss()
    }

    LaunchedEffect(selectedImage?.url, loadAttempt) {
        sourceSize = null
        decodedSize = null
        imageLoadFailed = false
        loadTimedOut = false
        currentStroke = null
        strokes.clear()
        redoStrokes.clear()
        inverted = false
        val url = selectedImage?.url
        if (url != null && dimensionReader != null) {
            dimensionReader.read(url.toUri()).getOrNull()?.let { dimensions ->
                // 头解析得到的是源图尺寸，不能被 Coil 的解码/显示尺寸覆盖。
                sourceSize = IntSize(dimensions.width, dimensions.height)
            }
        }
        if (selectedImage != null) {
            delay(20_000)
            if (decodedSize == null) {
                loadTimedOut = true
            }
        }
    }

    if (showDiscardConfirm) {
        NekoConfirmDialog(
            title = stringResource(R.string.mask_editor_discard_title),
            message = stringResource(R.string.mask_editor_discard_message),
            confirmLabel = stringResource(R.string.mask_editor_discard_confirm),
            onConfirm = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismiss = { showDiscardConfirm = false },
        )
    }

    Dialog(
        onDismissRequest = requestClose,
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
                    IconButton(onClick = requestClose) {
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
                        imageSize = decodedSize ?: sourceSize,
                        imageLoadFailed = imageLoadFailed,
                        loadTimedOut = loadTimedOut,
                        loadAttempt = loadAttempt,
                        onRetry = {
                            imageLoadFailed = false
                            loadTimedOut = false
                            loadAttempt += 1
                        },
                        onImageSize = { decoded ->
                            decodedSize = decoded
                            if (sourceSize == null) sourceSize = decoded
                        },
                        onImageFailed = { imageLoadFailed = true },
                        onStrokeChange = { currentStroke = it },
                        onStrokeCommit = { stroke ->
                            strokes.add(stroke)
                            redoStrokes.clear()
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
                            val history = MaskEditorPolicy.undo(
                                strokes.toList(),
                                redoStrokes.toList(),
                            )
                            strokes.clear()
                            strokes.addAll(history.strokes)
                            redoStrokes.clear()
                            redoStrokes.addAll(history.redo)
                            currentStroke = null
                        },
                        canRedo = redoStrokes.isNotEmpty(),
                        onRedo = {
                            val history = MaskEditorPolicy.redo(
                                strokes.toList(),
                                redoStrokes.toList(),
                            )
                            strokes.clear()
                            strokes.addAll(history.strokes)
                            redoStrokes.clear()
                            redoStrokes.addAll(history.redo)
                            currentStroke = null
                        },
                        inverted = inverted,
                        onToggleInverted = {
                            inverted = MaskEditorPolicy.toggleInverted(inverted)
                        },
                        onClear = {
                            val cleared = MaskEditorPolicy.clear()
                            strokes.clear()
                            strokes.addAll(cleared.strokes)
                            redoStrokes.clear()
                            inverted = false
                            currentStroke = null
                        },
                        uploading = uploading,
                        canUpload = (strokes.isNotEmpty() || inverted) && sourceSize != null,
                        uploadError = uploadError,
                        onPickAnother = { selectedImage = null },
                        onUpload = {
                            val size = sourceSize ?: return@MaskTools
                            onUpload(strokes.toList(), size, inverted)
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
    loadTimedOut: Boolean,
    loadAttempt: Int,
    onRetry: () -> Unit,
    onImageSize: (IntSize) -> Unit,
    onImageFailed: () -> Unit,
    onStrokeChange: (MaskStroke?) -> Unit,
    onStrokeCommit: (MaskStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(image.url) { mutableFloatStateOf(1f) }
    var pan by remember(image.url) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
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
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val brushFraction = if (displayWidthDp.value > 0f) {
            brushSize / displayWidthDp.value
        } else {
            0.02f
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(displayWidthDp, displayHeightDp)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            key(loadAttempt) {
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
                        if (size.width > 0 && size.height > 0) {
                            if (size != imageSize) onImageSize(size)
                        } else {
                            onImageFailed()
                        }
                    },
                    onError = { onImageFailed() },
                )
            }

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
                                fun normalized(position: Offset): Offset {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    return Offset(
                                        (((position.x - cx - pan.x) / zoom + cx) / size.width)
                                            .coerceIn(0f, 1f),
                                        (((position.y - cy - pan.y) / zoom + cy) / size.height)
                                            .coerceIn(0f, 1f),
                                    )
                                }
                                val points = mutableListOf(normalized(down.position))
                                var drawing = true
                                var transforming = false
                                onStrokeChange(
                                    MaskStroke(
                                        points = points.toList(),
                                        widthFraction = brushFraction,
                                        eraser = eraser,
                                    ),
                                )
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        if (drawing) {
                                            drawing = false
                                            onStrokeChange(null)
                                        }
                                        transforming = true
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val newZoom = MaskEditorPolicy.clampZoom(zoom * zoomChange)
                                        zoom = newZoom
                                        pan = MaskEditorPolicy.clampPan(
                                            pan = pan + panChange,
                                            viewportWidth = viewportWidthPx,
                                            viewportHeight = viewportHeightPx,
                                            baseWidth = displayWidthPx,
                                            baseHeight = displayHeightPx,
                                            zoom = newZoom,
                                        )
                                        event.changes.forEach { it.consume() }
                                    } else if (pressed.size == 1 && !transforming) {
                                        points += normalized(pressed.first().position)
                                        onStrokeChange(
                                            MaskStroke(
                                                points = points.toList(),
                                                widthFraction = brushFraction,
                                                eraser = eraser,
                                            ),
                                        )
                                        pressed.first().consume()
                                    } else if (pressed.isEmpty()) {
                                        break
                                    }
                                }
                                if (drawing) {
                                    onStrokeCommit(
                                        MaskStroke(
                                            points = points.toList(),
                                            widthFraction = brushFraction,
                                            eraser = eraser,
                                        ),
                                    )
                                }
                            }
                        },
                ) {
                    strokes.forEach { drawMaskStroke(it) }
                    currentStroke?.let { drawMaskStroke(it) }
                }
            }

            if (source == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (imageLoadFailed || loadTimedOut) {
                            Text(
                                text = stringResource(
                                    if (loadTimedOut) {
                                        R.string.mask_editor_load_timeout
                                    } else {
                                        R.string.mask_editor_image_failed
                                    },
                                ),
                                color = Color.White,
                            )
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.mask_editor_retry))
                            }
                        } else {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.mask_editor_loading_image),
                                color = Color.White,
                            )
                        }
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
    canRedo: Boolean,
    onRedo: () -> Unit,
    inverted: Boolean,
    onToggleInverted: () -> Unit,
    onClear: () -> Unit,
    uploading: Boolean,
    canUpload: Boolean,
    uploadError: NekoMessage?,
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onUndo, enabled = canUndo && !uploading) {
                Text(stringResource(R.string.mask_editor_undo))
            }
            OutlinedButton(onClick = onRedo, enabled = canRedo && !uploading) {
                Text(stringResource(R.string.mask_editor_redo))
            }
            OutlinedButton(
                onClick = onToggleInverted,
                enabled = !uploading,
            ) {
                Text(
                    stringResource(
                        if (inverted) {
                            R.string.mask_editor_invert_on
                        } else {
                            R.string.mask_editor_invert
                        },
                    ),
                )
            }
            OutlinedButton(onClick = onClear, enabled = (canUndo || inverted) && !uploading) {
                Text(stringResource(R.string.mask_editor_clear))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
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
                text = uploadError.asString(),
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
