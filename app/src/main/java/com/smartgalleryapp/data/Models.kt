package com.smartgalleryapp.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

enum class MediaCategory {
    ALL,
    CAMERA,
    VIDEOS,
    DOWNLOADS,
    SCREENSHOTS,
    WHATSAPP,
    IMAGES,
    OTHER,
}

enum class MediaLocation {
    LIBRARY,
    VAULT,
    TRASH,
}

data class AppSettings(
    val autoTrack: Boolean = true,
    val backgroundScan: Boolean = true,
    val reviewDays: Int = 7,
    val trashRetentionDays: Int = 30,
    val notificationsEnabled: Boolean = true,
    val weeklyDigest: Boolean = false,
    val lastWeeklyDigestAt: Long? = null,
    val permissionRequestedOnce: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("autoTrack", autoTrack)
        put("backgroundScan", backgroundScan)
        put("reviewDays", reviewDays)
        put("trashRetentionDays", trashRetentionDays)
        put("notificationsEnabled", notificationsEnabled)
        put("weeklyDigest", weeklyDigest)
        put("lastWeeklyDigestAt", lastWeeklyDigestAt)
        put("permissionRequestedOnce", permissionRequestedOnce)
    }

    companion object {
        fun fromJson(json: JSONObject?): AppSettings {
            if (json == null) return AppSettings()
            return AppSettings(
                autoTrack = json.optBoolean("autoTrack", true),
                backgroundScan = json.optBoolean("backgroundScan", true),
                reviewDays = json.optInt("reviewDays", 7),
                trashRetentionDays = json.optInt("trashRetentionDays", 30),
                notificationsEnabled = json.optBoolean("notificationsEnabled", true),
                weeklyDigest = json.optBoolean("weeklyDigest", false),
                lastWeeklyDigestAt = json.optLongOrNull("lastWeeklyDigestAt"),
                permissionRequestedOnce = json.optBoolean("permissionRequestedOnce", false),
            )
        }
    }
}

data class MediaItem(
    val key: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val dateModifiedEpochSec: Long,
    val isVideo: Boolean,
    val category: MediaCategory,
    val sourceUri: String,
    val location: MediaLocation = MediaLocation.LIBRARY,
    val openCount: Int = 0,
    val lastOpenedAt: Long? = null,
    val watchedAt: Long? = null,
    val watchProgress: Int = 0,
    val keepForever: Boolean = false,
    val manuallyQueuedForReview: Boolean = false,
    val reviewScheduledAt: Long? = null,
    val vaultedAt: Long? = null,
    val vaultPath: String? = null,
    val trashedAt: Long? = null,
    val trashPath: String? = null,
    val purgeAt: Long? = null,
    val deleted: Boolean = false,
    val sourceRetained: Boolean = false,
) {
    val watched: Boolean get() = openCount > 0 || watchedAt != null
    val isReviewDue: Boolean get() = !keepForever && manuallyQueuedForReview && reviewScheduledAt != null
    val safeUri: Uri get() = Uri.parse(if (location == MediaLocation.LIBRARY) sourceUri else currentPath ?: sourceUri)
    val currentPath: String? get() = when (location) {
        MediaLocation.LIBRARY -> null
        MediaLocation.VAULT -> vaultPath
        MediaLocation.TRASH -> trashPath
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("sizeBytes", sizeBytes)
        put("relativePath", relativePath)
        put("dateModifiedEpochSec", dateModifiedEpochSec)
        put("isVideo", isVideo)
        put("category", category.name)
        put("sourceUri", sourceUri)
        put("location", location.name)
        put("openCount", openCount)
        put("lastOpenedAt", lastOpenedAt)
        put("watchedAt", watchedAt)
        put("watchProgress", watchProgress)
        put("keepForever", keepForever)
        put("manuallyQueuedForReview", manuallyQueuedForReview)
        put("reviewScheduledAt", reviewScheduledAt)
        put("vaultedAt", vaultedAt)
        put("vaultPath", vaultPath)
        put("trashedAt", trashedAt)
        put("trashPath", trashPath)
        put("purgeAt", purgeAt)
        put("deleted", deleted)
        put("sourceRetained", sourceRetained)
    }

    companion object {
        fun fromJson(json: JSONObject): MediaItem = MediaItem(
            key = json.getString("key"),
            displayName = json.getString("displayName"),
            mimeType = json.optString("mimeType"),
            sizeBytes = json.optLong("sizeBytes"),
            relativePath = json.optString("relativePath"),
            dateModifiedEpochSec = json.optLong("dateModifiedEpochSec"),
            isVideo = json.optBoolean("isVideo"),
            category = runCatching { MediaCategory.valueOf(json.optString("category", "OTHER")) }.getOrDefault(MediaCategory.OTHER),
            sourceUri = json.optString("sourceUri"),
            location = runCatching { MediaLocation.valueOf(json.optString("location", "LIBRARY")) }.getOrDefault(MediaLocation.LIBRARY),
            openCount = json.optInt("openCount"),
            lastOpenedAt = json.optLongOrNull("lastOpenedAt"),
            watchedAt = json.optLongOrNull("watchedAt"),
            watchProgress = json.optInt("watchProgress"),
            keepForever = json.optBoolean("keepForever"),
            manuallyQueuedForReview = json.optBoolean("manuallyQueuedForReview"),
            reviewScheduledAt = json.optLongOrNull("reviewScheduledAt"),
            vaultedAt = json.optLongOrNull("vaultedAt"),
            vaultPath = json.optStringOrNull("vaultPath"),
            trashedAt = json.optLongOrNull("trashedAt"),
            trashPath = json.optStringOrNull("trashPath"),
            purgeAt = json.optLongOrNull("purgeAt"),
            deleted = json.optBoolean("deleted"),
            sourceRetained = json.optBoolean("sourceRetained"),
        )
    }
}

data class ScanSummary(
    val total: Int = 0,
    val watched: Int = 0,
    val reviewCount: Int = 0,
    val scannedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("total", total)
        put("watched", watched)
        put("reviewCount", reviewCount)
        put("scannedAt", scannedAt)
    }

    companion object {
        fun fromJson(json: JSONObject?): ScanSummary {
            if (json == null) return ScanSummary()
            return ScanSummary(
                total = json.optInt("total"),
                watched = json.optInt("watched"),
                reviewCount = json.optInt("reviewCount"),
                scannedAt = json.optLong("scannedAt", System.currentTimeMillis()),
            )
        }
    }
}

data class DashboardStats(
    val totalFiles: Int = 0,
    val watchedFiles: Int = 0,
    val unwatchedFiles: Int = 0,
    val reviewFiles: Int = 0,
    val trashFiles: Int = 0,
    val vaultFiles: Int = 0,
    val totalBytes: Long = 0,
    val vaultBytes: Long = 0,
    val reviewBytes: Long = 0,
    val trashBytes: Long = 0,
)

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
)

fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        add(optString(index))
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
