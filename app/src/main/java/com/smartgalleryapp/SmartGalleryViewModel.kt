package com.smartgalleryapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartgalleryapp.data.AppSettings
import com.smartgalleryapp.data.MediaCategory
import com.smartgalleryapp.data.MediaItem
import com.smartgalleryapp.data.MediaLocation
import com.smartgalleryapp.data.RepositoryState
import com.smartgalleryapp.data.SmartGalleryRepository
import com.smartgalleryapp.worker.PurgeScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab { HOME, LIBRARY, VAULT, REVIEW, SETTINGS, TRASH }

data class UiState(
    val repositoryState: RepositoryState,
    val hasRequestedInitialScan: Boolean = false,
    val isScanning: Boolean = false,
    val scanStep: String = "Preparing scan",
    val currentTab: MainTab = MainTab.HOME,
    val selectedCategory: MediaCategory = MediaCategory.ALL,
    val selectionMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val viewerItems: List<MediaItem> = emptyList(),
    val viewerIndex: Int = -1,
    val permissionSkipped: Boolean = false,
    val hasMediaPermission: Boolean = false,
    val eventMessage: String? = null,
) {
    val libraryItems: List<MediaItem>
        get() = repositoryState.items.filter {
            it.location == MediaLocation.LIBRARY && (selectedCategory == MediaCategory.ALL || it.category == selectedCategory)
        }
    val vaultItems: List<MediaItem> get() = repositoryState.items.filter { it.location == MediaLocation.VAULT }
    val reviewItems: List<MediaItem> get() = repositoryState.items.filter { it.isReviewDue && it.location != MediaLocation.TRASH }
    val trashItems: List<MediaItem> get() = repositoryState.items.filter { it.location == MediaLocation.TRASH }
    val viewerItem: MediaItem? get() = viewerItems.getOrNull(viewerIndex)
}

class SmartGalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmartGalleryRepository(application)
    private val _uiState: MutableStateFlow<UiState>
    val uiState: StateFlow<UiState>

    init {
        val settings = repository.loadSettings()
        val hasPermission = repository.hasMediaPermission()
        _uiState = MutableStateFlow(
            UiState(
                repositoryState = repository.loadState(settings),
                hasRequestedInitialScan = hasPermission || settings.permissionRequestedOnce,
                permissionSkipped = !hasPermission && settings.permissionRequestedOnce,
                hasMediaPermission = hasPermission,
            )
        )
        uiState = _uiState.asStateFlow()
        if (hasPermission) {
            scan()
        }
    }

    fun onPermissionFlowCompleted(granted: Boolean) {
        val updatedSettings = currentSettings().copy(permissionRequestedOnce = true)
        repository.saveSettings(updatedSettings)
        _uiState.value = _uiState.value.copy(
            repositoryState = repository.loadState(updatedSettings),
            permissionSkipped = !granted,
            hasMediaPermission = granted,
            hasRequestedInitialScan = true,
            eventMessage = if (granted) null else "Media access is off. Enable it from Settings to load your library.",
        )
        if (granted) scan()
    }

    fun scan() {
        viewModelScope.launch {
            val hasPermission = repository.hasMediaPermission()
            _uiState.value = _uiState.value.copy(
                isScanning = hasPermission,
                hasRequestedInitialScan = true,
                hasMediaPermission = hasPermission,
            )
            val state = withContext(Dispatchers.IO) {
                runCatching {
                    repository.scan(currentSettings()) { step ->
                        _uiState.value = _uiState.value.copy(scanStep = step)
                    }
                }.getOrElse {
                    _uiState.value = _uiState.value.copy(
                        eventMessage = "Could not load media. The app stayed safe and is showing an empty library instead.",
                    )
                    repository.loadState()
                }
            }
            _uiState.value = _uiState.value.copy(
                repositoryState = state,
                isScanning = false,
                scanStep = "All done",
                currentTab = MainTab.HOME,
                hasMediaPermission = hasPermission,
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = transform(currentSettings())
            repository.saveSettings(updated)
            if (updated.backgroundScan) {
                PurgeScheduler.schedule(getApplication())
            } else {
                PurgeScheduler.cancel(getApplication())
            }
            val state = repository.refreshForSettings(updated)
            _uiState.value = _uiState.value.copy(repositoryState = state, hasMediaPermission = repository.hasMediaPermission())
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab, selectedKeys = emptySet(), selectionMode = false)
    }

    fun selectCategory(category: MediaCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            selectionMode = enabled,
            selectedKeys = if (enabled) _uiState.value.selectedKeys else emptySet(),
        )
    }

    fun toggleSelection(itemKey: String) {
        val selected = _uiState.value.selectedKeys.toMutableSet()
        if (!selected.add(itemKey)) selected.remove(itemKey)
        _uiState.value = _uiState.value.copy(selectedKeys = selected, selectionMode = selected.isNotEmpty() || _uiState.value.selectionMode)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedKeys = emptySet(), selectionMode = false)
    }

    fun openViewer(item: MediaItem, sourceItems: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!currentSettings().autoTrack) {
                val source = sourceItems.ifEmpty { listOf(item) }
                _uiState.value = _uiState.value.copy(
                    viewerItems = source,
                    viewerIndex = source.indexOfFirst { it.key == item.key }.coerceAtLeast(0),
                )
            } else {
                val progress = if (item.isVideo) 30 else 100
                val state = repository.markOpened(item.key, progress)
                val refreshedSource = sourceItems.map { source ->
                    state.items.firstOrNull { it.key == source.key } ?: source
                }
                _uiState.value = _uiState.value.copy(
                    repositoryState = state,
                    viewerItems = refreshedSource,
                    viewerIndex = refreshedSource.indexOfFirst { it.key == item.key }.coerceAtLeast(0),
                )
            }
        }
    }

    fun closeViewer(progress: Int = 100) {
        val current = _uiState.value.viewerItem ?: return run { _uiState.value = _uiState.value.copy(viewerItems = emptyList(), viewerIndex = -1) }
        if (!currentSettings().autoTrack) {
            _uiState.value = _uiState.value.copy(viewerItems = emptyList(), viewerIndex = -1)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val state = repository.markOpened(current.key, progress)
            _uiState.value = _uiState.value.copy(
                repositoryState = state,
                viewerItems = emptyList(),
                viewerIndex = -1,
            )
        }
    }

    fun showPreviousViewerItem() {
        val current = _uiState.value.viewerIndex
        if (current > 0) {
            _uiState.value = _uiState.value.copy(viewerIndex = current - 1)
        }
    }

    fun showNextViewerItem() {
        val current = _uiState.value.viewerIndex
        if (current >= 0 && current < _uiState.value.viewerItems.lastIndex) {
            _uiState.value = _uiState.value.copy(viewerIndex = current + 1)
        }
    }

    fun keepForever(itemKey: String) = mutateRepository("Marked as keep forever") { repository.keepForever(itemKey) }

    fun moveSelectedToVault() {
        val keys = _uiState.value.selectedKeys.toList()
        mutateRepository("Moved to Vault") { repository.moveToVault(keys) }
    }

    fun moveToTrash(keys: List<String>) = mutateRepository("Moved to Trash") {
        repository.moveToTrash(keys, currentSettings().trashRetentionDays)
    }

    fun moveSelectedToTrash() {
        val keys = _uiState.value.selectedKeys.toList()
        mutateRepository("Moved to Trash") { repository.moveToTrash(keys, currentSettings().trashRetentionDays) }
    }

    fun addSelectedToReview() {
        val keys = _uiState.value.selectedKeys.toList()
        mutateRepository("Added to Review") { repository.addToReview(keys) }
    }

    fun recover(itemKey: String) = mutateRepository("Recovered from Trash") { repository.recoverFromTrash(itemKey) }

    fun deleteNow(itemKey: String) = mutateRepository("Deleted permanently") { repository.permanentlyDelete(itemKey) }

    fun emptyTrash() = mutateRepository("Trash emptied") { repository.emptyTrash() }

    fun purgeExpired() = mutateRepository("Expired Trash purged") {
        repository.purgeExpiredTrash()
        repository.loadState()
    }

    fun clearAllData() = mutateRepository("App data cleared") { repository.clearAllData() }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(eventMessage = null)
    }

    private fun mutateRepository(message: String, block: suspend () -> RepositoryState) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { block() }
            _uiState.value = _uiState.value.copy(
                repositoryState = state,
                selectedKeys = emptySet(),
                selectionMode = false,
                eventMessage = message,
            )
        }
    }

    private fun currentSettings(): AppSettings = _uiState.value.repositoryState.settings
}
