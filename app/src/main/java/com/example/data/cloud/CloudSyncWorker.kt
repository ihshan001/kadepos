package com.example.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Hourly (or manual) per-device upload to Google Drive.
 *
 * The phone is always the source of truth. This worker only reads the local
 * database, creates a safety copy first, then uploads that snapshot when the
 * data hash has changed. It never blocks the app.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = CloudSettingsRepository(applicationContext)
        var settings = repo.load()
        if (!settings.providerEnabled) return Result.success()

        if (settings.ownerGmail.isBlank()) {
            repo.update { it.copy(lastError = "Connect a Google account before syncing.", lastSyncAt = 0L) }
            return Result.success()
        }

        val backupManager = CloudBackupManager(applicationContext)
        val transport = GoogleDriveCloudTransport(applicationContext)

        // Make a rolling local backup before any cloud write.
        val backup = backupManager.createBackup(settings.deviceName)
        if (backup.file == null) {
            repo.update { it.copy(lastError = backup.message, lastSyncAt = 0L) }
            return Result.success()
        }

        if (settings.hourlySyncEnabled) {
            val hash = backupManager.snapshotHash()
            if (hash == settings.lastUploadedHash && settings.lastBackupAt > 0L &&
                System.currentTimeMillis() - settings.lastBackupAt < 60 * 60 * 1000L
            ) {
                return Result.success()
            }
        }

        val token = transport.accessToken(settings.ownerGmail)
        if (token.isNullOrBlank()) {
            repo.update {
                it.copy(
                    lastError = "Google account needs access approval. Tap Backup now from Settings and approve via Google.",
                    lastSyncAt = 0L
                )
            }
            return Result.retry()
        }

        val uploaded = transport.upload(
            token = token,
            deviceName = settings.deviceName.ifBlank { "counter" },
            fileName = backup.file.name,
            file = backup.file
        )
        currentCoroutineContext().ensureActive()

        if (uploaded != null) {
            repo.update {
                it.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    lastUploadedFile = uploaded.name,
                    lastUploadedHash = backupManager.snapshotHash(),
                    lastError = ""
                )
            }
            return Result.success()
        }
        repo.update { it.copy(lastError = "Could not upload the backup. Check your internet connection and try again.") }
        return Result.retry()
    }
}
