package com.smartgalleryapp.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SmartGalleryRepository(private val context: Context) {
    private val tag = "SmartGalleryRepository"
    private val store = MetadataStore(context)
    private val vaultDir: File by lazy { File(context.getExternalFilesDir(null), "vault").apply { mkdirs() } }
    private val trashDir: File by lazy { File(context.getExternalFilesDir(null), "trash").apply { mkdirs() } }

    fun loadSettings(): AppSettings = store.loadSettings()

    fun saveSettings(settings: AppSettings) {
        store.saveSettings(settings)
    }

    fun hasMediaPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                images || videos || selected
            }
            Build.VERSION.SDK_INT >= 33 -> {
                val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                images || videos
            }
            else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun loadState(settings: AppSettings = loadSettings()): RepositoryState {
        val items = store.loadItems().values.toList()
        val visibleItems = items.filterNot { it.deleted }
        val summary = buildSummary(visibleItems)
        return RepositoryState(
            items = visibleItems,
            settings = settings,
            summary = summary,
            stats = computeStats(visibleItems),
            storage = storageSnapshot(),
        )
    }

    fun refreshForSettings(settings: AppSettings): RepositoryState {
        val metadata = store.loadItems()
        metadata.replaceAll { _, item -> normalizeReviewState(item) }
        store.saveItems(metadata)
        val items = metadata.values.filterNot { it.deleted }
        val summary = buildSummary(items)
        store.saveSummary(summary)
        maybeSendWeeklyDigest(settings, computeStats(items))
        return RepositoryState(
            items = items,
            settings = settings,
            summary = summary,
            stats = computeStats(items),
            storage = storageSnapshot(),
        )
    }

    fun scan(settings: AppSettings, onProgress: (String) -> Unit = {}): RepositoryState {
        purgeExpiredTrash()
        if (!hasMediaPermission()) {
            Log.w(tag, "Skipping MediaStore scan because media permission is not granted.")
            return refreshForSettings(settings)
        }
        val metadata = store.loadItems()
        val steps = listOf(
            "Scanning DCIM",
            "Scanning Videos",
            "Scanning Downloads",
            "Scanning Screenshots",
            "Analysing history",
            "Classifying files",
            "Scheduling reviews",
            "All done",
        )

        val scannedKeys = mutableSetOf<String>()
        steps.take(6).forEachIndexed { index, label ->
            onProgress(label)
            if (index == 0) {
                queryMediaStore().forEach { scanned ->
                    scannedKeys += scanned.key
                    val existing = metadata[scanned.key]
                    metadata[scanned.key] = mergeScanned(existing, scanned)
                }
            }
        }

        metadata.values.toList().forEach { item ->
            if (item.location == MediaLocation.LIBRARY && item.key !in scannedKeys && !item.deleted) {
                metadata[item.key] = item.copy(deleted = true)
            }
        }

        onProgress(steps[6])
        metadata.replaceAll { _, item -> normalizeReviewState(item) }
        onProgress(steps[7])

        val items = metadata.values.filterNot { it.deleted }
        val summary = buildSummary(items)

        store.saveItems(metadata)
        store.saveSummary(summary)

        if (settings.notificationsEnabled) {
            val reviewBytes = items.filter { it.isReviewDue }.sumOf { it.sizeBytes }
            val expiring = items.count { it.location == MediaLocation.TRASH && daysUntil(it.purgeAt) <= 3 }
            if (summary.reviewCount > 0) NotificationHelper.notifyReviewReady(context, summary.reviewCount, reviewBytes)
            if (expiring > 0) NotificationHelper.notifyTrashExpiring(context, expiring)
        }
        maybeSendWeeklyDigest(settings, computeStats(items))

        return RepositoryState(
            items = items,
            settings = settings,
            summary = summary,
            stats = computeStats(items),
            storage = storageSnapshot(),
        )
    }

    fun markOpened(itemKey: String, progress: Int = 100): RepositoryState {
        val metadata = store.loadItems()
        val item = metadata[itemKey] ?: return loadState()
        val now = System.currentTimeMillis()
        metadata[itemKey] = normalizeReviewState(
            item.copy(
                openCount = item.openCount + 1,
                lastOpenedAt = now,
                watchedAt = item.watchedAt ?: now,
                watchProgress = maxOf(item.watchProgress, progress),
            ),
        )
        store.saveItems(metadata)
        return loadState()
    }

    fun keepForever(itemKey: String): RepositoryState {
        val metadata = store.loadItems()
        val item = metadata[itemKey] ?: return loadState()
        metadata[itemKey] = item.copy(keepForever = true, manuallyQueuedForReview = false, reviewScheduledAt = null)
        store.saveItems(metadata)
        return loadState()
    }

    fun addToReview(keys: List<String>): RepositoryState {
        if (keys.isEmpty()) return loadState()
        val metadata = store.loadItems()
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            val item = metadata[key] ?: return@forEach
            if (!item.deleted && item.location == MediaLocation.LIBRARY) {
                metadata[key] = item.copy(
                    keepForever = false,
                    manuallyQueuedForReview = true,
                    reviewScheduledAt = now,
                )
            }
        }
        store.saveItems(metadata)
        return loadState()
    }

    fun removeFromReview(itemKey: String): RepositoryState {
        val metadata = store.loadItems()
        val item = metadata[itemKey] ?: return loadState()
        metadata[itemKey] = item.copy(manuallyQueuedForReview = false, reviewScheduledAt = null)
        store.saveItems(metadata)
        return loadState()
    }

    fun clearAllData(): RepositoryState {
        store.clearAll()
        return loadState(AppSettings())
    }

    fun emptyTrash(): RepositoryState {
        val metadata = store.loadItems()
        metadata.replaceAll { _, item ->
            if (item.location == MediaLocation.TRASH) {
                item.trashPath?.let { File(it).delete() }
                item.copy(
                    deleted = true,
                    trashPath = null,
                    purgeAt = null,
                )
            } else {
                item
            }
        }
        store.saveItems(metadata)
        return loadState()
    }

    fun moveToVault(keys: List<String>): RepositoryState {
        val metadata = store.loadItems()
        keys.forEach { key ->
            val item = metadata[key] ?: return@forEach
            if (item.deleted) return@forEach
            val target = uniqueFile(vaultDir, item.displayName)
            val retained = when (item.location) {
                MediaLocation.LIBRARY -> copyFromUri(Uri.parse(item.sourceUri), target)
                MediaLocation.VAULT -> false
                MediaLocation.TRASH -> copyFromPath(item.trashPath, target)
            }
            metadata[key] = item.copy(
                location = MediaLocation.VAULT,
                vaultedAt = System.currentTimeMillis(),
                vaultPath = target.absolutePath,
                trashedAt = null,
                trashPath = null,
                purgeAt = null,
                manuallyQueuedForReview = false,
                reviewScheduledAt = null,
                sourceRetained = retained || item.sourceRetained,
            )
            if (item.location == MediaLocation.TRASH) {
                item.trashPath?.let { File(it).delete() }
            }
        }
        store.saveItems(metadata)
        return loadState()
    }

    fun moveToTrash(keys: List<String>, retentionDays: Int = loadSettings().trashRetentionDays): RepositoryState {
        val metadata = store.loadItems()
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            val item = metadata[key] ?: return@forEach
            if (item.deleted) return@forEach
            val target = uniqueFile(trashDir, item.displayName)
            val retained = when (item.location) {
                MediaLocation.LIBRARY -> copyFromUri(Uri.parse(item.sourceUri), target)
                MediaLocation.VAULT -> copyFromPath(item.vaultPath, target)
                MediaLocation.TRASH -> false
            }
            metadata[key] = item.copy(
                location = MediaLocation.TRASH,
                trashedAt = now,
                trashPath = target.absolutePath,
                purgeAt = now + TimeUnit.DAYS.toMillis(retentionDays.toLong()),
                vaultPath = if (item.location == MediaLocation.VAULT) null else item.vaultPath,
                manuallyQueuedForReview = false,
                reviewScheduledAt = null,
                sourceRetained = retained || item.sourceRetained,
            )
            if (item.location == MediaLocation.VAULT) {
                item.vaultPath?.let { File(it).delete() }
            }
        }
        store.saveItems(metadata)
        return loadState()
    }

    fun recoverFromTrash(itemKey: String): RepositoryState {
        val metadata = store.loadItems()
        val item = metadata[itemKey] ?: return loadState()
        if (item.location != MediaLocation.TRASH) return loadState()
        val restoredUri = if (item.sourceRetained) {
            item.sourceUri
        } else {
            restoreToMediaStore(item)
        }
        item.trashPath?.let { File(it).delete() }
        metadata[itemKey] = normalizeReviewState(
            item.copy(
                location = MediaLocation.LIBRARY,
                sourceUri = restoredUri ?: item.sourceUri,
                trashedAt = null,
                trashPath = null,
                purgeAt = null,
            ),
        )
        store.saveItems(metadata)
        return loadState()
    }

    fun permanentlyDelete(itemKey: String): RepositoryState {
        val metadata = store.loadItems()
        val item = metadata[itemKey] ?: return loadState()
        item.trashPath?.let { File(it).delete() }
        item.vaultPath?.let { File(it).delete() }
        metadata[itemKey] = item.copy(
            deleted = true,
            location = MediaLocation.TRASH,
            trashPath = null,
            vaultPath = null,
            purgeAt = null,
        )
        store.saveItems(metadata)
        return loadState()
    }

    fun purgeExpiredTrash(): Int {
        val metadata = store.loadItems()
        val now = System.currentTimeMillis()
        var purged = 0
        metadata.replaceAll { _, item ->
            if (item.location == MediaLocation.TRASH && item.purgeAt != null && item.purgeAt <= now) {
                item.trashPath?.let { File(it).delete() }
                purged += 1
                item.copy(deleted = true, trashPath = null, purgeAt = null)
            } else {
                item
            }
        }
        store.saveItems(metadata)
        return purged
    }

    fun thumbnailFor(item: MediaItem): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(item.safeUri, Size(256, 256), null)
    }.getOrNull()

    private fun queryMediaStore(): List<MediaItem> {
        if (!hasMediaPermission()) {
            Log.w(tag, "queryMediaStore called without media permission.")
            return emptyList()
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val uri = MediaStore.Files.getContentUri("external")

        return runCatching {
            val results = mutableListOf<MediaItem>()
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    results += cursor.toMediaItem()
                }
            }
            results
        }.onFailure {
            Log.e(tag, "MediaStore query failed.", it)
        }.getOrElse { emptyList() }
    }

    private fun Cursor.toMediaItem(): MediaItem {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
        val displayName = getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "Untitled"
        val mimeType = getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: ""
        val size = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
        val modified = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED))
        val relativePath = getString(getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)) ?: ""
        val mediaType = getInt(getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
        val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        return MediaItem(
            key = contentUri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = size,
            relativePath = relativePath,
            dateModifiedEpochSec = modified,
            isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
            category = categoryFor(relativePath, mimeType),
            sourceUri = contentUri.toString(),
        )
    }

    private fun mergeScanned(existing: MediaItem?, scanned: MediaItem): MediaItem {
        if (existing == null) return normalizeReviewState(scanned)
        return normalizeReviewState(
            existing.copy(
                displayName = scanned.displayName,
                mimeType = scanned.mimeType,
                sizeBytes = scanned.sizeBytes,
                relativePath = scanned.relativePath,
                dateModifiedEpochSec = scanned.dateModifiedEpochSec,
                isVideo = scanned.isVideo,
                category = scanned.category,
                sourceUri = scanned.sourceUri,
                deleted = false,
            ),
        )
    }

    private fun normalizeReviewState(item: MediaItem): MediaItem {
        if (item.deleted || item.location != MediaLocation.LIBRARY || item.keepForever || !item.manuallyQueuedForReview) {
            return item.copy(reviewScheduledAt = null)
        }
        return item.copy(reviewScheduledAt = item.reviewScheduledAt ?: System.currentTimeMillis())
    }

    private fun copyFromUri(uri: Uri, target: File): Boolean {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: return true
        return !deleteSource(uri)
    }

    private fun copyFromPath(path: String?, target: File): Boolean {
        if (path.isNullOrBlank()) return true
        File(path).inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return false
    }

    private fun restoreToMediaStore(item: MediaItem): String? {
        val relativePath = if (item.relativePath.isNotBlank()) item.relativePath else "Pictures/SmartGalleryApp/"
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { output ->
            File(item.trashPath ?: return null).inputStream().use { input -> input.copyTo(output) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri.toString()
    }

    private fun deleteSource(uri: Uri): Boolean = runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    private fun uniqueFile(dir: File, name: String): File {
        val cleanName = name.ifBlank { "media_${System.currentTimeMillis()}" }
        val base = cleanName.substringBeforeLast('.', cleanName)
        val extension = cleanName.substringAfterLast('.', "")
        var candidate = File(dir, cleanName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) "$base-$index" else "$base-$index.$extension"
            candidate = File(dir, nextName)
            index++
        }
        return candidate
    }

    private fun computeStats(items: List<MediaItem>): DashboardStats = DashboardStats(
        totalFiles = items.count { it.location == MediaLocation.LIBRARY },
        watchedFiles = items.count { it.watched && it.location != MediaLocation.TRASH },
        unwatchedFiles = items.count { !it.watched && it.location == MediaLocation.LIBRARY },
        reviewFiles = items.count { it.isReviewDue },
        trashFiles = items.count { it.location == MediaLocation.TRASH },
        vaultFiles = items.count { it.location == MediaLocation.VAULT },
        totalBytes = items.filter { it.location == MediaLocation.LIBRARY }.sumOf { it.sizeBytes },
        vaultBytes = items.filter { it.location == MediaLocation.VAULT }.sumOf { it.sizeBytes },
        reviewBytes = items.filter { it.isReviewDue }.sumOf { it.sizeBytes },
        trashBytes = items.filter { it.location == MediaLocation.TRASH }.sumOf { it.sizeBytes },
    )

    private fun storageSnapshot(): StorageSnapshot {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val stats = Os.statvfs(path)
        val total = stats.f_blocks * stats.f_frsize
        val free = stats.f_bavail * stats.f_frsize
        return StorageSnapshot(totalBytes = total, usedBytes = total - free, freeBytes = free)
    }

    private fun buildSummary(items: List<MediaItem>): ScanSummary = ScanSummary(
        total = items.count { it.location == MediaLocation.LIBRARY },
        watched = items.count { it.watched && it.location != MediaLocation.TRASH },
        reviewCount = items.count { it.isReviewDue },
        scannedAt = System.currentTimeMillis(),
    )

    private fun maybeSendWeeklyDigest(settings: AppSettings, stats: DashboardStats) {
        if (!settings.notificationsEnabled || !settings.weeklyDigest || !shouldSendWeeklyDigest(settings.lastWeeklyDigestAt)) {
            return
        }
        NotificationHelper.notifyWeeklyDigest(context, stats.totalFiles + stats.vaultFiles, stats.trashFiles, stats.reviewFiles)
        saveSettings(settings.copy(lastWeeklyDigestAt = System.currentTimeMillis()))
    }

    private fun shouldSendWeeklyDigest(lastSentAt: Long?): Boolean {
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return false
        if (now.get(Calendar.HOUR_OF_DAY) != 9) return false
        if (lastSentAt == null) return true
        return System.currentTimeMillis() - lastSentAt >= TimeUnit.DAYS.toMillis(6)
    }

    companion object {
        fun categoryFor(relativePath: String, mimeType: String): MediaCategory {
            val path = relativePath.lowercase()
            return when {
                "screenshot" in path -> MediaCategory.SCREENSHOTS
                "whatsapp" in path -> MediaCategory.WHATSAPP
                "download" in path -> MediaCategory.DOWNLOADS
                "dcim" in path || "camera" in path -> MediaCategory.CAMERA
                mimeType.startsWith("video") -> MediaCategory.VIDEOS
                mimeType.startsWith("image") -> MediaCategory.IMAGES
                else -> MediaCategory.OTHER
            }
        }

        fun daysUntil(timestamp: Long?): Long {
            if (timestamp == null) return Long.MAX_VALUE
            val delta = timestamp - System.currentTimeMillis()
            return kotlin.math.ceil(delta / TimeUnit.DAYS.toMillis(1).toDouble()).toLong()
        }
    }
}

data class RepositoryState(
    val items: List<MediaItem>,
    val settings: AppSettings,
    val summary: ScanSummary,
    val stats: DashboardStats,
    val storage: StorageSnapshot,
)
