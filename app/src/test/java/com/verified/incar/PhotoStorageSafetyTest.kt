package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import com.verified.incar.ui.HandoverPhotos
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStorageSafetyTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun failedWrite_preservesExistingEvidenceAndRemovesIncompleteTemporaryFile() {
        val dir = File(app.cacheDir, "atomic_failure").apply { mkdirs() }
        val target = File(dir, "before_FRONT.jpg").apply { writeText("original evidence") }
        assertThrows(IOException::class.java) {
            RecordFiles.writeAtomically(target) { output ->
                output.write("incomplete replacement".toByteArray())
                throw IOException("simulated full storage")
            }
        }
        assertEquals("original evidence", target.readText())
        assertEquals(listOf("before_FRONT.jpg"), dir.listFiles()!!.map { it.name })
        RecordFiles.writeAtomically(target) { it.write("complete replacement".toByteArray()) }
        assertEquals("complete replacement", target.readText())
    }

    @Test fun cameraReturn_afterModeSwitchCannotOverwriteEitherParkingPhoto() {
        val real = RecordFiles.parkingPhoto(app, false).apply { parentFile!!.mkdirs(); writeText("real photo") }
        val demo = RecordFiles.parkingPhoto(app, true).apply { parentFile!!.mkdirs(); writeText("demo photo") }
        val shot = File(app.cacheDir, "new_capture.jpg").apply { writeText("new capture") }
        assertNull(HandoverPhotos.keepCaptured(shot, demo.absolutePath, real))
        assertEquals("real photo", real.readText())
        assertEquals("demo photo", demo.readText())
        assertTrue(shot.exists())
        assertEquals(demo, HandoverPhotos.keepCaptured(shot, demo.absolutePath, demo))
        assertEquals("new capture", demo.readText())
        assertEquals("real photo", real.readText())
        assertFalse(shot.exists())
    }

    @Test fun exifPortrait_isUprightAndDecodedDimensionsStayWithinLimit() {
        val dir = File(app.cacheDir, "exif_picture")
        val original = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        for (x in 0 until original.width) for (y in 0 until original.height)
            original.setPixel(x, y, if (x < original.width / 2) Color.RED else Color.BLUE)
        val photo = HandoverPhotos.save(dir, HandoverPhotos.BEFORE, "FRONT", original)
        original.recycle()
        ExifInterface(photo.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val decoded = HandoverPhotos.decode(photo, 400)!!
        try {
            assertTrue(decoded.width <= 400 && decoded.height <= 400)
            assertEquals(2 * decoded.width, decoded.height)
            val top = decoded.getPixel(decoded.width / 2, decoded.height / 4)
            val bottom = decoded.getPixel(decoded.width / 2, decoded.height * 3 / 4)
            assertTrue("left red area rotates to top", Color.red(top) > Color.blue(top))
            assertTrue("right blue area rotates to bottom", Color.blue(bottom) > Color.red(bottom))
        } finally { decoded.recycle() }
    }

    @Test fun pdfMemo_keepsBlankLinesAndNeverSplitsSurrogatePairs() {
        val paint = Paint().apply { textSize = 14f }
        assertEquals(listOf("첫 줄", "", "둘째 줄"), RecordPdf.wrappedLines("첫 줄\r\n\r\n둘째 줄", paint, 500f))
        val lines = RecordPdf.wrappedLines("앞🚗뒤", paint, 1f)
        assertEquals(listOf("앞", "🚗", "뒤"), lines)
    }
}
