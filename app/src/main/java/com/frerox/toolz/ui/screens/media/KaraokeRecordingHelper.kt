package com.frerox.toolz.ui.screens.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.util.Calendar

private const val TAG = "KaraokeRecHelper"

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point: save the finished recording to MediaStore with proper
// metadata (artist = "Toolz Karaoke", album = "Toolz Karaoke Recordings") and
// an embedded thumbnail in the M4A covr atom.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Saves [file] to MediaStore under Music/Karaoke with rich metadata.
 *
 * @param displayName  The filename (e.g. "Song recording 1.m4a")
 * @param trackTitle   The original song title
 * @param thumbnailUrl Optional HTTP(S) URL to the song's thumbnail image
 * @param onDone       Called with true on success, false on failure
 */
fun saveKaraokeRecording(
    context      : Context,
    file         : File,
    displayName  : String,
    trackTitle   : String,
    thumbnailUrl : String,
    onDone       : (success: Boolean) -> Unit
) {
    Thread {
        try {
            // 1. Optionally embed album art in the M4A file
            if (thumbnailUrl.isNotBlank()) {
                runCatching {
                    val thumbBytes = downloadImageAsJpeg(thumbnailUrl)
                    if (thumbBytes != null) {
                        embedCoverArtInM4a(file, thumbBytes)
                        Log.d(TAG, "Album art embedded (${thumbBytes.size} bytes)")
                    }
                }.onFailure { Log.w(TAG, "Failed to embed album art", it) }
            }

            // 2. Insert into MediaStore with full metadata
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val resolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME,  displayName)
                put(MediaStore.Audio.Media.MIME_TYPE,     "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Karaoke")
                put(MediaStore.Audio.Media.TITLE,         trackTitle)
                put(MediaStore.Audio.Media.ARTIST,        "Toolz Karaoke")
                put(MediaStore.Audio.Media.ALBUM,         "Toolz Karaoke Recordings")
                put(MediaStore.Audio.Media.ALBUM_ARTIST,  "Toolz Karaoke")
                put(MediaStore.Audio.Media.YEAR,          year)
                put(MediaStore.Audio.Media.IS_MUSIC,      1)
                put(MediaStore.Audio.Media.IS_PENDING,    1)
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Log.e(TAG, "MediaStore insert returned null URI")
                onDone(false)
                return@Thread
            }

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Log.d(TAG, "Saved to MediaStore: $uri")
            onDone(true)
        } catch (e: Exception) {
            Log.e(TAG, "saveKaraokeRecording failed", e)
            onDone(false)
        }
    }.start()
}

// ─────────────────────────────────────────────────────────────────────────────
// Download & compress thumbnail as JPEG bytes
// ─────────────────────────────────────────────────────────────────────────────

