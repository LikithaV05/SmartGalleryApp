package com.smartgalleryapp.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class MetadataStore(private val context: Context) {
    private val metadataFile = File(context.filesDir, "smart_gallery_metadata.json")
    private val settingsFile = File(context.filesDir, "smart_gallery_settings.json")
    private val summaryFile = File(context.filesDir, "smart_gallery_summary.json")

    fun loadItems(): MutableMap<String, MediaItem> {
        if (!metadataFile.exists()) return mutableMapOf()
        val json = runCatching { JSONObject(metadataFile.readText()) }.getOrNull() ?: return mutableMapOf()
        val result = mutableMapOf<String, MediaItem>()
        json.keys().forEach { key ->
            result[key] = MediaItem.fromJson(json.getJSONObject(key))
        }
        return result
    }

    fun saveItems(items: Map<String, MediaItem>) {
        val root = JSONObject()
        items.values.sortedBy { it.displayName.lowercase() }.forEach { item ->
            root.put(item.key, item.toJson())
        }
        metadataFile.writeText(root.toString())
    }

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return AppSettings.fromJson(runCatching { JSONObject(settingsFile.readText()) }.getOrNull())
    }

    fun saveSettings(settings: AppSettings) {
        settingsFile.writeText(settings.toJson().toString())
    }

    fun loadSummary(): ScanSummary {
        if (!summaryFile.exists()) return ScanSummary()
        return ScanSummary.fromJson(runCatching { JSONObject(summaryFile.readText()) }.getOrNull())
    }

    fun saveSummary(summary: ScanSummary) {
        summaryFile.writeText(summary.toJson().toString())
    }

    fun clearAll() {
        metadataFile.delete()
        settingsFile.delete()
        summaryFile.delete()
    }
}
