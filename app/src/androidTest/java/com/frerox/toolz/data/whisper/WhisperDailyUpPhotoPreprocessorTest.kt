package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhisperDailyUpPhotoPreprocessorTest {

    @Test
    fun geotaggedRotatedJpegIsUprightBoundedAndMetadataFree() = runBlocking {
        val source = geotaggedRotatedJpeg()

        val result = preprocessDailyUpPhoto(
            InstrumentationRegistry.getInstrumentation().targetContext,
            source,
            "image/jpeg",
        )

        val photo = (result as DailyUpPhotoPreparation.Ready).photo
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, dimensions)
        val exif = ExifInterface(photo.bytes.inputStream())
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals(1920, dimensions.outHeight)
        assertEquals(960, dimensions.outWidth)
        assertTrue(maxOf(dimensions.outWidth, dimensions.outHeight) <= 1920)
        assertTrue(photo.bytes.size <= WhisperImageCipherTransport.MAX_CIPHER_BYTES - 16)
        assertEquals(null, exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(null, exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals(null, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(null, exif.getAttribute(ExifInterface.TAG_ORIENTATION))
    }

    @Test
    fun corruptBytesNeverProduceAnUploadableDailyUpPhoto() = runBlocking {
        val result = preprocessDailyUpPhoto(
            InstrumentationRegistry.getInstrumentation().targetContext,
            byteArrayOf(1, 2, 3, 4),
            "image/jpeg",
        )

        assertTrue(result is DailyUpPhotoPreparation.Rejected)
        assertFalse(result is DailyUpPhotoPreparation.Ready)
    }

    private fun geotaggedRotatedJpeg(): ByteArray {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("daily-up-geotag", ".jpg", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            try {
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                }
            } finally {
                bitmap.recycle()
            }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,46/1,30/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "122/1,25/1,10/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:18 12:00:00")
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }
}
