package com.swipeclean.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swipeclean.app.data.FolderItem
import com.swipeclean.app.data.MediaItem
import kotlinx.coroutines.launch

/**
 * Apple Photos style square album tile
 */
@Composable
fun AppleAlbumTile(
    folder: FolderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Apple Squircle 1:1 Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(18.dp))
        ) {
            if (folder.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(folder.thumbnailUri)
                        .size(450, 450)
                        .crossfade(true)
                        .build(),
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }

            // Discreet Apple-style translucent size tag
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xB3000000),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = folder.formattedSize,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Title & Count beneath thumbnail (Standard Apple Photos UX)
        Text(
            text = folder.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${folder.count}",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun SwipeCard(
    item: MediaItem,
    onSwipedLeft: () -> Unit,
    onSwipedRight: () -> Unit,
    triggerSwipeLeft: Boolean,
    triggerSwipeRight: Boolean,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dragX = remember(item.id) { Animatable(0f) }
    val dragY = remember(item.id) { Animatable(0f) }
    var isDismissed by remember(item.id) { mutableStateOf(false) }

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    // Effortless thumb flick threshold
    val threshold = screenWidthPx * 0.14f

    LaunchedEffect(triggerSwipeLeft) {
        if (triggerSwipeLeft && !isDismissed) {
            isDismissed = true
            dragX.animateTo(-screenWidthPx * 1.5f, tween(240, easing = FastOutSlowInEasing))
            onSwipedLeft()
        }
    }

    LaunchedEffect(triggerSwipeRight) {
        if (triggerSwipeRight && !isDismissed) {
            isDismissed = true
            dragX.animateTo(screenWidthPx * 1.5f, tween(240, easing = FastOutSlowInEasing))
            onSwipedRight()
        }
    }

    val progress = dragX.value / threshold
    val keepAlpha = (progress * 0.8f).coerceIn(0f, 1f)
    val trashAlpha = (-progress * 0.8f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = dragX.value
                translationY = dragY.value
                rotationZ = (dragX.value / 24f).coerceIn(-25f, 25f)
            }
            .pointerInput(item.id) {
                if (isDismissed) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var totalDragX = 0f
                    var totalDragY = 0f
                    val touchSlop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!isDrag) {
                                onTap()
                            } else {
                                scope.launch {
                                    if (dragX.value > threshold) {
                                        isDismissed = true
                                        dragX.animateTo(screenWidthPx * 1.5f, tween(240, easing = FastOutSlowInEasing))
                                        onSwipedRight()
                                    } else if (dragX.value < -threshold) {
                                        isDismissed = true
                                        dragX.animateTo(-screenWidthPx * 1.5f, tween(240, easing = FastOutSlowInEasing))
                                        onSwipedLeft()
                                    } else {
                                        launch { dragX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                        launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            }
                            break
                        }

                        val deltaX = change.position.x - change.previousPosition.x
                        val deltaY = change.position.y - change.previousPosition.y
                        totalDragX += deltaX
                        totalDragY += deltaY

                        if (!isDrag && (kotlin.math.hypot(totalDragX, totalDragY) > touchSlop)) {
                            isDrag = true
                        }

                        if (isDrag && !isDismissed) {
                            change.consume()
                            scope.launch {
                                dragX.snapTo(dragX.value + deltaX)
                                dragY.snapTo(dragY.value + deltaY * 0.35f)
                            }
                        }
                    }
                }
            }
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF16161E),
            modifier = Modifier
                .fillMaxSize()
                .shadow(16.dp, RoundedCornerShape(26.dp))
                .border(1.5.dp, Color(0xFF282836), RoundedCornerShape(26.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(1080, 1920)
                        .memoryCacheKey(item.uri.toString())
                        .diskCacheKey(item.uri.toString())
                        .crossfade(false)
                        .allowHardware(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )

                // KEEP Stamp
                if (keepAlpha > 0.02f) {
                    Box(
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                rotationZ = -18f
                                alpha = keepAlpha
                            }
                            .border(4.dp, Color(0xFF34C759), RoundedCornerShape(12.dp))
                            .background(Color(0xFF34C759).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "KEEP",
                            color = Color(0xFF34C759),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // TRASH Stamp
                if (trashAlpha > 0.02f) {
                    Box(
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                rotationZ = 18f
                                alpha = trashAlpha
                            }
                            .border(4.dp, Color(0xFFFF3B30), RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF3B30).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "TRASH",
                            color = Color(0xFFFF3B30),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Bottom Metadata Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xDD000000), Color(0xF0000000))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        if (item.formattedDateTime.isNotBlank()) {
                            Text(
                                text = item.formattedDateTime,
                                color = Color(0xFFEBEBF5).copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF3B30).copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = item.formattedSize,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.bucketName,
                                color = Color(0xFFB0B0C0),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundCard(
    item: MediaItem,
    scale: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF16161E),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY
            }
            .shadow(8.dp, RoundedCornerShape(26.dp))
            .border(1.dp, Color(0xFF282836), RoundedCornerShape(26.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .size(1080, 1920)
                .memoryCacheKey(item.uri.toString())
                .diskCacheKey(item.uri.toString())
                .crossfade(false)
                .allowHardware(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}

@Composable
fun TinderActionButtons(
    onTrash: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    isUndoEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red Trash (NOPE)
        Surface(
            onClick = onTrash,
            shape = CircleShape,
            color = Color(0xFF2D1619),
            modifier = Modifier
                .size(68.dp)
                .shadow(8.dp, CircleShape)
                .border(2.dp, Color(0xFFFF3B30).copy(alpha = 0.6f), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Trash",
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Yellow Undo
        Surface(
            onClick = onUndo,
            enabled = isUndoEnabled,
            shape = CircleShape,
            color = if (isUndoEnabled) Color(0xFF292416) else Color(0xFF1C1C24),
            modifier = Modifier
                .size(52.dp)
                .border(
                    1.5.dp,
                    if (isUndoEnabled) Color(0xFFFFB300).copy(alpha = 0.7f) else Color(0xFF333342),
                    CircleShape
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (isUndoEnabled) Color(0xFFFFB300) else Color(0xFF555566),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Green Keep (LIKE)
        Surface(
            onClick = onKeep,
            shape = CircleShape,
            color = Color(0xFF152A1C),
            modifier = Modifier
                .size(68.dp)
                .shadow(8.dp, CircleShape)
                .border(2.dp, Color(0xFF34C759).copy(alpha = 0.6f), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Keep",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

/**
 * Fullscreen Zoomable Image Viewer (Apple Photos Style)
 * Allows pinch-to-zoom, pan when zoomed, double tap to zoom in/out,
 * and single tap to exit back to swipe mode.
 */
@Composable
fun ZoomableImageViewer(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = {
                        onDismiss()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (scale > 1f) {
                        val maxOffset = 600f * (scale - 1f)
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                            y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Top Apple Info Bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xAA000000),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x40FFFFFF)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.formattedDateTime.isNotBlank()) {
                    Text(
                        text = item.formattedDateTime,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = item.formattedSize,
                    color = Color(0xFF0A84FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Tap Hint
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0x88000000),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Pinch to zoom • Tap anywhere to return",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

