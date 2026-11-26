package com.example.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import com.example.model.FilterPreset
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EditedBitmapResult(
    val uriString: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long
)

object MediaFilterEngine {

    fun buildColorMatrixValues(
        preset: FilterPreset,
        intensity: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): FloatArray {
        val base = AndroidColorMatrix()
        val presetMatrix = presetColorMatrix(preset)

        val blended = FloatArray(20)
        val identity = AndroidColorMatrix().array
        val presetArr = presetMatrix.array
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        for (i in 0 until 20) {
            blended[i] = identity[i] + (presetArr[i] - identity[i]) * clampedIntensity
        }
        base.set(blended)

        val satMatrix = AndroidColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 2f)) }
        base.postConcat(satMatrix)

        val c = contrast.coerceIn(0.4f, 2.0f)
        val translate = (-0.5f * c + 0.5f) * 255f + (brightness.coerceIn(-0.5f, 0.5f) * 255f)
        val bcMatrix = AndroidColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        base.postConcat(bcMatrix)
        return base.array
    }

    fun toComposeColorMatrix(
        preset: FilterPreset,
        intensity: Float = 1f,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f
    ): ComposeColorMatrix {
        return ComposeColorMatrix(
            buildColorMatrixValues(preset, intensity, brightness, contrast, saturation)
        )
    }

    private fun presetColorMatrix(preset: FilterPreset): AndroidColorMatrix {
        return when (preset) {
            FilterPreset.ORIGINAL -> AndroidColorMatrix()
            FilterPreset.ELECTRIC -> AndroidColorMatrix(
                floatArrayOf(
                    0.92f, 0f, 0.05f, 0f, -6f,
                    0f, 1.04f, 0.08f, 0f, 4f,
                    0.05f, 0.12f, 1.28f, 0f, 18f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.VIVID -> AndroidColorMatrix().apply { setSaturation(1.45f) }.apply {
                postConcat(
                    AndroidColorMatrix(
                        floatArrayOf(
                            1.08f, 0f, 0f, 0f, 6f,
                            0f, 1.08f, 0f, 0f, 6f,
                            0f, 0f, 1.08f, 0f, 6f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.NOIR -> AndroidColorMatrix().apply { setSaturation(0f) }.apply {
                postConcat(
                    AndroidColorMatrix(
                        floatArrayOf(
                            1.25f, 0f, 0f, 0f, -24f,
                            0f, 1.25f, 0f, 0f, -24f,
                            0f, 0f, 1.25f, 0f, -24f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.WARMTH -> AndroidColorMatrix(
                floatArrayOf(
                    1.16f, 0.06f, 0f, 0f, 12f,
                    0.02f, 1.04f, 0f, 0f, 4f,
                    0f, 0f, 0.85f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.CYBER -> AndroidColorMatrix(
                floatArrayOf(
                    1.08f, 0f, 0.14f, 0f, -4f,
                    0f, 0.96f, 0.12f, 0f, 6f,
                    0.18f, 0.08f, 1.24f, 0f, 16f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.MATTE -> AndroidColorMatrix(
                floatArrayOf(
                    0.92f, 0.04f, 0.02f, 0f, 18f,
                    0.02f, 0.92f, 0.04f, 0f, 18f,
                    0.02f, 0.04f, 0.90f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.CINEMA -> AndroidColorMatrix(
                floatArrayOf(
                    1.10f, 0.04f, -0.04f, 0f, -8f,
                    0.02f, 1.02f, 0.04f, 0f, -4f,
                    -0.05f, 0.08f, 1.12f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
    }

    fun renderAndSaveEditedImage(
        sourceFilePath: String,
        outputFile: File,
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
        highQuality: Boolean
    ): EditedBitmapResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFilePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048, 2048)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeFile(sourceFilePath, decodeOpts)
            ?: error("Unable to decode image at $sourceFilePath")

        val processed = processBitmap(
            rawBitmap = rawBitmap,
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
            cropBottom = cropBottom
        )

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            val quality = if (highQuality) 95 else 82
            processed.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        val outW = processed.width
        val outH = processed.height
        processed.recycle()

        return EditedBitmapResult(
            uriString = Uri.fromFile(outputFile).toString(),
            widthPx = outW,
            heightPx = outH,
            fileSizeBytes = outputFile.length()
        )
    }

    fun renderAndSaveEditedImageToDevice(
        context: Context,
        sourceUriString: String,
        displayName: String,
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
        highQuality: Boolean
    ): EditedBitmapResult {
        val sourceUri = parseUriOrFile(sourceUriString)
        val rawBitmap = decodeBitmapFromUri(context.contentResolver, sourceUri)
            ?: error("Unable to read image from $sourceUriString")

        val processed = processBitmap(
            rawBitmap = rawBitmap,
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
            cropBottom = cropBottom
        )

        val cleanName = displayName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            .ifEmpty { "SnapStream" }
        val fileName = "${cleanName}_edit_${System.currentTimeMillis()}.jpg"
        val quality = if (highQuality) 95 else 82
        val outW = processed.width
        val outH = processed.height

        // Save directly into MediaStore Pictures/SnapStream on Android 10+ so it appears in device gallery & file explorer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SnapStream")
                put(MediaStore.Images.Media.WIDTH, outW)
                put(MediaStore.Images.Media.HEIGHT, outH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val insertedUri = context.contentResolver.insert(collection, values)
            if (insertedUri != null) {
                try {
                    context.contentResolver.openOutputStream(insertedUri)?.use { out: OutputStream ->
                        processed.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(insertedUri, updateValues, null, null)
                    val fileSize = queryUriSize(context.contentResolver, insertedUri)
                    processed.recycle()
                    return EditedBitmapResult(
                        uriString = insertedUri.toString(),
                        widthPx = outW,
                        heightPx = outH,
                        fileSizeBytes = fileSize
                    )
                } catch (_: Exception) {
                    context.contentResolver.delete(insertedUri, null, null)
                }
            }
        }

        // Fallback to app-private media directory
        val fallbackDir = File(context.filesDir, "snapstream_edited").apply { mkdirs() }
        val outFile = File(fallbackDir, fileName)
        FileOutputStream(outFile).use { out ->
            processed.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        processed.recycle()
        return EditedBitmapResult(
            uriString = Uri.fromFile(outFile).toString(),
            widthPx = outW,
            heightPx = outH,
            fileSizeBytes = outFile.length()
        )
    }

    private fun processBitmap(
        rawBitmap: Bitmap,
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
        cropBottom: Float
    ): Bitmap {
        val transformMatrix = Matrix().apply {
            if (flipHorizontal) postScale(-1f, 1f)
            if (rotationDegrees % 360 != 0) postRotate((rotationDegrees % 360).toFloat())
        }
        val transformed = if (rotationDegrees % 360 != 0 || flipHorizontal) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, transformMatrix, true)
                .also { if (it != rawBitmap) rawBitmap.recycle() }
        } else {
            rawBitmap
        }

        val safeLeft = cropLeft.coerceIn(0f, 0.95f)
        val safeTop = cropTop.coerceIn(0f, 0.95f)
        val safeRight = cropRight.coerceIn(safeLeft + 0.05f, 1f)
        val safeBottom = cropBottom.coerceIn(safeTop + 0.05f, 1f)

        val cropX = (safeLeft * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val cropY = (safeTop * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val cropW = ((safeRight - safeLeft) * transformed.width).roundToInt()
            .coerceIn(1, transformed.width - cropX)
        val cropH = ((safeBottom - safeTop) * transformed.height).roundToInt()
            .coerceIn(1, transformed.height - cropY)

        val cropped = Bitmap.createBitmap(transformed, cropX, cropY, cropW, cropH)
        if (cropped != transformed) transformed.recycle()

        val resultBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                buildColorMatrixValues(preset, intensity, brightness, contrast, saturation)
            )
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        cropped.recycle()
        return resultBitmap
    }

    private fun decodeBitmapFromUri(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (uri.scheme == "file" && uri.path != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(uri.path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048, 2048)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(uri.path, opts)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048, 2048)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long {
        return try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun parseUriOrFile(value: String): Uri {
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            Uri.parse(value)
        } else {
            Uri.fromFile(File(value))
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, min(inSampleSize, 16))
    }
}
