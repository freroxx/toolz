/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented verification of the Daily Ups photo pipeline — the part plain
 * JVM tests cannot touch: real Bitmap decode/re-encode + EXIF round trips.
 *
 * The critical case is the GEOTAGGED source: a JPEG carrying GPS latitude/
 * longitude plus device make/model/dates MUST come out exact-clean
 * (Ready), and its output MUST carry zero GPS/device EXIF.
 */
@RunWith(AndroidJUnit4::class)
class WhisperDailyUpPhotoPreprocessorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun solidBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.argb(255, 60, 120, 200))
        return bmp
    }

    /** JPEG bytes whose EXIF block claims GPS coordinates + device identity. */
    private fun geotaggedJpeg(): ByteArray {
        val plain = ByteArrayOutputStream().use { out ->
            solidBitmap(120, 160).compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        // androidx ExifInterface can only re-save the media it was opened from,
        // so the tagged copy is written through a scratch file.
        val scratch = java.io.File.createTempFile("dailyup_exif", ".jpg", context.cacheDir)
        scratch.outputStream().use { out -> out.write(plain) }
        ExifInterface(scratch).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "41/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "29/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "120/1")
            setAttribute(ExifInterface.TAG_MAKE, "TestCorp")
            setAttribute(ExifInterface.TAG_MODEL, "Pixel Spy-9000")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:18 09:00:00")
            saveAttributes()
        }
        val tagged = scratch.readBytes()
        scratch.delete()
        // Confirm the fixtures actually carry the tags before exercising the cut.
        val reloaded = ExifInterface(ByteArrayInputStream(tagged))
        assertEquals("41/1", reloaded.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals("TestCorp", reloaded.getAttribute(ExifInterface.TAG_MAKE))
        return tagged
    }

    private suspend fun preprocess(source: ByteArray, mime: String) =
        preprocessDailyUpPhoto(context, source, mime)

    @Test
    fun geotaggedPhoto_publishedClean_withNoDeviceOrGpsExif() = runBlocking {
        val result = preprocess(geotaggedJpeg(), "image/jpeg")
        val ready = result as? DailyUpPhotoPreparation.Ready
        assertTrue(
            "Geotagged source must publish as Ready; got ${result}. " +
                "METADATA_REMAINS on a re-encoded JPEG means the strip failed.",
            ready != null,
        )
        val photo = ready!!.photo
        assertEquals("image/jpeg", photo.mimeType)

        // EXIF/GPS audit on the exact bytes that would be uploaded.
        val out = ExifInterface(ByteArrayInputStream(photo.bytes))
        assertNull(out.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(out.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(out.getAttribute(ExifInterface.TAG_GPS_ALTITUDE))
        assertNull(out.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertNull(out.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
        assertNull(out.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(out.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(out.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun outputStaysWithinBounds_andDecodesBack() = runBlocking {
        val photo = (preprocess(geotaggedJpeg(), "image/jpeg") as
            DailyUpPhotoPreparation.Ready).photo
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, bounds)
        assertEquals(120, bounds.outWidth)
        assertEquals(160, bounds.outHeight)
        // Upscale never happens: a small source stays its own size.
        assertTrue(bounds.outWidth <= 1920 && bounds.outHeight <= 1920)
    }

    @Test
    fun longEdgeIsScaledDownTo1920_neverUp() = runBlocking {
        val plain = ByteArrayOutputStream().use { out ->
            solidBitmap(2600, 1200).compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        val photo = (preprocess(plain, "image/jpeg") as
            DailyUpPhotoPreparation.Ready).photo
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, bounds)
        assertEquals(1920, bounds.outWidth)
        // Aspect ratio preserved (rounding-tolerant).
        assertTrue(bounds.outHeight in 884..888)
    }

    @Test
    fun unsupportedMime_rejected() = runBlocking {
        val result = preprocess(geotaggedJpeg(), "image/gif")
        val rejected = result as? DailyUpPhotoPreparation.Rejected
        assertEquals(DailyUpPhotoRejection.UNSUPPORTED, rejected?.reason)
    }

    @Test
    fun emptySource_rejected() = runBlocking {
        val result = preprocess(ByteArray(0), "image/jpeg")
        val rejected = result as? DailyUpPhotoPreparation.Rejected
        assertEquals(DailyUpPhotoRejection.INPUT_TOO_LARGE, rejected?.reason)
    }

    @Test
    fun garbageBytes_rejected_asDecodeFailure() = runBlocking {
        val result = preprocess("definitely not an image".toByteArray(), "image/jpeg")
        val rejected = result as? DailyUpPhotoPreparation.Rejected
        assertEquals(DailyUpPhotoRejection.DECODE_FAILED, rejected?.reason)
    }

    @Test
    fun alphaSource_publishedAsPng() = runBlocking {
        val plain = ByteArrayOutputStream().use { out ->
            solidBitmap(80, 80).copy(Bitmap.Config.ARGB_8888, false).also {
                it.setHasAlpha(true)
            }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val ready = preprocess(plain, "image/png") as? DailyUpPhotoPreparation.Ready
        assertTrue(ready != null)
        assertEquals("image/png", ready!!.photo.mimeType)
    }
}
