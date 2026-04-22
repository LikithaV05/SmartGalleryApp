package com.smartgalleryapp.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.smartgalleryapp.data.SmartGalleryRepository
import java.util.concurrent.TimeUnit

class PurgeWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val repository = SmartGalleryRepository(applicationContext)
        val settings = repository.loadSettings()
        if (settings.backgroundScan) {
            repository.scan(settings)
        } else {
            repository.purgeExpiredTrash()
        }
        return Result.success()
    }
}

object PurgeScheduler {
    private const val WORK_NAME = "smart_gallery_hourly_scan"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequest.Builder(PurgeWorker::class.java, 1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
