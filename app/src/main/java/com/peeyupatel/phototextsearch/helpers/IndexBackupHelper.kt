package com.peeyupatel.phototextsearch.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.peeyupatel.phototextsearch.database.ClassificationDatabase
import com.peeyupatel.phototextsearch.database.MediaDatabase
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "INDEX_BACKUP"

/**
 * Backs up and restores the OCR index (MediaDatabase + ClassificationDatabase) to/from a
 * user-chosen file outside the app's own storage, via the Storage Access Framework -- unlike
 * anything in internal app storage, a file the user saves to Downloads/Drive/an SD card survives
 * the app being uninstalled, which internal storage never does.
 */
object IndexBackupHelper {
    private const val MEDIA_DB_ENTRY = "media-database.db"
    private const val CLASSIFICATION_DB_ENTRY = "photo-classification-database.db"

    /**
     * Snapshots both Room databases via SQLite's VACUUM INTO, which produces a single,
     * consistent file regardless of WAL/journal state -- both databases default to WAL journal
     * mode, so without this a raw file copy would need to separately checkpoint and copy the
     * -wal/-shm side files too, or risk backing up an inconsistent/incomplete snapshot.
     */
    suspend fun backupIndex(context: Context, destinationUri: Uri): Boolean {
        val tempDir = File(context.cacheDir, "index_backup_tmp").apply { mkdirs() }
        val mediaSnapshot = File(tempDir, MEDIA_DB_ENTRY)
        val classificationSnapshot = File(tempDir, CLASSIFICATION_DB_ENTRY)

        return try {
            // VACUUM INTO fails if the target file already exists (e.g. a stale snapshot left
            // over from a previous failed attempt).
            mediaSnapshot.delete()
            classificationSnapshot.delete()

            MediaDatabase.getInstance(context).openHelper.writableDatabase
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

            opened
        } catch (e: Exception) {
            Log.e(TAG, "Index backup failed", e)
            false
        } finally {
            mediaSnapshot.delete()
            classificationSnapshot.delete()
        }
    }

    /**
     * Restores both Room databases from a zip previously created by [backupIndex]. Closes both
     * database singletons first -- Room/SQLite must not have a database open while its
     * underlying file is replaced -- and removes any stale -wal/-shm/-journal files so Room
     * doesn't try to replay a journal that no longer matches the swapped-in database content.
     * The singletons re-open lazily the next time anything calls getInstance().
     */
    suspend fun restoreIndex(context: Context, sourceUri: Uri): Boolean {
        val tempDir = File(context.cacheDir, "index_restore_tmp").apply { mkdirs() }

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
                Log.e(TAG, "Backup file is missing or doesn't contain both database entries")
                return false
            }

            MediaDatabase.closeAndReset()
            ClassificationDatabase.closeAndReset()

            replaceDatabaseFile(context, "media-database", media)
            replaceDatabaseFile(context, "photo-classification-database", classification)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Index restore failed", e)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun replaceDatabaseFile(context: Context, dbName: String, restoredFile: File) {
        val dbFile = context.getDatabasePath(dbName)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
        dbFile.parentFile?.mkdirs()
        restoredFile.copyTo(dbFile, overwrite = true)
    }
}
