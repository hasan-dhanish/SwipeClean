package com.swipeclean.app.ui

import android.content.Context
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipeclean.app.data.FolderItem
import com.swipeclean.app.data.MediaItem
import com.swipeclean.app.data.MediaRepository
import com.swipeclean.app.data.TrashStorage
import com.swipeclean.app.updater.AppUpdateManager
import com.swipeclean.app.updater.DownloadState
import com.swipeclean.app.updater.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PhotoSort(val label: String, val subtitle: String) {
    SIZE_DESC("Largest First", "Free up maximum storage space"),
    DATE_DESC("Newest First", "Most recent photos & screenshots"),
    DATE_ASC("Oldest First", "Clean older memories from years ago"),
    SIZE_ASC("Smallest First", "Clear small files, memes & icons"),
    NAME_ASC("Name (A to Z)", "Alphabetical order by file name")
}

sealed interface UiState {
    object Loading : UiState
    data class FolderList(
        val folders: List<FolderItem>,
        val totalPendingTrashCount: Int = 0,
        val pendingTrashItems: List<MediaItem> = emptyList(),
        val systemTrashCount: Int = 0,
        val systemTrashSizeBytes: Long = 0L,
        val systemTrashThumbnailUri: android.net.Uri? = null
    ) : UiState
    data class Swiping(
        val folderName: String,
        val totalCount: Int,
        val remainingItems: List<MediaItem>,
        val trashedItems: List<MediaItem>,
        val keptCount: Int,
        val allFolderItemIds: List<Long>,
        val history: List<SwipeAction>,
        val isAllReviewedInitially: Boolean = false,
        val currentSort: PhotoSort = PhotoSort.DATE_DESC
    ) : UiState
    data class RecycleBin(
        val items: List<MediaItem>,
        val systemTrashedIds: Set<Long> = emptySet(),
        val pendingTrashedIds: Set<Long> = emptySet(),
        val selectedIds: Set<Long> = emptySet(),
        val isSelecting: Boolean = false
    ) : UiState
}

data class SwipeAction(
    val item: MediaItem,
    val wasTrashed: Boolean
)

