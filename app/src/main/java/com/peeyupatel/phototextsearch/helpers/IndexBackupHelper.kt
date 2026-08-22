package com.peeyupatel.phototextsearch.helpers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
import com.peeyupatel.phototextsearch.ocr.DevanagariOcrManager
import com.peeyupatel.phototextsearch.ocr.OcrManager
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "INDEX_BACKUP"

/** Result of a backup/restore attempt, carrying a failure reason instead of collapsing every
 * cause (disk full, permission denial, a race with OCR indexing, an incompatible schema
 * version...) into an undifferentiated `false`. */
sealed class BackupResult {
    data object Success : BackupResult()
    data class Failure(val reason: String) : BackupResult()
}

/**
 * Backs up and restores the OCR index (MediaDatabase + ClassificationDatabase) to/from a
 * user-chosen file outside the app's own storage, via the Storage Access Framework -- unlike
 * anything in internal app storage, a file the user saves to Downloads/Drive/an SD card survives
 * the app being uninstalled, which internal storage never does.
 */
object IndexBackupHelper {
    private const val MEDIA_DB_ENTRY = "media-database.db"
    private const val CLASSIFICATION_DB_ENTRY = "photo-classification-database.db"

    // Must be kept in sync with MediaDatabase's/ClassificationDatabase's own `@Database(version
    // = ...)` -- restoring a backup taken on a different schema version than the installed app
    // expects would otherwise report success and only crash later, the next time Room opens it.
    private const val MEDIA_DB_SCHEMA_VERSION = 10
    private const val CLASSIFICATION_DB_SCHEMA_VERSION = 6

    /**
     * Snapshots both Room databases via SQLite's VACUUM INTO, which produces a single,
     * consistent file regardless of WAL/journal state -- both databases default to WAL journal
     * mode, so without this a raw file copy would need to separately checkpoint and copy the
     * -wal/-shm side files too, or risk backing up an inconsistent/incomplete snapshot.
     */
    suspend fun backupIndex(context: Context, destinationUri: Uri): BackupResult {
        val tempDir = File(context.cacheDir, "index_backup_tmp").apply { mkdirs() }
        val mediaSnapshot = File(tempDir, MEDIA_DB_ENTRY)
        val classificationSnapshot = File(tempDir, CLASSIFICATION_DB_ENTRY)

        val mediaDatabase = MediaDatabase.getInstance(context)
        val ocrManager = OcrManager(context, mediaDatabase)
        val devanagariOcrManager = DevanagariOcrManager(context, mediaDatabase)
        val wasOcrProcessing = mediaDatabase.ocrProgressDao().getProgress()?.isProcessing == true
        val wasDevanagariProcessing =
            mediaDatabase.devanagariOcrProgressDao().getProgress()?.isProcessing == true

        // Pause both OCR workers for the duration of the snapshot. VACUUM INTO on a single
        // database is itself safe against concurrent writers, but MediaDatabase and
        // ClassificationDatabase are snapshotted separately, one after another -- a worker
        // writing an OcrTextEntity to the former and a PhotoClassificationEntity to the latter
        // in between the two calls would otherwise produce a backup where the two files
        // disagree with each other.
        if (wasOcrProcessing) ocrManager.pauseProcessing()
        if (wasDevanagariProcessing) devanagariOcrManager.pauseProcessing()

        return try {
            // VACUUM INTO fails if the target file already exists (e.g. a stale snapshot left
            // over from a previous failed attempt).
            mediaSnapshot.delete()
            classificationSnapshot.delete()

            mediaDatabase.openHelper.writableDatabase
                .execSQL("VACUUM INTO '${mediaSnapshot.absolutePath}'")
            ClassificationDatabase.getInstance(context).openHelper.writableDatabase
                .execSQL("VACUUM INTO '${classificationSnapshot.absolutePath}'")

            val opened = context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(Deflater.BEST_COMPRESSION)
                    for (file in listOf(mediaSnapshot, classificationSnapshot)) {
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                true
            } ?: false

            if (opened) BackupResult.Success
            else BackupResult.Failure("Could not open the destination file for writing")
        } catch (e: Throwable) {
            Log.e(TAG, "Index backup failed", e)
            BackupResult.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            mediaSnapshot.delete()
            classificationSnapshot.delete()
            if (wasOcrProcessing) ocrManager.resumeProcessing()
            if (wasDevanagariProcessing) devanagariOcrManager.resumeProcessing()
        }
    }

