package com.example.ui.viewmodel

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DeleteMediaResult
import com.example.data.MediaRepository
import com.example.data.PreferencesRepository
import com.example.data.local.MediaItemEntity
import com.example.model.AppPreferences
import com.example.model.BrowseFilter
import com.example.model.DeviceStorageInfo
import com.example.model.MediaType
import com.example.model.SortOrder
import com.example.model.ViewMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val LARGE_FILE_THRESHOLD_BYTES = 10L * 1024L * 1024L // 10 MB

class SnapStreamViewModel(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _selectedGalleryFilter = MutableStateFlow(BrowseFilter.ALL)
    val selectedGalleryFilter: StateFlow<BrowseFilter> = _selectedGalleryFilter.asStateFlow()

    private val _selectedGalleryFolder = MutableStateFlow<String?>(null)
    val selectedGalleryFolder: StateFlow<String?> = _selectedGalleryFolder.asStateFlow()

    private val _selectedVideoFilter = MutableStateFlow(BrowseFilter.ALL)
    val selectedVideoFilter: StateFlow<BrowseFilter> = _selectedVideoFilter.asStateFlow()

    private val _selectedVideoFolder = MutableStateFlow<String?>(null)
    val selectedVideoFolder: StateFlow<String?> = _selectedVideoFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    private val _pendingDeleteIds = MutableStateFlow<List<Long>>(emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _deleteConfirmationSender = MutableSharedFlow<IntentSender>(extraBufferCapacity = 2)
    val deleteConfirmationSender = _deleteConfirmationSender.asSharedFlow()

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferences()
        )

    val allMedia: StateFlow<List<MediaItemEntity>> = mediaRepository.allMedia
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val galleryFolders: StateFlow<List<String>> = mediaRepository.allMedia
        .map { items ->
            items.map { it.folderName }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val videoFolders: StateFlow<List<String>> = mediaRepository.videos
        .map { items ->
            items.map { it.folderName }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val filteredGalleryItems: StateFlow<List<MediaItemEntity>> = combine(
        mediaRepository.allMedia,
        _selectedGalleryFilter,
        _selectedGalleryFolder,
        _searchQuery,
        preferences
    ) { items, filter, folder, query, prefs ->
        applyFilterAndSort(items, filter, folder, query, prefs.sortOrder)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val filteredVideoItems: StateFlow<List<MediaItemEntity>> = combine(
        mediaRepository.videos,
        _selectedVideoFilter,
        _selectedVideoFolder,
        _searchQuery,
        preferences
    ) { items, filter, folder, query, prefs ->
        applyFilterAndSort(items, filter, folder, query, prefs.sortOrder)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val storageInfo: StateFlow<DeviceStorageInfo> = mediaRepository.allMedia
        .map { items ->
            val disk = mediaRepository.getInternalDiskStats()
            val photos = items.filter { it.typeEnum == MediaType.PHOTO }
            val videos = items.filter { it.typeEnum == MediaType.VIDEO }
            DeviceStorageInfo(
                totalBytes = disk.totalBytes,
                availableBytes = disk.availableBytes,
                usedBytes = disk.usedBytes,
                photoCount = photos.size,
                videoCount = videos.size,
                photosBytes = photos.sumOf { it.fileSizeBytes },
                videosBytes = videos.sumOf { it.fileSizeBytes },
                editedCount = items.count { it.isEdited },
                largeFilesCount = items.count { it.fileSizeBytes >= LARGE_FILE_THRESHOLD_BYTES }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DeviceStorageInfo()
        )

    private fun applyFilterAndSort(
        items: List<MediaItemEntity>,
        filter: BrowseFilter,
        folder: String?,
        query: String,
        sortOrder: SortOrder
    ): List<MediaItemEntity> {
        val trimmed = query.trim()
        val filtered = items.filter { item ->
            val matchesFilter = when (filter) {
                BrowseFilter.ALL -> true
                BrowseFilter.PHOTOS -> item.typeEnum == MediaType.PHOTO
                BrowseFilter.VIDEOS -> item.typeEnum == MediaType.VIDEO
                BrowseFilter.FAVORITES -> item.isFavorite
                BrowseFilter.EDITED -> item.isEdited
                BrowseFilter.LARGE -> item.fileSizeBytes >= LARGE_FILE_THRESHOLD_BYTES
            }
            val matchesFolder = folder == null || item.folderName.equals(folder, ignoreCase = true)
            val matchesQuery = trimmed.isEmpty() ||
                item.title.contains(trimmed, ignoreCase = true) ||
                item.folderName.contains(trimmed, ignoreCase = true) ||
                item.relativePath.contains(trimmed, ignoreCase = true)
            matchesFilter && matchesFolder && matchesQuery
        }

        return when (sortOrder) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.dateModifiedSec }
            SortOrder.OLDEST -> filtered.sortedBy { it.dateModifiedSec }
            SortOrder.LARGEST -> filtered.sortedByDescending { it.fileSizeBytes }
            SortOrder.SMALLEST -> filtered.sortedBy { it.fileSizeBytes }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    fun scanDeviceMedia(showToast: Boolean = false) {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val count = mediaRepository.scanDeviceMedia()
            _isScanning.value = false
            if (showToast) {
                _snackbarMessage.tryEmit("Scanned $count media file${if (count == 1) "" else "s"} on device")
            }
        }
    }

    fun importPickedMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = mediaRepository.importPickedMedia(uris)
            if (count > 0) {
                _snackbarMessage.tryEmit("Added $count file${if (count == 1) "" else "s"}")
            }
        }
    }

    fun selectGalleryFilter(filter: BrowseFilter) {
        _selectedGalleryFilter.value = filter
    }

    fun selectGalleryFolder(folder: String?) {
        _selectedGalleryFolder.value = folder
    }

    fun selectVideoFilter(filter: BrowseFilter) {
        _selectedVideoFilter.value = filter
    }

    fun selectVideoFolder(folder: String?) {
        _selectedVideoFolder.value = folder
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        val next = if (preferences.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        viewModelScope.launch { preferencesRepository.setViewMode(next) }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { preferencesRepository.setViewMode(mode) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferencesRepository.setSortOrder(order) }
    }

    fun toggleDarkMode() {
        viewModelScope.launch { preferencesRepository.setDarkMode(!preferences.value.darkModeEnabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkMode(enabled) }
    }

    fun setDeviceLabel(label: String) {
        viewModelScope.launch {
            preferencesRepository.setDeviceLabel(label)
            _snackbarMessage.tryEmit("Device label updated")
        }
    }

    fun setSaveEditsAsCopy(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSaveEditsAsCopy(enabled) }
    }

    fun setHighQualityExport(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHighQualityExport(enabled) }
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch { mediaRepository.toggleFavorite(item) }
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _selectedItemIds.value = current
    }

    fun selectAll(items: List<MediaItemEntity>) {
        _selectedItemIds.value = items.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun favoriteSelected() {
        val ids = _selectedItemIds.value
        val items = allMedia.value.filter { it.id in ids }
        if (items.isEmpty()) return
        viewModelScope.launch {
            mediaRepository.setFavorites(items, true)
            _selectedItemIds.value = emptySet()
            _snackbarMessage.tryEmit("Starred ${items.size} item${if (items.size == 1) "" else "s"}")
        }
    }

    fun deleteItems(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (val res = mediaRepository.requestDeleteItems(items)) {
                is DeleteMediaResult.Deleted -> {
                    _selectedItemIds.value = emptySet()
                    _snackbarMessage.tryEmit("Deleted ${res.count} file${if (res.count == 1) "" else "s"}")
                }
                is DeleteMediaResult.RequiresConfirmation -> {
                    _pendingDeleteIds.value = res.targetIds
                    _deleteConfirmationSender.tryEmit(res.intentSender)
                }
            }
        }
    }

    fun onSystemDeleteConfirmed(approved: Boolean) {
        val ids = _pendingDeleteIds.value
        _pendingDeleteIds.value = emptyList()
        if (approved && ids.isNotEmpty()) {
            viewModelScope.launch {
                mediaRepository.completeConfirmedDelete(ids)
                _selectedItemIds.value = emptySet()
                _snackbarMessage.tryEmit("Deleted ${ids.size} file${if (ids.size == 1) "" else "s"}")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val freed = mediaRepository.clearCache()
            _snackbarMessage.tryEmit("Cleared ${freed / 1024L} KB of thumbnail cache")
        }
    }

    suspend fun getVideoThumbnailFile(item: MediaItemEntity): File? =
        mediaRepository.getVideoThumbnailFile(item)

    fun observeItem(id: Long) = mediaRepository.observeById(id)

    class Factory(
        private val mediaRepository: MediaRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SnapStreamViewModel(mediaRepository, preferencesRepository) as T
        }
    }
}
