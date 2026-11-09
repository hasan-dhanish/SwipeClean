package com.swipeclean.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import coil.imageLoader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SystemUpdate
import com.swipeclean.app.updater.DownloadState
import com.swipeclean.app.updater.UpdateInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swipeclean.app.data.FolderItem
import com.swipeclean.app.data.MediaItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeCleanAppRoot(
    viewModel: SwipeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val context = LocalContext.current

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onTrashSuccess()
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        hasPermission = granted
        if (granted) {
            viewModel.loadFolders()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadFolders()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF000000) // Apple Pure OLED Black
    ) {
        if (!hasPermission) {
            PermissionRequestScreen(
                onRequestPermission = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    }
                    permissionLauncher.launch(perms)
                }
            )
            return@Surface
        }

        when (val state = uiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0A84FF))
                }
            }
            is UiState.FolderList -> {
                AppleFolderGridScreen(
                    folders = state.folders,
                    pendingTrashItems = state.pendingTrashItems,
                    updateInfo = updateInfo,
                    onFolderClick = { folder -> viewModel.selectFolder(folder.name) },
                    onRefresh = {
                        viewModel.loadFolders()
                        viewModel.checkForUpdates(autoPrompt = false)
                    },
                    onTriggerUpdate = {
                        viewModel.openUpdateDialog()
                    },
                    onRequestTrash = {
                        viewModel.requestMoveTrash { request ->
                            trashLauncher.launch(request)
                        }
                    },
                    onRemoveFromTrash = { item ->
                        viewModel.removeFromTrash(item)
                    }
                )
            }
            is UiState.Swiping -> {
                SwipingDeckScreen(
                    state = state,
                    onSwipeLeft = { viewModel.swipeLeft() },
                    onSwipeRight = { viewModel.swipeRight() },
                    onUndo = { viewModel.undo() },
                    onBack = { viewModel.backToFolders() },
                    onResetFolder = { viewModel.resetFolderAndRestart(state.folderName) },
                    onRequestTrash = {
                        viewModel.requestMoveTrash { request ->
                            trashLauncher.launch(request)
                        }
                    },
                    onRemoveFromTrash = { item -> viewModel.removeFromTrash(item) },
                    onSortChange = { sort -> viewModel.setSortOrder(sort) }
                )
            }
        }

        // Over-The-Air Update Dialogs
        if (showUpdateDialog && updateInfo != null) {
            UpdateAvailableDialog(
                updateInfo = updateInfo!!,
                onConfirmUpdate = { viewModel.startUpdate(context) },
                onDismiss = { viewModel.dismissUpdate() }
            )
        }

        (downloadState as? DownloadState.Downloading)?.let { dState ->
            DownloadProgressDialog(
                state = dState,
                versionName = updateInfo?.versionName ?: "Update"
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1C1E),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38383A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2C2C2E),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "SwipeClean",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Apple Photos style clean-up.\nSwipe Left to trash, Swipe Right to keep.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF8E8E93)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        "Grant Gallery Access",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Authentic Apple Photos Albums Grid
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleFolderGridScreen(
    folders: List<FolderItem>,
    pendingTrashItems: List<MediaItem>,
    updateInfo: UpdateInfo? = null,
    onFolderClick: (FolderItem) -> Unit,
    onRefresh: () -> Unit,
    onTriggerUpdate: () -> Unit = {},
    onRequestTrash: () -> Unit = {},
    onRemoveFromTrash: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val screenshotFolder = folders.firstOrNull { it.name.contains("Screenshot", ignoreCase = true) }
    var showPendingTrashSheet by remember { mutableStateOf(false) }

    if (showPendingTrashSheet) {
        BackHandler {
            showPendingTrashSheet = false
        }
    }

    val trashedBytes = remember(pendingTrashItems) {
        pendingTrashItems.sumOf { it.size }
    }
    val formattedTrashSize = remember(trashedBytes) {
        when {
            trashedBytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", trashedBytes / (1024f * 1024f * 1024f))
            trashedBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", trashedBytes / (1024f * 1024f))
            trashedBytes >= 1024 -> String.format(Locale.US, "%.1f KB", trashedBytes / 1024f)
            else -> "$trashedBytes B"
        }
    }

    Scaffold(
        containerColor = Color(0xFF000000),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000000)),
                title = {},
                actions = {
                    if (updateInfo != null) {
                        Surface(
                            onClick = onTriggerUpdate,
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF0A84FF),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Homepage Recycle Bin Button (Top Bar Action)
                    if (pendingTrashItems.isNotEmpty()) {
                        Surface(
                            onClick = { showPendingTrashSheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF2E1518),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Recycle Bin",
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${pendingTrashItems.size}",
                                    color = Color(0xFFFF3B30),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onRefresh,
                        shape = CircleShape,
                        color = Color(0xFF1C1C1E),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Apple Photos Iconic Large Title Header
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = "Albums",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    if (pendingTrashItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            onClick = { showPendingTrashSheet = true },
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${pendingTrashItems.size} items in pending trash • $formattedTrashSize",
                                    color = Color(0xFFFF453A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color(0xFFFF453A),
                                    modifier = Modifier
                                        .size(12.dp)
                                        .graphicsLayer { rotationZ = 180f }
                                )
                            }
                        }
                    }
                }
            }

            // Apple "Featured / Spotlight" Card for Screenshots (21 GB)
            if (screenshotFolder != null) {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        onClick = { onFolderClick(screenshotFolder) },
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1C1C1E),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38383A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (screenshotFolder.thumbnailUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(screenshotFolder.thumbnailUri)
                                        .size(800, 350)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Dark Apple gradient scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
                                        )
                                    )
                            )

                            // Content inside featured card
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFBF5AF2).copy(alpha = 0.3f),
                                        modifier = Modifier.border(1.dp, Color(0xFFBF5AF2), RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = "FEATURED",
                                            color = Color(0xFFBF5AF2),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Screenshots",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${screenshotFolder.count} items • ${screenshotFolder.formattedSize}",
                                            color = Color(0xFFEBEBF5).copy(alpha = 0.7f),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0A84FF)
                                    ) {
                                        Text(
                                            text = "Clean",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Header: My Albums
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "My Albums",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 2-Column Apple Square Album Tiles
            items(folders, key = { it.name }) { folder ->
                AppleAlbumTile(
                    folder = folder,
                    onClick = { onFolderClick(folder) }
                )
            }

            // Apple Utilities Section: Pending Recycle Bin
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Utilities",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
                )
            }

            item(span = { GridItemSpan(2) }) {
                Surface(
                    onClick = { showPendingTrashSheet = true },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1C1C1E),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38383A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2E1518),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pending Recycle Bin",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (pendingTrashItems.isNotEmpty()) {
                                    "${pendingTrashItems.size} items waiting to be moved • $formattedTrashSize"
                                } else {
                                    "0 items • Everything clean"
                                },
                                color = if (pendingTrashItems.isNotEmpty()) Color(0xFFFF453A) else Color(0xFF8E8E93),
                                fontSize = 13.sp
                            )
                        }

                        if (pendingTrashItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2E1518),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Review",
                                    color = Color(0xFFFF3B30),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showPendingTrashSheet) {
        PendingTrashBottomSheet(
            items = pendingTrashItems,
            formattedTrashSize = formattedTrashSize,
            onDismiss = { showPendingTrashSheet = false },
            onRequestTrash = {
                showPendingTrashSheet = false
                onRequestTrash()
            },
            onRemoveFromTrash = onRemoveFromTrash
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipingDeckScreen(
    state: UiState.Swiping,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onResetFolder: () -> Unit,
    onRequestTrash: () -> Unit,
    onRemoveFromTrash: (MediaItem) -> Unit,
    onSortChange: (PhotoSort) -> Unit
) {
    val context = LocalContext.current
    var showTrashSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var inspectingItem by remember { mutableStateOf<MediaItem?>(null) }

    var triggerLeft by remember { mutableStateOf(false) }
    var triggerRight by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    // Intercept phone system back gesture (OxygenOS / Android Edge Swipe)
    BackHandler {
        when {
            inspectingItem != null -> inspectingItem = null
            showSortSheet -> showSortSheet = false
            showTrashSheet -> showTrashSheet = false
            showResetDialog -> showResetDialog = false
            else -> onBack()
        }
    }

    val trashedBytes = remember(state.trashedItems) {
        state.trashedItems.sumOf { it.size }
    }
    val formattedTrashSize = remember(trashedBytes) {
        when {
            trashedBytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", trashedBytes / (1024f * 1024f * 1024f))
            trashedBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", trashedBytes / (1024f * 1024f))
            trashedBytes >= 1024 -> String.format(Locale.US, "%.1f KB", trashedBytes / 1024f)
            else -> "$trashedBytes B"
        }
    }

    val currentCard = state.remainingItems.firstOrNull()
    val nextCard = state.remainingItems.getOrNull(1)

    // Preload next 2 upcoming images into RAM cache for zero blank frames
    val imageLoader = remember { context.imageLoader }
    LaunchedEffect(nextCard?.id) {
        nextCard?.let { item ->
            val request = ImageRequest.Builder(context)
                .data(item.uri)
                .size(1080, 1920)
                .memoryCacheKey(item.uri.toString())
                .diskCacheKey(item.uri.toString())
                .crossfade(false)
                .allowHardware(true)
                .build()
            imageLoader.enqueue(request)
        }
        state.remainingItems.getOrNull(2)?.let { item3 ->
            val request = ImageRequest.Builder(context)
                .data(item3.uri)
                .size(1080, 1920)
                .memoryCacheKey(item3.uri.toString())
                .diskCacheKey(item3.uri.toString())
                .crossfade(false)
                .allowHardware(true)
                .build()
            imageLoader.enqueue(request)
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text("Start Over from Beginning?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will let you re-swipe all previously kept photos in ${state.folderName}.\n\nYour pending Recycle Bin selections (${state.trashedItems.size} items) will remain safe.",
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetFolder()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                ) {
                    Text("Yes, Start Over", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.14f

    var activeDragOffset by remember { mutableFloatStateOf(0f) }

    val targetLeftGlow = if (activeDragOffset < 0f) {
        ((-activeDragOffset / threshold) * 0.95f).coerceIn(0f, 1f)
    } else 0f

    val targetRightGlow = if (activeDragOffset > 0f) {
        ((activeDragOffset / threshold) * 0.95f).coerceIn(0f, 1f)
    } else 0f

    val leftGlowAlpha by animateFloatAsState(
        targetValue = targetLeftGlow,
        animationSpec = if (targetLeftGlow > 0f) {
            tween(durationMillis = 80, easing = LinearEasing)
        } else {
            tween(durationMillis = 500, easing = LinearOutSlowInEasing)
        },
        label = "LeftGlowAlpha"
    )

    val rightGlowAlpha by animateFloatAsState(
        targetValue = targetRightGlow,
        animationSpec = if (targetRightGlow > 0f) {
            tween(durationMillis = 80, easing = LinearEasing)
        } else {
            tween(durationMillis = 500, easing = LinearOutSlowInEasing)
        },
        label = "RightGlowAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Color(0xFF000000),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000000)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0A84FF)
                        )
                    }
                },
                title = {
                    Text(
                        text = state.folderName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    // Sort Button (Just the Icon)
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Restart Button (Just the Icon)
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Start Over",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = {
                    if (state.totalCount > 0) {
                        ((state.totalCount - state.remainingItems.size).toFloat() / state.totalCount).coerceIn(0f, 1f)
                    } else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(CircleShape),
                color = Color(0xFF0A84FF),
                trackColor = Color(0xFF1C1C1E)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val density = LocalDensity.current
            val stepOffsetYPx = with(density) { 16.dp.toPx() }
            val deckProgress = (kotlin.math.abs(activeDragOffset) / (screenWidthPx * 0.40f)).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (currentCard != null) {
                    val thirdCard = state.remainingItems.getOrNull(2)
                    if (thirdCard != null) {
                        BackgroundCard(
                            item = thirdCard,
                            scale = 0.86f + (0.93f - 0.86f) * deckProgress,
                            offsetY = (stepOffsetYPx * 2f) - (stepOffsetYPx * deckProgress),
                            scrimAlpha = 0.45f - 0.20f * deckProgress,
                            metadataAlpha = 0f,
                            shadowElevation = 4f + 6f * deckProgress,
                            modifier = Modifier.graphicsLayer { alpha = 0.35f + 0.65f * deckProgress }
                        )
                    }
                    if (nextCard != null) {
                        BackgroundCard(
                            item = nextCard,
                            scale = 0.93f + (1.0f - 0.93f) * deckProgress,
                            offsetY = stepOffsetYPx * (1f - deckProgress),
                            scrimAlpha = 0.25f * (1f - deckProgress),
                            metadataAlpha = 0.5f + 0.5f * deckProgress,
                            shadowElevation = 10f + 6f * deckProgress
                        )
                    }
                    key(currentCard.id) {
                        SwipeCard(
                            item = currentCard,
                            onSwipedLeft = {
                                isBusy = false
                                triggerLeft = false
                                onSwipeLeft()
                            },
                            onSwipedRight = {
                                isBusy = false
                                triggerRight = false
                                onSwipeRight()
                            },
                            triggerSwipeLeft = triggerLeft,
                            triggerSwipeRight = triggerRight,
                            onTap = {
                                inspectingItem = currentCard
                            },
                            onDragOffset = { offset ->
                                activeDragOffset = offset
                            }
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xFF1C1C1E),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38383A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "All Caught Up!",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You have reviewed all photos in ${state.folderName}.\n${state.keptCount} kept • ${state.trashedItems.size} pending in Recycle Bin.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF8E8E93)
                            )

                            if (state.trashedItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    onClick = onRequestTrash,
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2E1518),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFFF3B30),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Move ${state.trashedItems.size} to Trash ($formattedTrashSize)",
                                            color = Color(0xFFFF3B30),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(color = Color(0xFF2C2C2E))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Want to review from the beginning?",
                                color = Color(0xFF8E8E93),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Again from Beginning", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = onBack) {
                                Text("Back to Albums", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Bottom Area: Undo button (always visible) + Trash button + discreet progress counts
            if (currentCard != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    val canUndo = state.history.isNotEmpty() && !isBusy
                    val hasTrash = state.trashedItems.isNotEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Undo button: Always present, activates when there is something to undo
                        Surface(
                            onClick = onUndo,
                            enabled = canUndo,
                            shape = RoundedCornerShape(24.dp),
                            color = if (canUndo) Color(0xDD1C1C1E) else Color(0x5516161A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (canUndo) Color(0x33FFFFFF) else Color(0x18FFFFFF)
                            ),
                            shadowElevation = if (canUndo) 6.dp else 0.dp,
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) Color(0xFFFF9F0A) else Color(0xFF555562),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Undo",
                                    color = if (canUndo) Color.White else Color(0xFF555562),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Trash button: Next to Undo button
                        Surface(
                            onClick = { showTrashSheet = true },
                            shape = RoundedCornerShape(24.dp),
                            color = if (hasTrash) Color(0xFF2E1518) else Color(0x5516161A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (hasTrash) Color(0xFFFF3B30).copy(alpha = 0.5f) else Color(0x18FFFFFF)
                            ),
                            shadowElevation = if (hasTrash) 6.dp else 0.dp,
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Recycle Bin",
                                    tint = if (hasTrash) Color(0xFFFF3B30) else Color(0xFF555562),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasTrash) "Trash (${state.trashedItems.size})" else "Trash",
                                    color = if (hasTrash) Color(0xFFFF3B30) else Color(0xFF555562),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Discreet bottom counts (Unreviewed & Kept)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${state.remainingItems.size} unreviewed",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "  •  ",
                            color = Color(0xFF48484A),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${state.keptCount} kept",
                            color = Color(0xFF34C759),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

        // Left Edge Soft Red Light Glow (Dynamic on Swipe Left with organic decay)
        if (leftGlowAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(180.dp)
                    .graphicsLayer { alpha = leftGlowAlpha }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF3B30).copy(alpha = 0.50f),
                                Color(0xFFFF3B30).copy(alpha = 0.18f),
                                Color(0xFFFF3B30).copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Right Edge Soft Green Light Glow (Dynamic on Swipe Right with organic decay)
        if (rightGlowAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(180.dp)
                    .graphicsLayer { alpha = rightGlowAlpha }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF34C759).copy(alpha = 0.04f),
                                Color(0xFF34C759).copy(alpha = 0.18f),
                                Color(0xFF34C759).copy(alpha = 0.50f)
                            )
                        )
                    )
            )
        }
    }

    // Fullscreen Tap-to-Zoom Mode with fluid Apple spring expansion & collapse
    AnimatedVisibility(
        visible = inspectingItem != null,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.90f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(tween(160)) + scaleOut(
            targetScale = 0.90f,
            animationSpec = tween(160, easing = FastOutLinearInEasing)
        )
    ) {
        inspectingItem?.let { item ->
            ZoomableImageViewer(
                item = item,
                onDismiss = { inspectingItem = null }
            )
        }
    }

    if (showSortSheet) {
        SortOrderBottomSheet(
            currentSort = state.currentSort,
            onSelectSort = { sort ->
                showSortSheet = false
                onSortChange(sort)
            },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showTrashSheet) {
        PendingTrashBottomSheet(
            items = state.trashedItems,
            formattedTrashSize = formattedTrashSize,
            onDismiss = { showTrashSheet = false },
            onRequestTrash = {
                showTrashSheet = false
                onRequestTrash()
            },
            onRemoveFromTrash = onRemoveFromTrash
        )
    }
}

/**
 * Shared Authentic Apple Bottom Sheet for Reviewing & Confirming Pending Recycle Bin
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTrashBottomSheet(
    items: List<MediaItem>,
    formattedTrashSize: String,
    onDismiss: () -> Unit,
    onRequestTrash: () -> Unit,
    onRemoveFromTrash: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pending Recycle Bin",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${items.size} items • $formattedTrashSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }

                if (items.isNotEmpty()) {
                    Surface(
                        onClick = onRequestTrash,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF3B30),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Move to Trash",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pending photos in Recycle Bin", color = Color(0xFF8E8E93), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2C2C2E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.uri)
                                        .size(140, 140)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFF3B30)
                                    )
                                }
                                TextButton(
                                    onClick = { onRemoveFromTrash(item) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Restore", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Authentic Apple Bottom Sheet for Selecting Deck Photo Sorting Order
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOrderBottomSheet(
    currentSort: PhotoSort,
    onSelectSort: (PhotoSort) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Sort Photos",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose order of images in this deck",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            PhotoSort.values().forEach { sortOption ->
                val isSelected = currentSort == sortOption
                Surface(
                    onClick = { onSelectSort(sortOption) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sortOption.label,
                                color = if (isSelected) Color(0xFF0A84FF) else Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sortOption.subtitle,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

