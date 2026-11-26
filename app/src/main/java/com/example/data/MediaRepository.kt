package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import com.example.data.local.MediaItemDao
import com.example.data.local.MediaItemEntity
import com.example.model.CropAspectRatio
import com.example.model.FilterPreset
import com.example.model.MediaType
import com.example.util.MediaFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface DeleteMediaResult {
    data class Deleted(val count: Int) : DeleteMediaResult
    data class RequiresConfirmation(val intentSender: IntentSender, val targetIds: List<Long>) : DeleteMediaResult
}

data class InternalDiskStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long
)

class MediaRepository(
    private val context: Context,
    private val dao: MediaItemDao
) {
    private val localMediaDir: File
        get() = File(context.filesDir, "snapstream_media").apply { mkdirs() }

    val allMedia: Flow<List<MediaItemEntity>> = dao.observeAllMedia()
    val videos: Flow<List<MediaItemEntity>> = dao.observeVideos()

    fun observeById(id: Long): Flow<MediaItemEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): MediaItemEntity? = dao.getById(id)

    /**
     * Scans the user's device MediaStore for all images and videos offline.
     * Preserves favorites and edit metadata for previously scanned items.
     */
    suspend fun scanDeviceMedia(): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val existingMap = dao.getAllOnce().associateBy { it.contentUri }
        val scannedItems = mutableListOf<MediaItemEntity>()

        // 1. Scan Images
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageProjection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            resolver.query(
                imageCollection,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val msId = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, msId).toString()
                    val name = cursor.getString(nameCol) ?: "Image_$msId"
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val dateSec = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val width = cursor.getInt(widthCol).coerceAtLeast(0)
                    val height = cursor.getInt(heightCol).coerceAtLeast(0)
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty().ifBlank { "Pictures" } else "Pictures"
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else bucket

                    val prev = existingMap[uri]
                    scannedItems.add(
                        prev?.copy(
                            mediaStoreId = msId,
                            title = name.substringBeforeLast('.').ifBlank { name },
                            folderName = bucket,
                            relativePath = relPath,
                            mimeType = mime,
                            widthPx = if (width > 0) width else prev.widthPx,
                            heightPx = if (height > 0) height else prev.heightPx,
                            fileSizeBytes = size,
                            dateModifiedSec = dateSec
                        ) ?: MediaItemEntity(
                            mediaStoreId = msId,
                            title = name.substringBeforeLast('.').ifBlank { name },
                            mediaType = MediaType.PHOTO.name,
                            folderName = bucket,
                            relativePath = relPath,
                            mimeType = mime,
                            contentUri = uri,
                            originalUri = uri,
                            widthPx = width,
                            heightPx = height,
                            fileSizeBytes = size,
                            dateModifiedSec = dateSec
                        )
                    )
                }
            }
        }

        // 2. Scan Videos
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val videoProjection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            resolver.query(
                videoCollection,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val msId = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, msId).toString()
                    val name = cursor.getString(nameCol) ?: "Video_$msId"
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val dateSec = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val width = cursor.getInt(widthCol).coerceAtLeast(0)
                    val height = cursor.getInt(heightCol).coerceAtLeast(0)
                    val duration = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty().ifBlank { "Movies" } else "Movies"
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else bucket

                    val prev = existingMap[uri]
                    scannedItems.add(
                        prev?.copy(
                            mediaStoreId = msId,
                            title = name.substringBeforeLast('.').ifBlank { name },
                            folderName = bucket,
                            relativePath = relPath,
                            mimeType = mime,
                            widthPx = if (width > 0) width else prev.widthPx,
                            heightPx = if (height > 0) height else prev.heightPx,
                            durationMs = duration,
                            trimEndMs = if (prev.trimEndMs > 0L) prev.trimEndMs else duration,
                            fileSizeBytes = size,
                            dateModifiedSec = dateSec
                        ) ?: MediaItemEntity(
                            mediaStoreId = msId,
                            title = name.substringBeforeLast('.').ifBlank { name },
                            mediaType = MediaType.VIDEO.name,
                            folderName = bucket,
                            relativePath = relPath,
                            mimeType = mime,
                            contentUri = uri,
                            originalUri = uri,
                            durationMs = duration,
                            trimStartMs = 0L,
                            trimEndMs = duration,
                            widthPx = width,
                            heightPx = height,
                            fileSizeBytes = size,
                            dateModifiedSec = dateSec
                        )
                    )
                }
            }
        }

        if (scannedItems.isNotEmpty()) {
            dao.insertAll(scannedItems)
        }

        // Remove stale MediaStore items that no longer exist on the device
        val activeUriSet = scannedItems.map { it.contentUri }.toSet()
        val staleIds = existingMap.values
            .filter { it.mediaStoreId != null && it.contentUri !in activeUriSet }
            .map { it.id }
        if (staleIds.isNotEmpty()) {
            dao.deleteByIds(staleIds)
        }

        scannedItems.size
    }

    /**
     * Fallback manual file picker import (works even if full MediaStore permission was denied).
     */
    suspend fun importPickedMedia(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var imported = 0
        val resolver = context.contentResolver
        for (uri in uris) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mime = resolver.getType(uri).orEmpty()
            val isVideo = mime.startsWith("video/")
            val ext = when {
                isVideo -> "mp4"
                mime.contains("png") -> "png"
                else -> "jpg"
            }
            val displayName = queryDisplayName(uri)?.substringBeforeLast('.')?.ifBlank { null }
                ?: if (isVideo) "Video_${System.currentTimeMillis() % 10000}" else "Photo_${System.currentTimeMillis() % 10000}"

            val destFile = File(localMediaDir, "picked_${System.currentTimeMillis()}_$imported.$ext")
            val copied = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !destFile.exists()) continue

            val nowSec = System.currentTimeMillis() / 1000L
            val fileUri = Uri.fromFile(destFile).toString()
            val entity = if (isVideo) {
                val meta = extractVideoInfo(destFile.absolutePath)
                MediaItemEntity(
                    title = displayName,
                    mediaType = MediaType.VIDEO.name,
                    folderName = "Imported",
                    relativePath = "SnapStream/Imported",
                    mimeType = mime.ifEmpty { "video/mp4" },
                    contentUri = fileUri,
                    originalUri = fileUri,
                    durationMs = meta.first,
                    trimEndMs = meta.first,
                    widthPx = meta.second,
                    heightPx = meta.third,
                    fileSizeBytes = destFile.length(),
                    dateModifiedSec = nowSec
                )
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, bounds)
                MediaItemEntity(
                    title = displayName,
                    mediaType = MediaType.PHOTO.name,
                    folderName = "Imported",
                    relativePath = "SnapStream/Imported",
                    mimeType = mime.ifEmpty { "image/jpeg" },
                    contentUri = fileUri,
                    originalUri = fileUri,
                    widthPx = bounds.outWidth.coerceAtLeast(1),
                    heightPx = bounds.outHeight.coerceAtLeast(1),
                    fileSizeBytes = destFile.length(),
                    dateModifiedSec = nowSec
                )
            }
            dao.insert(entity)
            imported++
        }
        imported
    }

    suspend fun saveEditedMedia(
        originalItem: MediaItemEntity,
        title: String,
        folderName: String,
        isFavorite: Boolean,
        preset: FilterPreset,
        intensity: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        cropAspectRatio: CropAspectRatio,
        trimStartMs: Long,
        trimEndMs: Long,
        saveAsCopy: Boolean,
        highQuality: Boolean
    ): Result<MediaItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val nowSec = System.currentTimeMillis() / 1000L
            val hasVisualEdits = preset != FilterPreset.ORIGINAL ||
                brightness != 0f ||
                contrast != 1f ||
                saturation != 1f ||
                rotationDegrees % 360 != 0 ||
                flipHorizontal ||
                cropLeft > 0.005f ||
                cropTop > 0.005f ||
                cropRight < 0.995f ||
                cropBottom < 0.995f ||
                (originalItem.typeEnum == MediaType.VIDEO &&
                    (trimStartMs > 0L || (originalItem.durationMs > 0L && trimEndMs < originalItem.durationMs)))

            if (originalItem.typeEnum == MediaType.PHOTO) {
                val rendered = MediaFilterEngine.renderAndSaveEditedImageToDevice(
                    context = context,
                    sourceUriString = originalItem.originalUri,
                    displayName = title,
                    preset = preset,
                    intensity = intensity,
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    highQuality = highQuality
                )

                val renderedUri = Uri.parse(rendered.uriString)
                val newMediaStoreId = if (renderedUri.scheme == "content") {
                    runCatching { ContentUris.parseId(renderedUri) }.getOrNull()
                } else null

                if (saveAsCopy || originalItem.mediaStoreId != null) {
                    val copyEntity = originalItem.copy(
                        id = 0L,
                        mediaStoreId = newMediaStoreId,
                        title = "${title.trim().ifEmpty { originalItem.title }}_edit",
                        folderName = "SnapStream",
                        relativePath = "Pictures/SnapStream",
                        mimeType = "image/jpeg",
                        contentUri = rendered.uriString,
                        originalUri = originalItem.originalUri,
                        filterPreset = preset.name,
                        filterIntensity = intensity,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        rotationDegrees = rotationDegrees,
                        flipHorizontal = flipHorizontal,
                        cropLeft = cropLeft,
                        cropTop = cropTop,
                        cropRight = cropRight,
                        cropBottom = cropBottom,
                        cropAspectRatio = cropAspectRatio.name,
                        widthPx = rendered.widthPx,
                        heightPx = rendered.heightPx,
                        fileSizeBytes = rendered.fileSizeBytes,
                        isFavorite = isFavorite,
                        isEdited = hasVisualEdits,
                        dateModifiedSec = nowSec
                    )
                    val newId = dao.insert(copyEntity)
                    copyEntity.copy(id = newId)
                } else {
                    val updated = originalItem.copy(
                        title = title.trim().ifEmpty { originalItem.title },
                        folderName = folderName,
                        contentUri = rendered.uriString,
                        filterPreset = preset.name,
                        filterIntensity = intensity,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        rotationDegrees = rotationDegrees,
                        flipHorizontal = flipHorizontal,
                        cropLeft = cropLeft,
                        cropTop = cropTop,
                        cropRight = cropRight,
                        cropBottom = cropBottom,
                        cropAspectRatio = cropAspectRatio.name,
                        widthPx = rendered.widthPx,
                        heightPx = rendered.heightPx,
                        fileSizeBytes = rendered.fileSizeBytes,
                        isFavorite = isFavorite,
                        isEdited = hasVisualEdits,
                        dateModifiedSec = nowSec
                    )
                    dao.update(updated)
                    updated
                }
            } else {
                val updated = originalItem.copy(
                    id = if (saveAsCopy) 0L else originalItem.id,
                    mediaStoreId = if (saveAsCopy) null else originalItem.mediaStoreId,
                    title = if (saveAsCopy) "${title.trim().ifEmpty { originalItem.title }}_edit" else title.trim().ifEmpty { originalItem.title },
                    folderName = folderName,
                    filterPreset = preset.name,
                    filterIntensity = intensity,
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    cropAspectRatio = cropAspectRatio.name,
                    trimStartMs = trimStartMs.coerceAtLeast(0L),
                    trimEndMs = trimEndMs.coerceAtLeast(trimStartMs + 500L)
                        .coerceAtMost(originalItem.durationMs.coerceAtLeast(1000L)),
                    isFavorite = isFavorite,
                    isEdited = hasVisualEdits,
                    dateModifiedSec = nowSec
                )
                if (saveAsCopy) {
                    val newId = dao.insert(updated)
                    updated.copy(id = newId)
                } else {
                    dao.update(updated)
                    updated
                }
            }
        }
    }

    suspend fun toggleFavorite(item: MediaItemEntity) = withContext(Dispatchers.IO) {
        dao.update(item.copy(isFavorite = !item.isFavorite))
    }

    suspend fun setFavorites(items: List<MediaItemEntity>, favorite: Boolean) = withContext(Dispatchers.IO) {
        items.forEach { dao.update(it.copy(isFavorite = favorite)) }
    }

    suspend fun requestDeleteItems(items: List<MediaItemEntity>): DeleteMediaResult =
        withContext(Dispatchers.IO) {
            val contentItems = items.filter { it.contentUri.startsWith("content://") }
            val fileItems = items.filter { !it.contentUri.startsWith("content://") }

            // Delete app-owned local files immediately
            fileItems.forEach { item ->
                val path = MediaFilterEngine.parseUriOrFile(item.contentUri).path
                if (path != null && path.startsWith(context.filesDir.absolutePath)) {
                    File(path).delete()
                }
            }
            if (fileItems.isNotEmpty()) {
                dao.deleteByIds(fileItems.map { it.id })
            }

            if (contentItems.isEmpty()) {
                return@withContext DeleteMediaResult.Deleted(fileItems.size)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = contentItems.map { Uri.parse(it.contentUri) }
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                DeleteMediaResult.RequiresConfirmation(
                    intentSender = pendingIntent.intentSender,
                    targetIds = contentItems.map { it.id }
                )
            } else {
                var removed = fileItems.size
                contentItems.forEach { item ->
                    val deletedRows = runCatching {
                        context.contentResolver.delete(Uri.parse(item.contentUri), null, null)
                    }.getOrDefault(0)
                    if (deletedRows > 0) {
                        dao.deleteById(item.id)
                        removed++
                    }
                }
                DeleteMediaResult.Deleted(removed)
            }
        }

    suspend fun completeConfirmedDelete(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            dao.deleteByIds(ids)
        }
    }

    suspend fun getVideoThumbnailFile(item: MediaItemEntity): File? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "video_thumbs").apply { mkdirs() }
        val thumbFile = File(cacheDir, "thumb_${item.id}_${item.dateModifiedSec}.jpg")
        if (thumbFile.exists() && thumbFile.length() > 0L) return@withContext thumbFile

        val uri = MediaFilterEngine.parseUriOrFile(item.contentUri)
        val bitmap: Bitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                context.contentResolver.loadThumbnail(uri, Size(480, 480), null)
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    if (uri.scheme == "content") {
                        retriever.setDataSource(context, uri)
                    } else {
                        retriever.setDataSource(uri.path)
                    }
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }.getOrNull()

        if (bitmap != null) {
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            bitmap.recycle()
            if (thumbFile.exists()) thumbFile else null
        } else null
    }

    fun getInternalDiskStats(): InternalDiskStats {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val available = stat.blockSizeLong * stat.availableBlocksLong
            InternalDiskStats(
                totalBytes = total,
                availableBytes = available,
                usedBytes = (total - available).coerceAtLeast(0L)
            )
        } catch (_: Exception) {
            InternalDiskStats(0L, 0L, 0L)
        }
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { file ->
            freed += dirSize(file)
            file.deleteRecursively()
        }
        freed
    }

    private fun dirSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVideoInfo(path: String): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(dur, w, h)
        } catch (_: Exception) {
            Triple(0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
