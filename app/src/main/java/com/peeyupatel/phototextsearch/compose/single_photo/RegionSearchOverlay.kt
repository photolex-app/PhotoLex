package com.peeyupatel.phototextsearch.compose.single_photo

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.peeyupatel.phototextsearch.R
import com.peeyupatel.phototextsearch.compose.app_bars.BottomAppBarItem
import com.peeyupatel.phototextsearch.compose.app_bars.FloatingBottomAppBar
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarController
import com.peeyupatel.phototextsearch.lavender_snackbars.LavenderSnackbarEvents
import com.peeyupatel.phototextsearch.ocr.EnhancedOcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val MIN_REGION_SIZE_DP = 32
private const val MAX_DECODE_DIMENSION = 2048

/**
 * Full-screen "circle/mark a part of this photo, then search by what's in it" overlay -- the
 * user draws a freeform mark over the area with a finger (like a highlighter/pen), and the
 * mark's bounding box is what actually gets cropped and OCR'd (ML Kit takes a rectangular
 * Bitmap regardless, so an irregular mask wouldn't change what gets recognized -- freeform
 * drawing is just a faster, more natural way to point at an area than dragging corner handles).
 * Not a reuse of EditingView's crop UI (tightly coupled to that screen's own rotation/zoom/
 * pager state) -- only its screen-to-bitmap coordinate math is reused here. Decodes its own
 * bitmap from [imageUri] downsampled to a bounded max dimension (matching the OCR pipeline's
 * own downsampling elsewhere), since the photo pager's already-displayed bitmap isn't exposed
 * to this composable.
 */
@Composable
fun RegionSearchOverlay(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val ratio = min(
                        1f,
                        MAX_DECODE_DIMENSION.toFloat() / max(info.size.width, info.size.height)
                    )
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1)
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap == null) decodeFailed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val bmp = bitmap

        when {
            decodeFailed -> {
                Text(
                    text = "Couldn't load this photo for region search.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            bmp == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            else -> {
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val containerWidthPx = constraints.maxWidth.toFloat()
                    val containerHeightPx = constraints.maxHeight.toFloat()
                    val scale = remember(containerWidthPx, containerHeightPx, bmp) {
                        min(containerWidthPx / bmp.width, containerHeightPx / bmp.height)
                    }
                    val displayedWidth = bmp.width * scale
                    val displayedHeight = bmp.height * scale
                    val imageLeft = (containerWidthPx - displayedWidth) / 2f
                    val imageTop = (containerHeightPx - displayedHeight) / 2f
                    val imageRight = imageLeft + displayedWidth
                    val imageBottom = imageTop + displayedHeight

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val minRegionPx = with(density) { MIN_REGION_SIZE_DP.dp.toPx() }
                    val strokeWidthPx = with(density) { 20.dp.toPx() }

                    var markPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
                    var hasMark by remember { mutableStateOf(false) }

                    fun clamp(point: Offset) = Offset(
                        point.x.coerceIn(imageLeft, imageRight),
                        point.y.coerceIn(imageTop, imageBottom)
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(imageLeft, imageTop, imageRight, imageBottom) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        markPoints = listOf(clamp(start))
                                        hasMark = false
                                    },
                                    onDragEnd = {
                                        hasMark = markPoints.size > 1
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        markPoints = markPoints + clamp(change.position)
                                    }
                                )
                            }
                    ) {
                        if (markPoints.size > 1) {
                            val path = Path().apply {
                                moveTo(markPoints[0].x, markPoints[0].y)
                                markPoints.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFFFFEB3B).copy(alpha = 0.55f),
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        if (hasMark) {
                            val minX = markPoints.minOf { it.x }
                            val maxX = markPoints.maxOf { it.x }
                            val minY = markPoints.minOf { it.y }
                            val maxY = markPoints.maxOf { it.y }
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(minX, minY),
                                size = Size(maxX - minX, maxY - minY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Draw over the text you want to search, then tap Search",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp)
                    )

                    fun performSearch() {
                        if (markPoints.size < 2) {
                            coroutineScope.launch {
                                LavenderSnackbarController.pushEvent(
                                    LavenderSnackbarEvents.MessageEvent(
                                        message = "Draw over an area first",
                                        iconResId = R.drawable.error_2,
                                        duration = SnackbarDuration.Short
                                    )
                                )
                            }
                            return
                        }

                        val minX = markPoints.minOf { it.x }
                        val maxX = markPoints.maxOf { it.x }
                        val minY = markPoints.minOf { it.y }
                        val maxY = markPoints.maxOf { it.y }

                        if (maxX - minX < minRegionPx || maxY - minY < minRegionPx) {
                            coroutineScope.launch {
                                LavenderSnackbarController.pushEvent(
                                    LavenderSnackbarEvents.MessageEvent(
                                        message = "Draw over a larger area",
                                        iconResId = R.drawable.error_2,
                                        duration = SnackbarDuration.Short
                                    )
                                )
                            }
                            return
                        }

                        isProcessing = true
                        coroutineScope.launch {
                            val bx = ((minX - imageLeft) / scale).toInt().coerceIn(0, bmp.width)
                            val by = ((minY - imageTop) / scale).toInt().coerceIn(0, bmp.height)
                            val bw = ((maxX - minX) / scale).toInt().coerceIn(1, bmp.width - bx)
                            val bh = ((maxY - minY) / scale).toInt().coerceIn(1, bmp.height - by)

                            val text = withContext(Dispatchers.Default) {
                                try {
                                    val cropped = Bitmap.createBitmap(bmp, bx, by, bw, bh)
                                    EnhancedOcrExtractor.extractSelectableTextFromBitmap(cropped)
                                        ?.fullText?.trim().orEmpty()
                                } catch (e: Exception) {
                                    ""
                                }
                            }

                            isProcessing = false

                            if (text.isBlank()) {
                                LavenderSnackbarController.pushEvent(
                                    LavenderSnackbarEvents.MessageEvent(
                                        message = "No readable text found in that area",
                                        iconResId = R.drawable.error_2,
                                        duration = SnackbarDuration.Short
                                    )
                                )
                            } else {
                                onSearch(text)
                            }
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        FloatingBottomAppBar {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(1f)
                                    .padding(12.dp, 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                BottomAppBarItem(
                                    text = "Cancel",
                                    iconResId = R.drawable.close,
                                    cornerRadius = 32.dp,
                                    action = onDismiss
                                )
                                BottomAppBarItem(
                                    text = "Clear",
                                    iconResId = R.drawable.reset,
                                    cornerRadius = 32.dp,
                                    enabled = hasMark && !isProcessing,
                                    action = {
                                        markPoints = emptyList()
                                        hasMark = false
                                    }
                                )
                                BottomAppBarItem(
                                    text = "Search",
                                    iconResId = R.drawable.search,
                                    cornerRadius = 32.dp,
                                    enabled = !isProcessing,
                                    action = ::performSearch
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