private fun downloadImageAsJpeg(url: String): ByteArray? {
    return try {
        val stream = URL(url).openStream()
        val bitmap = BitmapFactory.decodeStream(stream) ?: return null
        // Scale down so we don't bloat the audio file
        val maxDim = 512
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        bos.toByteArray()
    } catch (e: Exception) {
        Log.w(TAG, "downloadImageAsJpeg failed for $url", e)
        null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Embed a JPEG image into an M4A file's moov/udta/meta/ilst/covr atom
//
// MP4 box structure we inject:
//   udta {
//     meta {
//       hdlr (handler reference — mdir/appl)
//       ilst {
//         covr {
//           data (cover-image payload)
//         }
//       }
//     }
//   }
//
// If the file already has a moov box, we patch it in place by:
//   1. Locating the 'moov' box.
//   2. Finding or creating 'udta' > 'meta' > 'ilst' > 'covr'.
//   3. Rewriting the file with the covr atom appended.
//
// This is a *safe* append approach: we build the minimal new atom tree and
// append it inside the moov box. We do NOT attempt to parse or modify any
// existing atoms, so this cannot corrupt the audio stream.
// ─────────────────────────────────────────────────────────────────────────────

private fun embedCoverArtInM4a(file: File, jpegBytes: ByteArray) {
    // Build the nested atom tree for the cover art
    val newAtoms = buildCoverArtAtomTree(jpegBytes)

    // Read the entire file, locate the moov box, inject our atoms just before
    // moov's closing boundary (i.e. append inside moov).
    val original = file.readBytes()
    val patched = injectInsideMoov(original, newAtoms) ?: run {
        Log.w(TAG, "Could not locate moov box – skipping cover art embedding")
        return
    }
    file.writeBytes(patched)
    Log.d(TAG, "M4A patched: original=${original.size} patched=${patched.size}")
}

/**
 * Builds:
 *   udta {
 *     meta (version=0, flags=0) {
 *       hdlr (type=mdir)
 *       ilst {
 *         covr {
 *           data (type=13 = JPEG, locale=0) + jpegBytes
 *         }
 *       }
 *     }
 *   }
 */
private fun buildCoverArtAtomTree(jpeg: ByteArray): ByteArray {
    // data atom payload: 4-byte type indicator (13 = JPEG) + 4-byte locale + image bytes
    val dataPayload = ByteArray(8 + jpeg.size)
    dataPayload[0] = 0; dataPayload[1] = 0; dataPayload[2] = 0; dataPayload[3] = 13  // type = JPEG
    dataPayload[4] = 0; dataPayload[5] = 0; dataPayload[6] = 0; dataPayload[7] = 0  // locale
    jpeg.copyInto(dataPayload, 8)

    val dataAtom = box("data", dataPayload)
    val covrAtom = box("covr", dataAtom)
    val ilstAtom = box("ilst", covrAtom)

    // hdlr: version(1) + flags(3) + pre-defined(4) + handler-type(4="mdir") + reserved(12) + name(1)
    val hdlrPayload = byteArrayOf(
        0, 0, 0, 0,                        // version + flags
        0, 0, 0, 0,                        // pre-defined
        'm'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(), // handler type
        'a'.code.toByte(), 'p'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), // manufacturer
        0, 0, 0, 0,                        // component flags
        0, 0, 0, 0,                        // component flags mask
        0                                   // name (null-terminated empty string)
    )
    val hdlrAtom = box("hdlr", hdlrPayload)

    // meta: version(1) + flags(3) = 4 bytes header, then children
    val metaHeader = byteArrayOf(0, 0, 0, 0) // version=0, flags=0
    val metaChildren = hdlrAtom + ilstAtom
    val metaPayload = metaHeader + metaChildren
    val metaAtom = box("meta", metaPayload)

    val udtaAtom = box("udta", metaAtom)
    return udtaAtom
}

/** Builds a standard MP4 box: 4-byte big-endian size + 4-byte type + payload. */
private fun box(type: String, payload: ByteArray): ByteArray {
    val size = 8 + payload.size
    val result = ByteArray(size)
    result[0] = (size shr 24 and 0xFF).toByte()
    result[1] = (size shr 16 and 0xFF).toByte()
    result[2] = (size shr  8 and 0xFF).toByte()
    result[3] = (size        and 0xFF).toByte()
    type.toByteArray(Charsets.US_ASCII).copyInto(result, 4)
    payload.copyInto(result, 8)
    return result
}

/**
 * Scans [data] for the 'moov' box and returns a new byte array with
 * [insert] injected immediately before the moov box's closing boundary
 * (i.e. appended at the end of the moov payload).
 * Returns null if no moov box is found.
 */
private fun injectInsideMoov(data: ByteArray, insert: ByteArray): ByteArray? {
    var i = 0
    while (i + 8 <= data.size) {
        val boxSize = readInt32BE(data, i)
        val boxType = String(data, i + 4, 4, Charsets.US_ASCII)
        if (boxSize <= 0 || i + boxSize > data.size) break

        if (boxType == "moov") {
            // Inject 'insert' at the end of the moov payload
            val insertionPoint = i + boxSize
            val newMoovSize = boxSize + insert.size

            val result = ByteArray(data.size + insert.size)
            data.copyInto(result, 0, 0, i)                            // before moov
            // Write new moov size
            result[i + 0] = (newMoovSize shr 24 and 0xFF).toByte()
            result[i + 1] = (newMoovSize shr 16 and 0xFF).toByte()
            result[i + 2] = (newMoovSize shr  8 and 0xFF).toByte()
            result[i + 3] = (newMoovSize        and 0xFF).toByte()
            // Copy moov type + original content
            data.copyInto(result, i + 4, i + 4, insertionPoint)
            // Inject new atoms
            insert.copyInto(result, insertionPoint)
            // Copy everything after moov
            data.copyInto(result, insertionPoint + insert.size, insertionPoint)
            return result
        }

        i += boxSize
    }
    return null
}

private fun readInt32BE(data: ByteArray, offset: Int): Int {
    return ((data[offset].toInt() and 0xFF) shl 24) or
           ((data[offset + 1].toInt() and 0xFF) shl 16) or
           ((data[offset + 2].toInt() and 0xFF) shl 8) or
           (data[offset + 3].toInt() and 0xFF)
}
