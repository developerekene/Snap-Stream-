package com.example

import android.graphics.Bitmap
import com.example.model.FilterPreset
import com.example.util.MediaFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaFilterEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `identity matrix preserves pixel transforms when original`() {
        val values = MediaFilterEngine.buildColorMatrixValues(
            preset = FilterPreset.ORIGINAL,
            intensity = 1f,
            brightness = 0f,
            contrast = 1f,
            saturation = 1f
        )
        assertEquals(20, values.size)
        assertEquals(1f, values[0], 0.01f)
        assertEquals(1f, values[6], 0.01f)
        assertEquals(1f, values[12], 0.01f)
        assertEquals(1f, values[18], 0.01f)
    }

    @Test
    fun `renderAndSaveEditedImage crops and filters bitmap to disk`() {
        val srcFile = tempFolder.newFile("source.jpg")
        val srcBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(srcFile).use { out ->
            srcBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        srcBitmap.recycle()

        val outFile = File(tempFolder.root, "edited.jpg")
        val result = MediaFilterEngine.renderAndSaveEditedImage(
            sourceFilePath = srcFile.absolutePath,
            outputFile = outFile,
            preset = FilterPreset.ELECTRIC,
            intensity = 0.8f,
            brightness = 0.1f,
            contrast = 1.2f,
            saturation = 1.1f,
            rotationDegrees = 90,
            flipHorizontal = true,
            cropLeft = 0.1f,
            cropTop = 0.1f,
            cropRight = 0.6f,
            cropBottom = 0.6f,
            highQuality = true
        )

        assertTrue(outFile.exists())
        assertTrue(result.fileSizeBytes > 0L)
        // Rotated 90deg -> 100x200, cropped to 50% x 50% -> ~50x100
        assertEquals(50, result.widthPx)
        assertEquals(100, result.heightPx)
    }
}
