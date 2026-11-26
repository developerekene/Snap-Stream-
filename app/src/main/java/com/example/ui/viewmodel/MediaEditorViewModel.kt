package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MediaRepository
import com.example.data.PreferencesRepository
import com.example.data.local.MediaItemEntity
import com.example.model.CropAspectRatio
import com.example.model.FilterPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorToolTab(val label: String) {
    FILTERS("Filters"),
    CROP("Crop"),
    ADJUST("Adjust"),
    DETAILS("Details")
}

data class MediaEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val originalItem: MediaItemEntity? = null,
    val activeTab: EditorToolTab = EditorToolTab.FILTERS,
    val title: String = "",
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL,
    val filterIntensity: Float = 1.0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.FREE,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val errorMessage: String? = null
)

class MediaEditorViewModel(
    private val mediaId: Long,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaEditorUiState())
    val uiState: StateFlow<MediaEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val item = mediaRepository.getById(mediaId)
            if (item == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Media item not found") }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        originalItem = item,
                        title = item.title,
                        folderName = item.folderName,
                        isFavorite = item.isFavorite,
                        filterPreset = item.filterEnum,
                        filterIntensity = item.filterIntensity,
                        brightness = item.brightness,
                        contrast = item.contrast,
                        saturation = item.saturation,
                        rotationDegrees = item.rotationDegrees,
                        flipHorizontal = item.flipHorizontal,
                        cropLeft = item.cropLeft,
                        cropTop = item.cropTop,
                        cropRight = item.cropRight,
                        cropBottom = item.cropBottom,
                        cropAspectRatio = item.cropRatioEnum,
                        trimStartMs = item.trimStartMs,
                        trimEndMs = if (item.trimEndMs > 0L) item.trimEndMs else item.durationMs
                    )
                }
            }
        }
    }

    fun selectTab(tab: EditorToolTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectFilter(preset: FilterPreset) {
        _uiState.update { it.copy(filterPreset = preset, filterIntensity = 1.0f) }
    }

    fun updateFilterIntensity(intensity: Float) {
        _uiState.update { it.copy(filterIntensity = intensity.coerceIn(0f, 1f)) }
    }

    fun updateBrightness(value: Float) {
        _uiState.update { it.copy(brightness = value.coerceIn(-0.5f, 0.5f)) }
    }

    fun updateContrast(value: Float) {
        _uiState.update { it.copy(contrast = value.coerceIn(0.5f, 1.8f)) }
    }

    fun updateSaturation(value: Float) {
        _uiState.update { it.copy(saturation = value.coerceIn(0f, 2f)) }
    }

    fun rotate90() {
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleFlipHorizontal() {
        _uiState.update { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun selectAspectRatio(aspectRatio: CropAspectRatio) {
        val state = _uiState.value
        val item = state.originalItem ?: return
        val effectiveW = if (state.rotationDegrees % 180 == 0) item.widthPx.coerceAtLeast(1) else item.heightPx.coerceAtLeast(1)
        val effectiveH = if (state.rotationDegrees % 180 == 0) item.heightPx.coerceAtLeast(1) else item.widthPx.coerceAtLeast(1)
        val imageAspect = effectiveW.toFloat() / effectiveH.toFloat()

        if (aspectRatio.ratio == null) {
            _uiState.update { it.copy(cropAspectRatio = aspectRatio) }
            return
        }

        val targetRatio = aspectRatio.ratio
        val (normW, normH) = if (targetRatio > imageAspect) {
            1.0f to (imageAspect / targetRatio).coerceIn(0.15f, 1f)
        } else {
            (targetRatio / imageAspect).coerceIn(0.15f, 1f) to 1.0f
        }
        val left = (1f - normW) / 2f
        val top = (1f - normH) / 2f
        _uiState.update {
            it.copy(
                cropAspectRatio = aspectRatio,
                cropLeft = left,
                cropTop = top,
                cropRight = left + normW,
                cropBottom = top + normH
            )
        }
    }

    fun updateCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        val safeL = left.coerceIn(0f, 0.85f)
        val safeT = top.coerceIn(0f, 0.85f)
        val safeR = right.coerceIn(safeL + 0.15f, 1f)
        val safeB = bottom.coerceIn(safeT + 0.15f, 1f)
        _uiState.update {
            it.copy(
                cropLeft = safeL,
                cropTop = safeT,
                cropRight = safeR,
                cropBottom = safeB
            )
        }
    }

    fun resetCrop() {
        _uiState.update {
            it.copy(
                cropLeft = 0f,
                cropTop = 0f,
                cropRight = 1f,
                cropBottom = 1f,
                cropAspectRatio = CropAspectRatio.FREE,
                rotationDegrees = 0,
                flipHorizontal = false
            )
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _uiState.update { it.copy(trimStartMs = startMs, trimEndMs = endMs) }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun resetAllEdits() {
        val orig = _uiState.value.originalItem ?: return
        _uiState.update {
            it.copy(
                filterPreset = FilterPreset.ORIGINAL,
                filterIntensity = 1.0f,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                rotationDegrees = 0,
                flipHorizontal = false,
                cropLeft = 0f,
                cropTop = 0f,
                cropRight = 1f,
                cropBottom = 1f,
                cropAspectRatio = CropAspectRatio.FREE,
                trimStartMs = 0L,
                trimEndMs = orig.durationMs
            )
        }
    }

    fun saveEdits(forceSaveAsCopy: Boolean? = null, onComplete: (Boolean, String) -> Unit) {
        val state = _uiState.value
        val item = state.originalItem ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val prefs = preferencesRepository.preferencesFlow.first()
            val asCopy = forceSaveAsCopy ?: prefs.saveEditsAsCopy

            val result = mediaRepository.saveEditedMedia(
                originalItem = item,
                title = state.title,
                folderName = state.folderName,
                isFavorite = state.isFavorite,
                preset = state.filterPreset,
                intensity = state.filterIntensity,
                brightness = state.brightness,
                contrast = state.contrast,
                saturation = state.saturation,
                rotationDegrees = state.rotationDegrees,
                flipHorizontal = state.flipHorizontal,
                cropLeft = state.cropLeft,
                cropTop = state.cropTop,
                cropRight = state.cropRight,
                cropBottom = state.cropBottom,
                cropAspectRatio = state.cropAspectRatio,
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                saveAsCopy = asCopy,
                highQuality = prefs.highQualityExport
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false) }
                    onComplete(true, "Saved to device gallery")
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to save edits"
                        )
                    }
                    onComplete(false, err.localizedMessage ?: "Failed to save edits")
                }
            )
        }
    }

    class Factory(
        private val mediaId: Long,
        private val mediaRepository: MediaRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MediaEditorViewModel(mediaId, mediaRepository, preferencesRepository) as T
        }
    }
}
