package com.swipeclean.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun InteractiveTutorialDialog(
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) } // 0: Swipe Left, 1: Swipe Right, 2: Tap to Zoom, 3: Full Overview
    var isZoomPreviewOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step Dots Indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..3) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == currentStep) 20.dp else 8.dp, 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == currentStep) Color(0xFF0A84FF)
                                        else if (i < currentStep) Color(0xFF34C759)
                                        else Color(0xFF333338)
                                    )
                            )
                        }
                    }

                    // Skip / Close Button
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (currentStep == 3) "Close" else "Skip Tutorial",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Step Content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (fadeIn(tween(250)) + scaleIn(initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 1.05f))
                    },
                    modifier = Modifier.weight(1f),
                    label = "tutorial_step"
                ) { step ->
                    when (step) {
                        0 -> TutorialSwipeLeftCard(
                            onCompleted = { currentStep = 1 }
                        )
                        1 -> TutorialSwipeRightCard(
                            onCompleted = { currentStep = 2 }
                        )
                        2 -> TutorialTapZoomCard(
                            onCompleted = { currentStep = 3 },
                            onOpenZoom = { isZoomPreviewOpen = true }
                        )
                        3 -> TutorialOverviewScreen(
                            onDone = onDismiss
                        )
                    }
                }

                // Bottom Step Controls
                if (currentStep < 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 0) {
                            TextButton(onClick = { currentStep-- }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back", color = Color(0xFF8E8E93), fontSize = 13.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        TextButton(onClick = { currentStep++ }) {
                            Text("Next Tip", color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Fullscreen Zoom Simulator in Step 2
            AnimatedVisibility(
                visible = isZoomPreviewOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                TutorialZoomPreview(
                    onDismiss = {
                        isZoomPreviewOpen = false
                        currentStep = 3
                    }
                )
            }
        }
    }
}

/**
 * Step 0: Interactive Practice Card for Swiping Left (Trash)
 */
@Composable
private fun TutorialSwipeLeftCard(
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val dragX = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    // Pulsing indicator guide
    val infiniteTransition = rememberInfiniteTransition(label = "left_hand_pulse")
    val handOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hand_pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 1 of 3",
            color = Color(0xFFFF453A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Swipe Left to Trash",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try dragging the card to the left! It moves photos safely to your 30-day Recycle Bin.",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Interactive Swiping Card Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val trashAlpha = (-dragX.value / (screenWidthPx * 0.15f)).coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF282836)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragX.value
                        rotationZ = (dragX.value / screenWidthPx) * 20f
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragX.snapTo(dragX.value + dragAmount.x)
                                }
                            },
                            onDragEnd = {
                                if (dragX.value < -screenWidthPx * 0.18f && !isDismissed) {
                                    isDismissed = true
                                    scope.launch {
                                        dragX.animateTo(-screenWidthPx * 1.5f, tween(200))
                                        onCompleted()
                                    }
                                } else {
                                    scope.launch {
                                        dragX.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                }
                            }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Mock Card Visual
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF251A1C), Color(0xFF131012))
                                )
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF3B1A1E),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Blurry Receipt / Old Meme",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "5.4 MB • Screenshots",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Animated Drag Left Hand Guide
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer { translationX = handOffset }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Flick Left 👈",
                                color = Color(0xFFFF453A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dynamic Red TRASH Stamp
                    if (trashAlpha > 0.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .graphicsLayer {
                                    rotationZ = 16f
                                    alpha = trashAlpha
                                }
                                .border(3.5.dp, Color(0xFFFF3B30), RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "TRASH",
                                color = Color(0xFFFF3B30),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 1: Interactive Practice Card for Swiping Right (Keep)
 */
@Composable
private fun TutorialSwipeRightCard(
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val dragX = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "right_hand_pulse")
    val handOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hand_pulse_right"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 2 of 3",
            color = Color(0xFF34C759),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Swipe Right to Keep",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Drag this memory to the right to keep it in your gallery. Kept photos won't bother you again!",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val keepAlpha = (dragX.value / (screenWidthPx * 0.15f)).coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF282836)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragX.value
                        rotationZ = (dragX.value / screenWidthPx) * 20f
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragX.snapTo(dragX.value + dragAmount.x)
                                }
                            },
                            onDragEnd = {
                                if (dragX.value > screenWidthPx * 0.18f && !isDismissed) {
                                    isDismissed = true
                                    scope.launch {
                                        dragX.animateTo(screenWidthPx * 1.5f, tween(200))
                                        onCompleted()
                                    }
                                } else {
                                    scope.launch {
                                        dragX.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                }
                            }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF14241A), Color(0xFF0F1A13))
                                )
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF183B22),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Beautiful Vacation Sunset",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "3.8 MB • Camera",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Animated Drag Right Hand Guide
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer { translationX = handOffset }
                        ) {
                            Text(
                                text = "Flick Right 👉",
                                color = Color(0xFF34C759),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Dynamic Green KEEP Stamp
                    if (keepAlpha > 0.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(24.dp)
                                .graphicsLayer {
                                    rotationZ = -16f
                                    alpha = keepAlpha
                                }
                                .border(3.5.dp, Color(0xFF34C759), RoundedCornerShape(12.dp))
                                .background(Color(0xFF34C759).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "KEEP",
                                color = Color(0xFF34C759),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 2: Interactive Practice Card for Tap to Zoom
 */
@Composable
private fun TutorialTapZoomCard(
    onCompleted: () -> Unit,
    onOpenZoom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 3 of 3",
            color = Color(0xFF0A84FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap Photo to Zoom & Inspect",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap on any card to examine high-res details. Pinch to zoom up to 5x or double tap!",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onOpenZoom,
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF16161E),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0A84FF).copy(alpha = 0.5f)),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF162030), Color(0xFF101622))
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1A3554),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Tap Anywhere to Test Zoom 🔍",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Double tap to zoom 2.5x • Pinch up to 5x",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A84FF).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0A84FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Try Tapping Here",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 3: Complete App Feature & Control Dock Overview
 */