    /**
     * Restores both Room databases from a zip previously created by [backupIndex]. Closes both
     * database singletons first -- Room/SQLite must not have a database open while its
     * underlying file is replaced -- and removes any stale -wal/-shm/-journal files so Room
     * doesn't try to replay a journal that no longer matches the swapped-in database content.
     * The singletons re-open lazily the next time anything calls getInstance().
     */
    suspend fun restoreIndex(context: Context, sourceUri: Uri): BackupResult {
        val tempDir = File(context.cacheDir, "index_restore_tmp").apply { mkdirs() }

        val mediaDatabase = MediaDatabase.getInstance(context)
        val ocrManager = OcrManager(context, mediaDatabase)
        val devanagariOcrManager = DevanagariOcrManager(context, mediaDatabase)
        val wasOcrProcessing = mediaDatabase.ocrProgressDao().getProgress()?.isProcessing == true
        val wasDevanagariProcessing =
            mediaDatabase.devanagariOcrProgressDao().getProgress()?.isProcessing == true
        // Set once the live databases are actually closed/swapped -- past that point the
        // OcrManager/DevanagariOcrManager instances above hold a now-stale MediaDatabase
        // reference, so resuming against them would just throw. A restart is required past
        // this point regardless (the UI already says so on success; a failure this late means
        // the on-disk file may be a partial write, so restart is the safest recovery either way).
        var swappedLiveDb = false

        if (wasOcrProcessing) ocrManager.pauseProcessing()
        if (wasDevanagariProcessing) devanagariOcrManager.pauseProcessing()

        return try {
            var mediaRestored: File? = null
            var classificationRestored: File? = null

            val opened = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, File(entry.name).name)
                        outFile.outputStream().use { zip.copyTo(it) }
                        when (outFile.name) {
                            MEDIA_DB_ENTRY -> mediaRestored = outFile
                            CLASSIFICATION_DB_ENTRY -> classificationRestored = outFile
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                true
            } ?: false

            val media = mediaRestored
            val classification = classificationRestored
            if (!opened || media == null || classification == null) {
                return BackupResult.Failure(
                    "Backup file is missing or doesn't contain both database entries"
                )
            }

            // Reject an incompatible schema version before touching the live databases at all --
            // see MEDIA_DB_SCHEMA_VERSION/CLASSIFICATION_DB_SCHEMA_VERSION.
            val mediaVersion = readSqliteUserVersion(media)
            val classificationVersion = readSqliteUserVersion(classification)
            if (mediaVersion != MEDIA_DB_SCHEMA_VERSION ||
                classificationVersion != CLASSIFICATION_DB_SCHEMA_VERSION
            ) {
                return BackupResult.Failure(
                    "Backup is from an incompatible app version (schema " +
                        "$mediaVersion/$classificationVersion, expected " +
                        "$MEDIA_DB_SCHEMA_VERSION/$CLASSIFICATION_DB_SCHEMA_VERSION)"
                )
            }

            MediaDatabase.closeAndReset()
            ClassificationDatabase.closeAndReset()
            swappedLiveDb = true

            replaceDatabaseFile(context, "media-database", media)
            replaceDatabaseFile(context, "photo-classification-database", classification)

            BackupResult.Success
        } catch (e: Throwable) {
            Log.e(TAG, "Index restore failed", e)
            BackupResult.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            tempDir.deleteRecursively()
            if (!swappedLiveDb) {
                if (wasOcrProcessing) ocrManager.resumeProcessing()
                if (wasDevanagariProcessing) devanagariOcrManager.resumeProcessing()
            }
        }
    }

    private fun readSqliteUserVersion(dbFile: File): Int {
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { it.version }
    }

    private fun replaceDatabaseFile(context: Context, dbName: String, restoredFile: File) {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Copy into a temp file in the same directory first (same filesystem, so the final
        // rename below is atomic) instead of overwriting dbFile directly. If the copy is
        // interrupted partway -- an I/O error, disk full, the app getting killed mid-restore --
        // the live database is untouched and the restore just fails, instead of leaving a
        // truncated, unrecoverable database behind with its -wal/-shm siblings already deleted.
        val tempFile = File(dbFile.parentFile, "$dbName.restore-tmp")
        try {
            restoredFile.copyTo(tempFile, overwrite = true)

            // Only now that a complete copy exists do we touch the live database: the old
            // WAL/SHM/journal siblings describe the database being replaced, not the new one.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()

            if (!tempFile.renameTo(dbFile)) {
                throw IOException("Failed to move restored $dbName into place")
            }
        } finally {
            tempFile.delete()
        }
    }
}
