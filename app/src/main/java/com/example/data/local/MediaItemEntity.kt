package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.CropAspectRatio
import com.example.model.FilterPreset
import com.example.model.MediaType

@Entity(
    tableName = "media_items",
    indices = [Index(value = ["contentUri"], unique = true)]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaStoreId: Long? = null,
    val title: String,
    val mediaType: String, // MediaType.PHOTO.name or MediaType.VIDEO.name
    val folderName: String, // e.g., Camera, Screenshots, Download, SnapStream
    val relativePath: String = "",
    val mimeType: String = "image/jpeg",
    val contentUri: String, // content://media/external/... or file://...
    val originalUri: String,
    val filterPreset: String = FilterPreset.ORIGINAL.name,
    val filterIntensity: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val cropAspectRatio: String = CropAspectRatio.FREE.name,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val durationMs: Long = 0L,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val isEdited: Boolean = false,
    val dateModifiedSec: Long = System.currentTimeMillis() / 1000L
) {
    val typeEnum: MediaType
        get() = if (mediaType == MediaType.VIDEO.name) MediaType.VIDEO else MediaType.PHOTO

    val filterEnum: FilterPreset
        get() = FilterPreset.fromName(filterPreset)

    val cropRatioEnum: CropAspectRatio
        get() = CropAspectRatio.fromName(cropAspectRatio)
}