@Composable
private fun TutorialOverviewScreen(
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1A2A1E),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "You're All Set to Clean!",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Here are your key controls inside every album:",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Key Tools Grid / List
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TutorialFeatureRow(
                icon = Icons.AutoMirrored.Filled.Undo,
                iconColor = Color(0xFFFF9F0A),
                iconBg = Color(0xFF2C2216),
                title = "Quick Undo (Bottom Dock)",
                description = "Swiped the wrong way? Tap Undo anytime to instantly bring the photo back."
            )

            TutorialFeatureRow(
                icon = Icons.Default.Delete,
                iconColor = Color(0xFFFF453A),
                iconBg = Color(0xFF30181A),
                title = "Recycle Bin (Bottom Dock & Home)",
                description = "Photos stay in your pending queue until you confirm moving them to your phone's 30-day Recycle Bin."
            )

            TutorialFeatureRow(
                icon = Icons.AutoMirrored.Filled.Sort,
                iconColor = Color(0xFF0A84FF),
                iconBg = Color(0xFF16253A),
                title = "Smart Sort (Top Bar)",
                description = "Sort by 'Largest First' to free up gigabytes, or by Oldest / Newest dates."
            )

            TutorialFeatureRow(
                icon = Icons.Default.RestartAlt,
                iconColor = Color(0xFF8E8E93),
                iconBg = Color(0xFF232328),
                title = "Start Over (Top Bar)",
                description = "Finished an album or want to review again? Tap the restart icon anytime."
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Big Primary "Start Cleaning" Button
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Let's Start Cleaning 🚀",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TutorialFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF16161E),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2C2C34)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Fullscreen Interactive Zoom Preview Simulator
 */
@Composable
private fun TutorialZoomPreview(
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
                    onTap = { onDismiss() }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2838),
                modifier = Modifier
                    .size(240.dp)
                    .border(2.dp, Color(0xFF0A84FF), RoundedCornerShape(20.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(70.dp)
                    )
                }
            }
        }

        // Top info bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xAA000000),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x40FFFFFF)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
        ) {
            Text(
                text = "Pinch to zoom • Double tap to 2.5x",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Bottom Dismiss Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A84FF),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .clickable { onDismiss() }
        ) {
            Text(
                text = "Tap anywhere to continue →",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

