package com.example.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Per-device backup/sync worker.
 *
 * The phone is always the source of truth. This worker only reads the local
 * database, creates a safety copy first, then may upload that snapshot. It
 * never blocks the app and it uploads only when the data hash has changed.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = CloudSettingsRepository(applicationContext)
        val settings = repo.load()
        // The provider allows the feature; the owner decides whether this
        // phone actually uses it.
        if (!settings.providerEnabled || !settings.ownerBackupEnabled) return Result.success()

        val mode = inputData.getString(KEY_MODE) ?: MODE_HOURLY
        val backupManager = CloudBackupManager(applicationContext)
        val transport = GoogleDriveCloudTransport(applicationContext)

        // Make a rolling local backup before any cloud write. Daily backup runs
        // even when no Google account is connected yet.
        val backup = backupManager.createBackup(settings.deviceName.ifBlank { repo.displayDeviceName() })
        if (backup.file == null) {
            repo.update { it.copy(lastBackupAt = 0L, lastError = backup.message) }
            return Result.success()
        }
        repo.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupFile = backup.file.name) }

        if (mode == MODE_DAILY_BACKUP) {
            repo.update { it.copy(lastError = "") }
            return Result.success()
        }

        if (settings.ownerGmail.isBlank()) {
            repo.update {
                it.copy(
                    lastError = if (mode == MODE_MANUAL) {
                        "Connect a Google account before syncing."
                    } else {
                        "Connect a Google account from More > Backup & Cloud."
                    }
                )
            }
            return if (mode == MODE_MANUAL) Result.failure() else Result.success()
        }

        // Hourly runs are skipped when nothing has changed since the last upload.
        if (mode == MODE_HOURLY) {
            val hash = backupManager.snapshotHash()
            if (hash == settings.lastUploadedHash && settings.lastBackupAt > 0L) {
                return Result.success()
            }
        }

        val token = transport.accessToken(settings.ownerGmail)
        if (token.isNullOrBlank()) {
            repo.update {
                it.copy(
                    lastError = "Google account needs access approval. Tap Backup now and approve via Google.",
                    lastSyncAt = 0L
                )
            }
            return if (mode == MODE_MANUAL) Result.failure() else Result.retry()
        }

        val uploaded = transport.upload(
            token = token,
            deviceName = repo.deviceId(),
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
        return if (mode == MODE_MANUAL) Result.failure() else Result.retry()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_HOURLY = "hourly"
        const val MODE_MANUAL = "manual"
        const val MODE_DAILY_BACKUP = "daily_backup"
    }
}