class SwipeViewModel(
    private val repository: MediaRepository,
    private val trashStorage: TrashStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow<Boolean>(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        checkForUpdates(autoPrompt = true)
    }

    fun checkForUpdates(autoPrompt: Boolean = false) {
        viewModelScope.launch {
            val info = AppUpdateManager.checkForUpdate()
            if (info != null && info.isUpdateAvailable) {
                _updateInfo.value = info
                val lastPrompted = trashStorage.getLastPromptedVersion()
                if (autoPrompt && lastPrompted != info.versionName) {
                    _showUpdateDialog.value = true
                } else if (!autoPrompt) {
                    _showUpdateDialog.value = true
                }
            } else {
                _updateInfo.value = null
                _showUpdateDialog.value = false
            }
        }
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
    }

    fun dismissUpdate() {
        _showUpdateDialog.value = false
        val info = _updateInfo.value
        if (info != null) {
            trashStorage.setLastPromptedVersion(info.versionName)
        }
    }

    fun startUpdate(context: Context) {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            AppUpdateManager.downloadApk(context, info.apkUrl).collect { state ->
                _downloadState.value = state
                if (state is DownloadState.ReadyToInstall) {
                    if (AppUpdateManager.canInstallApk(context)) {
                        AppUpdateManager.launchApkInstall(context, state.apkFile)
                    } else {
                        AppUpdateManager.openInstallPermissionSettings(context)
                    }
                }
            }
        }
    }

    fun dismissDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val folders = repository.getFolders()
            val pendingTrashIds = trashStorage.getTrashedIds()
            val pendingTrashItems = repository.getMediaByIds(pendingTrashIds.toList())
            val systemTrashed = repository.getTrashedMedia()

            _uiState.value = UiState.FolderList(
                folders = folders,
                totalPendingTrashCount = pendingTrashItems.size,
                pendingTrashItems = pendingTrashItems,
                systemTrashCount = systemTrashed.size,
                systemTrashSizeBytes = systemTrashed.sumOf { it.size },
                systemTrashThumbnailUri = systemTrashed.firstOrNull()?.uri ?: pendingTrashItems.firstOrNull()?.uri
            )
        }
    }

    fun selectFolder(folderName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val allFolderItems = repository.getMediaInFolder(folderName)

            val trashedIds = trashStorage.getTrashedIds()
            val keptIds = trashStorage.getKeptIds()

            // Load all persisted trashed items
            val existingTrashedItems = repository.getMediaByIds(trashedIds.toList())

            // Filter out items already swiped right (kept) or swiped left (trashed)
            val remainingItems = allFolderItems.filterNot { item ->
                trashedIds.contains(item.id) || keptIds.contains(item.id)
            }

            val folderKeptCount = allFolderItems.count { keptIds.contains(it.id) }
            val isAllReviewed = remainingItems.isEmpty() && allFolderItems.isNotEmpty()

            val savedSortName = trashStorage.getSavedSortOrder()
            val currentSort = try { PhotoSort.valueOf(savedSortName) } catch (_: Exception) { PhotoSort.DATE_DESC }
            val sortedRemaining = sortItems(remainingItems, currentSort)

            _uiState.value = UiState.Swiping(
                folderName = folderName,
                totalCount = allFolderItems.size,
                remainingItems = sortedRemaining,
                trashedItems = existingTrashedItems,
                keptCount = folderKeptCount,
                allFolderItemIds = allFolderItems.map { it.id },
                history = emptyList(),
                isAllReviewedInitially = isAllReviewed,
                currentSort = currentSort
            )
        }
    }

    fun setSortOrder(sort: PhotoSort) {
        val state = _uiState.value as? UiState.Swiping ?: return
        trashStorage.setSavedSortOrder(sort.name)
        val sortedRemaining = sortItems(state.remainingItems, sort)
        _uiState.update {
            state.copy(
                remainingItems = sortedRemaining,
                currentSort = sort
            )
        }
    }

    private fun sortItems(items: List<MediaItem>, sort: PhotoSort): List<MediaItem> {
        return when (sort) {
            PhotoSort.SIZE_DESC -> items.sortedWith(compareByDescending<MediaItem> { it.size }.thenByDescending { it.dateTaken })
            PhotoSort.DATE_DESC -> items.sortedByDescending { it.dateTaken }
            PhotoSort.DATE_ASC -> items.sortedBy { it.dateTaken }
            PhotoSort.SIZE_ASC -> items.sortedWith(compareBy<MediaItem> { it.size }.thenByDescending { it.dateTaken })
            PhotoSort.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        }
    }

    fun resetFolderAndRestart(folderName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val allFolderItems = repository.getMediaInFolder(folderName)
            // Clear kept IDs for this folder only, keeping trash selections 100% safe!
            trashStorage.resetKeptForFolder(allFolderItems.map { it.id })
            selectFolder(folderName)
        }
    }

    fun swipeRight() { // Keep (DO NOT SHOW AGAIN)
        val state = _uiState.value as? UiState.Swiping ?: return
        val currentItem = state.remainingItems.firstOrNull() ?: return

        // Persist immediately to disk synchronously!
        trashStorage.addKeptId(currentItem.id)

        _uiState.update {
            state.copy(
                remainingItems = state.remainingItems.drop(1),
                keptCount = state.keptCount + 1,
                history = state.history + SwipeAction(currentItem, wasTrashed = false)
            )
        }
    }

    fun swipeLeft() { // Trash (REMEMBER PENDING TRASH)
        val state = _uiState.value as? UiState.Swiping ?: return
        val currentItem = state.remainingItems.firstOrNull() ?: return

        // Persist immediately to disk synchronously!
        trashStorage.addTrashedId(currentItem.id)

        _uiState.update {
            state.copy(
                remainingItems = state.remainingItems.drop(1),
                trashedItems = state.trashedItems + currentItem,
                history = state.history + SwipeAction(currentItem, wasTrashed = true)
            )
        }
    }

    fun undo() {
        val state = _uiState.value as? UiState.Swiping ?: return
        val lastAction = state.history.lastOrNull() ?: return

        if (lastAction.wasTrashed) {
            trashStorage.removeTrashedId(lastAction.item.id)
        } else {
            trashStorage.removeKeptId(lastAction.item.id)
        }

        val newHistory = state.history.dropLast(1)
        val newTrashed = if (lastAction.wasTrashed) {
            state.trashedItems.filterNot { it.id == lastAction.item.id }
        } else state.trashedItems

        val newKeptCount = if (!lastAction.wasTrashed) {
            (state.keptCount - 1).coerceAtLeast(0)
        } else state.keptCount

        _uiState.update {
            state.copy(
                remainingItems = listOf(lastAction.item) + state.remainingItems,
                trashedItems = newTrashed,
                keptCount = newKeptCount,
                history = newHistory
            )
        }
    }

    private var lastRequestedTrashIds: List<Long> = emptyList()

    fun removeFromTrash(item: MediaItem) {
        trashStorage.removeTrashedId(item.id)
        when (val state = _uiState.value) {
            is UiState.FolderList -> {
                val updated = state.pendingTrashItems.filterNot { it.id == item.id }
                _uiState.update {
                    state.copy(
                        totalPendingTrashCount = updated.size,
                        pendingTrashItems = updated
                    )
                }
            }
            is UiState.Swiping -> {
                _uiState.update {
                    state.copy(
                        trashedItems = state.trashedItems.filterNot { it.id == item.id }
                    )
                }
            }
            else -> {}
        }
    }

    private var lastRequestedRestoreIds = listOf<Long>()
    private var lastRequestedPermanentDeleteIds = listOf<Long>()

    fun openRecycleBin() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val pending = repository.getMediaByIds(trashStorage.getTrashedIds().toList())
            val system = repository.getTrashedMedia()
            val pendingIds = pending.map { it.id }.toSet()
            val systemIds = system.map { it.id }.toSet()
            val allItems = (pending + system).distinctBy { it.id }

            _uiState.value = UiState.RecycleBin(
                items = allItems,
                systemTrashedIds = systemIds,
                pendingTrashedIds = pendingIds,
                selectedIds = emptySet(),
                isSelecting = false
            )
        }
    }

    fun startRecycleBinSelection() {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        _uiState.value = state.copy(
            selectedIds = emptySet(),
            isSelecting = true
        )
    }

    fun toggleRecycleBinSelection(id: Long) {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        val newSelected = if (state.selectedIds.contains(id)) {
            state.selectedIds - id
        } else {
            state.selectedIds + id
        }
        _uiState.value = state.copy(
            selectedIds = newSelected,
            isSelecting = true
        )
    }

    fun selectAllRecycleBin() {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        _uiState.value = state.copy(
            selectedIds = state.items.map { it.id }.toSet(),
            isSelecting = true
        )
    }

    fun deselectAllRecycleBin() {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        _uiState.value = state.copy(
            selectedIds = emptySet(),
            isSelecting = true
        )
    }

    fun clearRecycleBinSelection() {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        _uiState.value = state.copy(
            selectedIds = emptySet(),
            isSelecting = false
        )
    }

    fun restoreItems(
        targetIds: List<Long>,
        onIntentReady: (IntentSenderRequest) -> Unit,
        onDirectSuccess: () -> Unit
    ) {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        val itemsToRestore = state.items.filter { targetIds.contains(it.id) }
        if (itemsToRestore.isEmpty()) return

        val pendingToRestore = itemsToRestore.filter { state.pendingTrashedIds.contains(it.id) }
        val systemToRestore = itemsToRestore.filter { state.systemTrashedIds.contains(it.id) }

        // Pending items can be restored instantly without system dialog
        if (pendingToRestore.isNotEmpty()) {
            trashStorage.removeTrashedIds(pendingToRestore.map { it.id })
        }

        if (systemToRestore.isNotEmpty()) {
            lastRequestedRestoreIds = systemToRestore.map { it.id }
            val pendingIntent = repository.createRestoreRequest(systemToRestore.map { it.uri })
            if (pendingIntent != null) {
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                onIntentReady(request)
                return
            }
        }

        openRecycleBin()
        onDirectSuccess()
    }

    fun onRestoreSuccess() {
        lastRequestedRestoreIds = emptyList()
        openRecycleBin()
    }

    fun permanentlyDeleteItems(
        targetIds: List<Long>,
        onIntentReady: (IntentSenderRequest) -> Unit
    ) {
        val state = _uiState.value as? UiState.RecycleBin ?: return
        val itemsToDelete = state.items.filter { targetIds.contains(it.id) }
        if (itemsToDelete.isEmpty()) return

        val systemToDelete = itemsToDelete.filter { state.systemTrashedIds.contains(it.id) }
        val pendingToDelete = itemsToDelete.filter { state.pendingTrashedIds.contains(it.id) }

        if (pendingToDelete.isNotEmpty()) {
            trashStorage.removeTrashedIds(pendingToDelete.map { it.id })
        }

        if (systemToDelete.isNotEmpty()) {
            lastRequestedPermanentDeleteIds = systemToDelete.map { it.id }
            val pendingIntent = repository.createPermanentDeleteRequest(systemToDelete.map { it.uri })
            if (pendingIntent != null) {
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                onIntentReady(request)
                return
            }
        }

        openRecycleBin()
    }

    fun onPermanentDeleteSuccess() {
        lastRequestedPermanentDeleteIds = emptyList()
        openRecycleBin()
    }

    fun requestMoveTrash(onIntentReady: (IntentSenderRequest) -> Unit) {
        val itemsToTrash = when (val state = _uiState.value) {
            is UiState.FolderList -> state.pendingTrashItems
            is UiState.Swiping -> state.trashedItems
            else -> emptyList()
        }
        if (itemsToTrash.isEmpty()) return

        lastRequestedTrashIds = itemsToTrash.map { it.id }
        val uris = itemsToTrash.map { it.uri }
        val pendingIntent = repository.createTrashRequest(uris)
        if (pendingIntent != null) {
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            onIntentReady(request)
        }
    }

    fun onTrashSuccess() {
        trashStorage.removeTrashedIds(lastRequestedTrashIds)
        when (val state = _uiState.value) {
            is UiState.FolderList -> {
                loadFolders()
            }
            is UiState.Swiping -> {
                val trashedSet = lastRequestedTrashIds.toSet()
                _uiState.update {
                    state.copy(
                        trashedItems = state.trashedItems.filterNot { trashedSet.contains(it.id) }
                    )
                }
            }
            else -> {}
        }
    }

    fun backToFolders() {
        loadFolders()
    }
}
