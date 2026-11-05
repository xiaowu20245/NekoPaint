package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.roundToInt

@Composable
fun ImagePreviewDialog(
    images: List<HistoryImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val startIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val zoomScales = remember { mutableStateMapOf<Int, Float>() }
    var pagerEnabled by remember { mutableStateOf(true) }
    val currentImage = images[pagerState.currentPage.coerceIn(0, images.lastIndex)]
    val currentScale = zoomScales[pagerState.currentPage] ?: 1f

    LaunchedEffect(pagerState.currentPage) {
        pagerEnabled = (zoomScales[pagerState.currentPage] ?: 1f) <= 1.01f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerEnabled,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val image = images[page]
                    ZoomableRemoteImage(
                        url = image.url,
                        contentDescription = image.filename,
                        onScaleChange = { scale ->
                            zoomScales[page] = scale
                            if (page == pagerState.currentPage) {
                                pagerEnabled = scale <= 1.01f
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentImage.filename,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.image_viewer_close),
                            tint = Color.White,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.image_viewer_page,
                            pagerState.currentPage + 1,
                            images.size,
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(R.string.image_viewer_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (currentScale > 1.01f) {
                        Text(
                            text = stringResource(
                                R.string.image_viewer_zoom,
                                (currentScale * 100).roundToInt(),
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableRemoteImage(
    url: String?,
    contentDescription: String,
    onScaleChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(scale) {
        onScaleChange(scale)
    }

    // 重试时改变 URL，绕过 Coil/OkHttp 可能缓存的失败结果。
    val retryUrl = remember(url, retryKey) {
        url?.let {
            if (retryKey == 0) {
                it
            } else {
                it.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("retry", retryKey.toString())
                    .build()
                    .toString()
            }
        }
    }
    val request = remember(retryUrl) {
        ImageRequest.Builder(context)
            .data(retryUrl)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        // 单指且未放大时把事件留给 HorizontalPager，保证左右切换。
                        if (pressedCount > 1 || scale > 1.01f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            scale = newScale
                            offset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                )
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        val newScale = if (scale > 1.01f) 1f else DOUBLE_TAP_SCALE
                        scale = newScale
                        offset = Offset.Zero
                    },
                )
            },
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.image_load_failed),
                            color = Color.White,
                        )
                        TextButton(
                            onClick = { retryKey += 1 },
                        ) {
                            Text(stringResource(R.string.image_retry))
                        }
                    }
                }
            },
        )
    }
}

private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
