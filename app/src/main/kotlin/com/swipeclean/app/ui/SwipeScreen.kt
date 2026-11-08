package com.swipeclean.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
                    pendingTrashCount = state.totalPendingTrashCount,
                    updateInfo = updateInfo,
                    onFolderClick = { folder -> viewModel.selectFolder(folder.name) },
                    onRefresh = {
                        viewModel.loadFolders()
                        viewModel.checkForUpdates(autoPrompt = false)
                    },
                    onTriggerUpdate = {
                        viewModel.openUpdateDialog()
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
                    onRemoveFromTrash = { item -> viewModel.removeFromTrash(item) }
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
    pendingTrashCount: Int,
    updateInfo: UpdateInfo? = null,
    onFolderClick: (FolderItem) -> Unit,
    onRefresh: () -> Unit,
    onTriggerUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val screenshotFolder = folders.firstOrNull { it.name.contains("Screenshot", ignoreCase = true) }

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
                    if (pendingTrashCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$pendingTrashCount items saved in pending trash",
                            color = Color(0xFFFF453A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
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

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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
    onRemoveFromTrash: (MediaItem) -> Unit
) {
    var showTrashSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    var triggerLeft by remember { mutableStateOf(false) }
    var triggerRight by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            text = state.folderName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.remainingItems.size} unreviewed • ${state.keptCount} kept",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8E93)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Start Over",
                            tint = Color(0xFF8E8E93)
                        )
                    }

                    if (state.trashedItems.isNotEmpty()) {
                        Surface(
                            onClick = { showTrashSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF261518),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.4f)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${state.trashedItems.size}",
                                    color = Color(0xFFFF3B30),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formattedTrashSize,
                                    color = Color(0xFF8E8E93),
                                    fontSize = 11.sp
                                )
                            }
                        }
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
                    .height(3.dp)
                    .clip(CircleShape),
                color = Color(0xFF0A84FF),
                trackColor = Color(0xFF1C1C1E)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (currentCard != null) {
                    if (nextCard != null) {
                        BackgroundCard(
                            item = nextCard,
                            scale = 0.94f,
                            offsetY = 14f
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
                            triggerSwipeRight = triggerRight
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

            if (currentCard != null) {
                TinderActionButtons(
                    onTrash = {
                        if (!isBusy) {
                            isBusy = true
                            triggerLeft = true
                        }
                    },
                    onUndo = {
                        if (!isBusy) {
                            onUndo()
                        }
                    },
                    onKeep = {
                        if (!isBusy) {
                            isBusy = true
                            triggerRight = true
                        }
                    },
                    isUndoEnabled = state.history.isNotEmpty() && !isBusy
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTrashSheet) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { showTrashSheet = false },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
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
                            text = "${state.trashedItems.size} items • $formattedTrashSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8E93)
                        )
                    }

                    Surface(
                        onClick = {
                            showTrashSheet = false
                            onRequestTrash()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF3B30),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "Move to Trash",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.trashedItems, key = { it.id }) { item ->
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
                                        .size(120, 120)
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
        }
    }
}
