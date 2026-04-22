package com.smartgalleryapp

import android.app.Application
import com.smartgalleryapp.data.NotificationHelper
import com.smartgalleryapp.worker.PurgeScheduler

class SmartGalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        PurgeScheduler.schedule(this)
    }
}
