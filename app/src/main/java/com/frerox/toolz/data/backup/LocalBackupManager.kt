/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.ai.AiChat
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.catalog.CatalogSearchEntry
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.crypto.CryptoHistoryEntry
import com.frerox.toolz.data.focus.AppLimit
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.data.pdf.PdfAnnotation
import com.frerox.toolz.data.pdf.PdfMetadata
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.util.security.KeyManager
import com.squareup.moshi.JsonAdapter
import com.frerox.toolz.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val aiSettingsManager: AiSettingsManager,
    private val moshi: Moshi
) {
    private val manifestAdapter = moshi.adapter(ToolzBackupManifest::class.java)
    private val stringMapAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private inline fun <reified T> listAdapter(): JsonAdapter<List<T>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, T::class.java))

    suspend fun exportReusableBackup(
        reason: String = "manual",
        items: Set<BackupItem> = BackupItem.entries.toSet()
    ): BackupExportResult = withContext(Dispatchers.IO) {
        _progress.value = context.getString(R.string.st_Backup_Progress_Preparing)
        checkpointDatabase()

        val stagingDir = File(context.cacheDir, "toolz_backup_staging").apply { mkdirs() }
        val fileName = "toolz_backup_${System.currentTimeMillis()}.tzbk"
        val tempZip = File(stagingDir, "$fileName.partial").apply {
            if (exists()) delete()
        }
        val entryHashes = linkedMapOf<String, String>()

        ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
            // 1. Security (Always included if any encryption needed)
            addTextEntry(
                zip = zip,
                path = "security/sqlcipher_passphrase.txt",
                text = KeyManager.getOrCreateMasterKeyString(context),
                entryHashes = entryHashes
            )

            // 2. AI Settings
            if (items.contains(BackupItem.AI_KEYS)) {
                addTextEntry(
                    zip = zip,
                    path = "ai_settings/portable.json",
                    text = stringMapAdapter.toJson(aiSettingsManager.exportPortableSettings()),
                    entryHashes = entryHashes
                )
            }

            // 3. Database Items (Selective)
            exportDatabaseItems(zip, items, entryHashes)

            // 4. Files & Settings (Selective)
            if (items.contains(BackupItem.SETTINGS)) {
                addDirectory(
                    zip = zip,
                    source = File(context.filesDir, "datastore"),
                    archivePrefix = "datastore",
                    entryHashes = entryHashes
                )
                addDirectory(
                    zip = zip,
                    source = File(context.dataDir, "shared_prefs"),
                    archivePrefix = "shared_prefs",
                    entryHashes = entryHashes,
                    excludedNames = ENCRYPTED_PREF_FILES
                )
            }

            if (items.contains(BackupItem.OTHERS)) {
                addDirectory(
                    zip = zip,
                    source = context.filesDir,
                    archivePrefix = "files",
                    entryHashes = entryHashes,
                    excludedTopLevelNames = setOf("datastore")
                )
                addDirectory(zip, context.noBackupFilesDir, "no_backup", entryHashes)
                context.getExternalFilesDir(null)?.let {
                    addDirectory(zip, it, "external_files/primary", entryHashes)
                }
            }

            val manifest = ToolzBackupManifest(
                payloadFormatVersion = PAYLOAD_FORMAT_VERSION,
                appPackageName = context.packageName,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                appVersionName = BuildConfig.VERSION_NAME,
                createdAtMillis = System.currentTimeMillis(),
                androidSdk = Build.VERSION.SDK_INT,
                databaseName = DATABASE_NAME,
                databaseSchemaVersion = AppDatabase::class.java.getAnnotation(androidx.room.Database::class.java)?.version ?: -1,
                entryHashes = entryHashes,
                includedItems = items,
                notes = listOf(
                    "reason=$reason",
                    "Selective backup enabled.",
                    "EncryptedSharedPreferences are exported as portable decrypted sections when needed."
                )
            )
            addTextEntry(zip, "manifest.json", manifestAdapter.toJson(manifest), entryHashes = null)
        }

        verifyZip(tempZip)
        val payloadHash = sha256(tempZip)
        val destination = publishToDocuments(tempZip, fileName)
        tempZip.delete()

        val pfd = context.contentResolver.openFileDescriptor(destination, "r")
        val size = pfd?.use { it.statSize } ?: 0L

        BackupExportResult(
            uri = destination,
            fileName = fileName,
            sha256 = payloadHash,
            byteCount = size,
            entryCount = entryHashes.size
        )
    }

    private suspend fun exportDatabaseItems(
        zip: ZipOutputStream,
        items: Set<BackupItem>,
        entryHashes: MutableMap<String, String>
    ) {
        if (items.contains(BackupItem.NOTES)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Notes)
            addTextEntry(zip, "data/notes.json", listAdapter<Note>().toJson(database.noteDao().getAllNotesSync()), entryHashes)
        }
        if (items.contains(BackupItem.TASKS)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Tasks)
            addTextEntry(zip, "data/tasks.json", listAdapter<TaskEntry>().toJson(database.taskDao().getAllTasksSync()), entryHashes)
        }
        if (items.contains(BackupItem.AI_HISTORY)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_AiHistory)
            addTextEntry(zip, "data/ai_chats.json", listAdapter<AiChat>().toJson(database.aiDao().getAllChatsSync()), entryHashes)
            addTextEntry(zip, "data/ai_messages.json", listAdapter<AiMessage>().toJson(database.aiDao().getAllMessagesSync()), entryHashes)
        }
        if (items.contains(BackupItem.PASSWORDS)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Passwords)
            addTextEntry(zip, "data/passwords.json", listAdapter<PasswordEntity>().toJson(database.passwordDao().getAllPasswordsSync()), entryHashes)
        }
        if (items.contains(BackupItem.SEARCH_HISTORY)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_SearchHistory)
            addTextEntry(zip, "data/search_history.json", listAdapter<SearchHistoryEntry>().toJson(database.searchDao().getAllHistorySync()), entryHashes)
            addTextEntry(zip, "data/bookmarks.json", listAdapter<BookmarkEntry>().toJson(database.searchDao().getAllBookmarksSync()), entryHashes)
            addTextEntry(zip, "data/quick_links.json", listAdapter<QuickLinkEntry>().toJson(database.searchDao().getAllQuickLinksSync()), entryHashes)
        }
        if (items.contains(BackupItem.NOTIFICATIONS)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Notifications)
            addTextEntry(zip, "data/notifications.json", listAdapter<NotificationEntry>().toJson(database.notificationDao().getAllNotificationsSync()), entryHashes)
        }
        if (items.contains(BackupItem.CALENDAR)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Calendar)
            addTextEntry(zip, "data/events.json", listAdapter<EventEntry>().toJson(database.eventDao().getAllEventsSync()), entryHashes)
        }
        if (items.contains(BackupItem.CLIPBOARD)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Clipboard)
            addTextEntry(zip, "data/clipboard.json", listAdapter<ClipboardEntry>().toJson(database.clipboardDao().getAllEntriesSync()), entryHashes)
        }
        if (items.contains(BackupItem.STEPS)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Steps)
            addTextEntry(zip, "data/steps.json", listAdapter<StepEntry>().toJson(database.stepDao().getAllStepsSync()), entryHashes)
        }
        if (items.contains(BackupItem.MATH_HISTORY)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Math)
            addTextEntry(zip, "data/math_history.json", listAdapter<MathHistory>().toJson(database.mathHistoryDao().getAllHistorySync()), entryHashes)
        }
        if (items.contains(BackupItem.PDF_METADATA)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Pdf)
            addTextEntry(zip, "data/pdf_metadata.json", listAdapter<PdfMetadata>().toJson(database.pdfMetadataDao().getAllMetadataSync()), entryHashes)
            addTextEntry(zip, "data/pdf_annotations.json", listAdapter<PdfAnnotation>().toJson(database.pdfAnnotationDao().getAllAnnotationsSync()), entryHashes)
        }
        if (items.contains(BackupItem.CATALOG_DATA)) {
            _progress.value = "Music Catalog Data"
            addTextEntry(zip, "data/catalog_search.json", listAdapter<CatalogSearchEntry>().toJson(database.catalogSearchDao().getAllSearchesSync()), entryHashes)
        }
        if (items.contains(BackupItem.OTHERS)) {
            _progress.value = context.getString(R.string.st_Backup_Progress_Others)
            addTextEntry(zip, "data/app_limits.json", listAdapter<AppLimit>().toJson(database.appLimitDao().getAllLimitsSync()), entryHashes)
            addTextEntry(zip, "data/caffeinate.json", listAdapter<CaffeinateApp>().toJson(database.caffeinateDao().getAllAppsSync()), entryHashes)
            addTextEntry(zip, "data/crypto_history.json", listAdapter<CryptoHistoryEntry>().toJson(database.cryptoDao().getAllHistorySync()), entryHashes)
            addTextEntry(zip, "data/music_tracks.json", listAdapter<MusicTrack>().toJson(database.musicDao().getAllTracksSync()), entryHashes)
            addTextEntry(zip, "data/playlists.json", listAdapter<Playlist>().toJson(database.musicDao().getAllPlaylistsSync()), entryHashes)
        }
        _progress.value = context.getString(R.string.st_Backup_Progress_Finalizing)
    }

    suspend fun importReusableBackup(
        uri: Uri,
        itemsToRestore: Set<BackupItem> = BackupItem.entries.toSet()
    ): BackupImportResult = withContext(Dispatchers.IO) {
        _progress.value = context.getString(R.string.st_Backup_Progress_Opening)
        val stagingFile = File(context.cacheDir, "toolz_restore_${System.currentTimeMillis()}.tzbk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(stagingFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Unable to open backup: $uri")

        val manifest = readAndVerifyManifest(stagingFile)
        require(manifest.appPackageName == context.packageName) {
            "Backup package ${manifest.appPackageName} does not match ${context.packageName}"
        }
        require(manifest.payloadFormatVersion <= PAYLOAD_FORMAT_VERSION) {
            "Unsupported future backup format ${manifest.payloadFormatVersion}"
        }

        var restoredEntries = 0

        ZipFile(stagingFile).use { zipFile ->
            val passphrase = zipFile.getInputStream(zipFile.getEntry("security/sqlcipher_passphrase.txt")).bufferedReader().use { it.readText() }
            KeyManager.restoreMasterKey(context, passphrase.trim())

            zipFile.entries().asSequence().forEach { entry ->
                if (entry.isDirectory || entry.name == "manifest.json" || entry.name == "security/sqlcipher_passphrase.txt") {
                    return@forEach
                }
                
                when {
                    entry.name == "ai_settings/portable.json" && itemsToRestore.contains(BackupItem.AI_KEYS) -> {
                        val json = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
                        aiSettingsManager.importPortableSettings(stringMapAdapter.fromJson(json).orEmpty())
                        restoredEntries++
                    }
                    entry.name.startsWith("data/") -> {
                        if (restoreDatabaseItem(zipFile, entry, itemsToRestore)) {
                            restoredEntries++
                        }
                    }
                    entry.name.startsWith("datastore/") && itemsToRestore.contains(BackupItem.SETTINGS) -> {
                        restoreEntry(zipFile, entry.name, File(context.filesDir, "datastore"), "datastore")
                        restoredEntries++
                    }
                    entry.name.startsWith("shared_prefs/") && itemsToRestore.contains(BackupItem.SETTINGS) -> {
                        restoreEntry(zipFile, entry.name, File(context.dataDir, "shared_prefs"), "shared_prefs")
                        restoredEntries++
                    }
                    entry.name.startsWith("files/") && itemsToRestore.contains(BackupItem.OTHERS) -> {
                        restoreEntry(zipFile, entry.name, context.filesDir, "files")
                        restoredEntries++
                    }
                    entry.name.startsWith("no_backup/") && itemsToRestore.contains(BackupItem.OTHERS) -> {
                        restoreEntry(zipFile, entry.name, context.noBackupFilesDir, "no_backup")
                        restoredEntries++
                    }
                    entry.name.startsWith("external_files/primary/") && itemsToRestore.contains(BackupItem.OTHERS) -> {
                        context.getExternalFilesDir(null)?.let { target ->
                            restoreEntry(zipFile, entry.name, target, "external_files/primary")
                            restoredEntries++
                        }
                    }
                }
            }
        }

        stagingFile.delete()
        BackupImportResult(manifest = manifest, restoredEntries = restoredEntries)
    }

    private suspend fun restoreDatabaseItem(
        zipFile: ZipFile,
        entry: ZipEntry,
        itemsToRestore: Set<BackupItem>
    ): Boolean {
        val json = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
        _progress.value = context.getString(R.string.st_Backup_Progress_Restoring, entry.name.substringAfterLast("/").substringBefore("."))
        return when (entry.name) {
            "data/notes.json" -> if (itemsToRestore.contains(BackupItem.NOTES)) {
                database.noteDao().insertNotes(listAdapter<Note>().fromJson(json).orEmpty())
                true
            } else false
            "data/tasks.json" -> if (itemsToRestore.contains(BackupItem.TASKS)) {
                database.taskDao().insertTasks(listAdapter<TaskEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/ai_chats.json" -> if (itemsToRestore.contains(BackupItem.AI_HISTORY)) {
                database.aiDao().insertChats(listAdapter<AiChat>().fromJson(json).orEmpty())
                true
            } else false
            "data/ai_messages.json" -> if (itemsToRestore.contains(BackupItem.AI_HISTORY)) {
                database.aiDao().insertMessages(listAdapter<AiMessage>().fromJson(json).orEmpty())
                true
            } else false
            "data/passwords.json" -> if (itemsToRestore.contains(BackupItem.PASSWORDS)) {
                database.passwordDao().insertPasswords(listAdapter<PasswordEntity>().fromJson(json).orEmpty())
                true
            } else false
            "data/search_history.json" -> if (itemsToRestore.contains(BackupItem.SEARCH_HISTORY)) {
                database.searchDao().insertHistories(listAdapter<SearchHistoryEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/bookmarks.json" -> if (itemsToRestore.contains(BackupItem.SEARCH_HISTORY)) {
                database.searchDao().insertBookmarks(listAdapter<BookmarkEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/quick_links.json" -> if (itemsToRestore.contains(BackupItem.SEARCH_HISTORY)) {
                database.searchDao().insertQuickLinks(listAdapter<QuickLinkEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/notifications.json" -> if (itemsToRestore.contains(BackupItem.NOTIFICATIONS)) {
                database.notificationDao().insertNotifications(listAdapter<NotificationEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/events.json" -> if (itemsToRestore.contains(BackupItem.CALENDAR)) {
                database.eventDao().insertEvents(listAdapter<EventEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/clipboard.json" -> if (itemsToRestore.contains(BackupItem.CLIPBOARD)) {
                database.clipboardDao().insertEntries(listAdapter<ClipboardEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/steps.json" -> if (itemsToRestore.contains(BackupItem.STEPS)) {
                database.stepDao().insertSteps(listAdapter<StepEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/math_history.json" -> if (itemsToRestore.contains(BackupItem.MATH_HISTORY)) {
                database.mathHistoryDao().insertHistories(listAdapter<MathHistory>().fromJson(json).orEmpty())
                true
            } else false
            "data/pdf_metadata.json" -> if (itemsToRestore.contains(BackupItem.PDF_METADATA)) {
                database.pdfMetadataDao().insertMetadataList(listAdapter<PdfMetadata>().fromJson(json).orEmpty())
                true
            } else false
            "data/pdf_annotations.json" -> if (itemsToRestore.contains(BackupItem.PDF_METADATA)) {
                database.pdfAnnotationDao().insertAnnotations(listAdapter<PdfAnnotation>().fromJson(json).orEmpty())
                true
            } else false
            "data/catalog_search.json" -> if (itemsToRestore.contains(BackupItem.CATALOG_DATA)) {
                database.catalogSearchDao().insertSearches(listAdapter<CatalogSearchEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/app_limits.json" -> if (itemsToRestore.contains(BackupItem.OTHERS)) {
                database.appLimitDao().insertLimits(listAdapter<AppLimit>().fromJson(json).orEmpty())
                true
            } else false
            "data/caffeinate.json" -> if (itemsToRestore.contains(BackupItem.OTHERS)) {
                database.caffeinateDao().insertApps(listAdapter<CaffeinateApp>().fromJson(json).orEmpty())
                true
            } else false
            "data/crypto_history.json" -> if (itemsToRestore.contains(BackupItem.OTHERS)) {
                database.cryptoDao().insertHistory(listAdapter<CryptoHistoryEntry>().fromJson(json).orEmpty())
                true
            } else false
            "data/music_tracks.json" -> if (itemsToRestore.contains(BackupItem.OTHERS)) {
                database.musicDao().insertTracks(listAdapter<MusicTrack>().fromJson(json).orEmpty())
                true
            } else false
            "data/playlists.json" -> if (itemsToRestore.contains(BackupItem.OTHERS)) {
                database.musicDao().insertPlaylists(listAdapter<Playlist>().fromJson(json).orEmpty())
                true
            } else false
            else -> false
        }
    }

    private fun checkpointDatabase() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
    }

    private fun addDirectory(
        zip: ZipOutputStream,
        source: File,
        archivePrefix: String,
        entryHashes: MutableMap<String, String>,
        excludedNames: Set<String> = emptySet(),
        excludedTopLevelNames: Set<String> = emptySet()
    ) {
        if (!source.exists()) return
        source.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.name in excludedNames }
            .filterNot { it.absolutePath.contains("${File.separator}cache${File.separator}") }
            .forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                if (relative.substringBefore('/') in excludedTopLevelNames) return@forEach
                addFileEntry(zip, file, "$archivePrefix/$relative", entryHashes)
            }
    }

    private fun addFileEntry(
        zip: ZipOutputStream,
        file: File,
        path: String,
        entryHashes: MutableMap<String, String>
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(path))
        DigestInputStream(FileInputStream(file), digest).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
        entryHashes[path] = digest.digest().toHex()
    }

    private fun addTextEntry(
        zip: ZipOutputStream,
        path: String,
        text: String,
        entryHashes: MutableMap<String, String>?
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
        entryHashes?.put(path, MessageDigest.getInstance("SHA-256").digest(bytes).toHex())
    }

    private fun publishToDocuments(source: File, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            // Broader MIME type for better visibility in generic pickers
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Toolz_Backups")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Could not create backup in Documents")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not open backup output stream")
            verifyZip(uri)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun verifyZip(uri: Uri) {
        val temp = File(context.cacheDir, "toolz_verify_${System.currentTimeMillis()}.tzbk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to verify backup at $uri")
        try {
            verifyZip(temp)
        } finally {
            temp.delete()
        }
    }

    private fun verifyZip(file: File) {
        ZipInputStream(FileInputStream(file)).use { zip ->
            while (zip.nextEntry != null) {
                zip.closeEntry()
            }
        }
    }

    private fun readAndVerifyManifest(file: File): ToolzBackupManifest {
        ZipFile(file).use { zipFile ->
            val manifestEntry = zipFile.getEntry("manifest.json") ?: error("Backup manifest missing")
            val manifestJson = zipFile.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifest = manifestAdapter.fromJson(manifestJson) ?: error("Backup manifest is invalid")
            manifest.entryHashes.forEach { (entryName, expectedHash) ->
                val entry = zipFile.getEntry(entryName) ?: error("Backup entry missing: $entryName")
                val actualHash = zipFile.getInputStream(entry).use { sha256(it) }
                require(actualHash == expectedHash) { "Backup entry hash mismatch: $entryName" }
            }
            return manifest
        }
    }

    private fun restoreEntry(zipFile: ZipFile, entryName: String, targetRoot: File, archivePrefix: String) {
        val relativeName = entryName.removePrefix("$archivePrefix/").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Invalid backup entry: $entryName")
        val target = resolveSafe(targetRoot, relativeName)
        target.parentFile?.mkdirs()
        zipFile.getInputStream(zipFile.getEntry(entryName)).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun resolveSafe(root: File, relativeName: String): File {
        val rootCanonical = root.canonicalFile
        val target = File(rootCanonical, relativeName).canonicalFile
        require(target.path == rootCanonical.path || target.path.startsWith(rootCanonical.path + File.separator)) {
            "Unsafe backup path: $relativeName"
        }
        return target
    }

    private fun sha256(file: File): String = FileInputStream(file).use { sha256(it) }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val PAYLOAD_FORMAT_VERSION = 1
        const val DATABASE_NAME = "toolz_db"

        private val ENCRYPTED_PREF_FILES = setOf(
            "toolz_vault_prefs.xml",
            "ai_settings.xml"
        )
    }
}
