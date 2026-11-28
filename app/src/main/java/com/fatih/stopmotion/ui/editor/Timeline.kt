package com.fatih.stopmotion.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.ui.theme.FatihColors
import java.io.File
import kotlin.math.roundToInt

private val ItemSpacing = 4.dp

/**
 * Timeline horizontal. Hanya item yang terlihat yang dibuat (LazyRow) dan thumbnail
 * dimuat dengan ukuran kecil, sehingga tetap ringan untuk ratusan hingga ribuan frame.
 * Tekan lama lalu geser sebuah frame untuk memindahkannya (drag and drop).
 */
@Composable
fun Timeline(
    frames: List<FrameEntity>,
    currentIndex: Int,
    selected: Set<Long>,
    itemWidth: Dp,
    listState: LazyListState,
    onTap: (Int) -> Unit,
    onMove: (frameId: Long, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing)
    ) {
        itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
            TimelineItem(
                frame = frame,
                index = index,
                lastIndex = frames.lastIndex,
                itemWidth = itemWidth,
                isCurrent = index == currentIndex,
                isSelected = frame.id in selected,
                onTap = { onTap(index) },
                onDropMove = { toIndex -> onMove(frame.id, toIndex) }
            )
        }
    }
}

@Composable
private fun TimelineItem(
    frame: FrameEntity,
    index: Int,
    lastIndex: Int,
    itemWidth: Dp,
    isCurrent: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDropMove: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val stridePx by rememberUpdatedState(with(density) { (itemWidth + ItemSpacing).toPx() })
    val indexNow by rememberUpdatedState(index)
    val lastNow by rememberUpdatedState(lastIndex)
    val dropNow by rememberUpdatedState(onDropMove)

    val widthPx = with(density) { itemWidth.roundToPx() }
    val request = remember(frame.thumbPath, widthPx) {
        ImageRequest.Builder(context)
            .data(File(frame.thumbPath))
            .size(widthPx, widthPx * 4 / 3)
            .build()
    }

    Box(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragX
                val s = if (dragging) 1.1f else 1f
                scaleX = s
                scaleY = s
            }
            .size(itemWidth, itemWidth * 4f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(FatihColors.SurfaceHigh)
            .pointerInput(frame.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                    },
                    onDragEnd = {
                        val steps = (dragX / stridePx).roundToInt()
                        dragging = false
                        dragX = 0f
                        if (steps != 0) dropNow((indexNow + steps).coerceIn(0, lastNow))
                    },
                    onDragCancel = {
                        dragging = false
                        dragX = 0f
                    }
                )
            }
            .clickable(onClick = onTap)
            .border(
                if (isCurrent) 3.dp else 0.dp,
                if (isCurrent) FatihColors.ShutterRed else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
    ) {
        AsyncImage(
            model = request,
            contentDescription = "Frame ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(itemWidth, itemWidth * 4f / 3f)
        )
        if (isSelected) {
            Box(Modifier.size(itemWidth, itemWidth * 4f / 3f).background(FatihColors.Blue.copy(alpha = 0.35f)))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Dipilih",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(16.dp)
                    .background(FatihColors.Blue, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            )
        }
        Text(
            "${index + 1}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topEnd = 6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        if (frame.holdCount > 1) {
            Text(
                "x${frame.holdCount}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = FatihColors.Blue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(bottomStart = 6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
