package com.example.data.cloud

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local rolling backups. The phone remains the source of truth; these files are
 * the safety copy that can later be uploaded to Google Drive or handed to the
 * POS provider on a monthly visit.
 */
class CloudBackupManager(private val context: Context) {

    private val backupDir: File =
        File(context.filesDir, "backups").apply { mkdirs() }

    private val dbPath: File
        get() = context.getDatabasePath("kadepos_database")

    data class Result(
        val file: File?,
        val message: String
    )

    /** Copies the live Room database plus WAL/SHM into one timestamped zip. */
    @Synchronized
    fun createBackup(deviceName: String): Result {
        return runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val outFile = File(backupDir, "kadepos-backup-$stamp.zip")
            val sources = listOfNotNull(
                context.getDatabasePath("kadepos_database").takeIf { it.exists() }
            ) + listOfNotNull(
                context.getDatabasePath("kadepos_database-wal").takeIf { it.exists() },
                context.getDatabasePath("kadepos_database-shm").takeIf { it.exists() }
            )

            ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
                sources.forEach { source ->
                    zip.putNextEntry(ZipEntry(source.name))
                    source.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                val manifest = JSONObject().apply {
                    put("app", "KadePOS")
                    put("deviceName", deviceName)
                    put("createdAt", System.currentTimeMillis())
                    put("database", dbPath.name)
                    put("source", "cloud_backup_manager")
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            pruneOld(30)
            Result(outFile, "Backup saved")
        }.getOrElse {
            Result(null, it.message ?: "Backup failed")
        }
    }

    @Synchronized
    fun latestBackup(): File? = backupDir.listFiles()
        ?.filter { it.extension == "zip" }
        ?.maxByOrNull { it.lastModified() }

    @Synchronized
    fun listBackups(): List<File> =
        backupDir.listFiles()?.filter { it.extension == "zip" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    @Synchronized
    fun pruneOld(keep: Int) {
        backupDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    /**
     * The hash used by the hourly worker to decide whether there is anything new
     * to upload. If it matches the last uploaded hash, no network is used.
     */
    fun snapshotHash(): String {
        val files = listOfNotNull(
            context.getDatabasePath("kadepos_database").takeIf { it.exists() },
            context.getDatabasePath("kadepos_database-wal").takeIf { it.exists() }
        )
        val digest = MessageDigest.getInstance("SHA-256")
        files.sortedBy { it.name }.forEach { file ->
            digest.update(file.name.toByteArray())
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun backupDirPath(): String = backupDir.absolutePath
}
