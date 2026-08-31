package com.example.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val PERIODIC_NAME = "kadepos_cloud_hourly"
    private const val DAILY_NAME = "kadepos_cloud_daily"
    private const val MANUAL_NAME = "kadepos_cloud_manual"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(1, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(CloudSyncWorker.KEY_MODE, CloudSyncWorker.MODE_HOURLY).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleDailyBackup(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(1, TimeUnit.DAYS)
            .setInputData(
                Data.Builder().putString(CloudSyncWorker.KEY_MODE, CloudSyncWorker.MODE_DAILY_BACKUP).build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(
                Data.Builder().putString(CloudSyncWorker.KEY_MODE, CloudSyncWorker.MODE_MANUAL).build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL_NAME)
    }

    fun cancelHourly(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }

    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_NAME)
    }
}
