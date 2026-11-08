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

sealed interface UiState {
    object Loading : UiState
    data class FolderList(
        val folders: List<FolderItem>,
        val totalPendingTrashCount: Int = 0
    ) : UiState
    data class Swiping(
        val folderName: String,
        val totalCount: Int,
        val remainingItems: List<MediaItem>,
        val trashedItems: List<MediaItem>,
        val keptCount: Int,
        val allFolderItemIds: List<Long>,
        val history: List<SwipeAction>,
        val isAllReviewedInitially: Boolean = false
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

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = AppUpdateManager.checkForUpdate()
            if (info != null && info.isUpdateAvailable) {
                _updateInfo.value = info
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
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
            _uiState.value = UiState.FolderList(folders, totalPendingTrashCount = pendingTrashIds.size)
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

            _uiState.value = UiState.Swiping(
                folderName = folderName,
                totalCount = allFolderItems.size,
                remainingItems = remainingItems,
                trashedItems = existingTrashedItems,
                keptCount = folderKeptCount,
                allFolderItemIds = allFolderItems.map { it.id },
                history = emptyList(),
                isAllReviewedInitially = isAllReviewed
            )
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

    fun removeFromTrash(item: MediaItem) {
        trashStorage.removeTrashedId(item.id)
        val state = _uiState.value as? UiState.Swiping ?: return
        _uiState.update {
            state.copy(
                trashedItems = state.trashedItems.filterNot { it.id == item.id }
            )
        }
    }

    fun requestMoveTrash(onIntentReady: (IntentSenderRequest) -> Unit) {
        val state = _uiState.value as? UiState.Swiping ?: return
        if (state.trashedItems.isEmpty()) return

        val uris = state.trashedItems.map { it.uri }
        val pendingIntent = repository.createTrashRequest(uris)
        if (pendingIntent != null) {
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            onIntentReady(request)
        } else {
            viewModelScope.launch {
                repository.deleteDirect(uris)
                onTrashSuccess()
            }
        }
    }

    fun onTrashSuccess() {
        val state = _uiState.value as? UiState.Swiping ?: return
        val trashedIds = state.trashedItems.map { it.id }
        trashStorage.removeTrashedIds(trashedIds)

        _uiState.update {
            state.copy(
                trashedItems = emptyList()
            )
        }
    }

    fun backToFolders() {
        loadFolders()
    }
}
