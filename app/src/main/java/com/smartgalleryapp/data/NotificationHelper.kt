package com.smartgalleryapp.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smartgalleryapp.R

object NotificationHelper {
    const val CHANNEL_GENERAL = "smart_gallery_general"
    const val CHANNEL_REVIEW = "smart_gallery_review"
    const val CHANNEL_DIGEST = "smart_gallery_digest"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(CHANNEL_GENERAL, "SmartGallery General", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_REVIEW, "Review Alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_DIGEST, "Weekly Digest", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannels(channels)
    }

    fun notifyReviewReady(context: Context, fileCount: Int, bytes: Long) {
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Review queue ready")
            .setContentText("$fileCount files are idle and saving ${bytes.toHumanReadable()} if cleaned up.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    fun notifyTrashExpiring(context: Context, fileCount: Int) {
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Trash expires soon")
            .setContentText("$fileCount files have 3 days or less remaining in Trash.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(2002, notification)
    }

    fun notifyWeeklyDigest(context: Context, trackedFiles: Int, trashFiles: Int, reviewFiles: Int) {
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Weekly SmartGallery digest")
            .setContentText("$trackedFiles tracked, $reviewFiles in review, $trashFiles in trash.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(2003, notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

fun Long.toHumanReadable(): String {
    if (this <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}
