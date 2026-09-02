package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "indexed_items",
    indices = [
        Index(value = ["type"]),
        Index(value = ["title"]),
        Index(value = ["dateModified"]),
        Index(value = ["sizeBytes"]),
        Index(value = ["visualHash"])
    ]
)
data class IndexedItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: String,
    val uriString: String = "",
    val filePath: String = "",
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val packageName: String? = null,
    val phoneNumber: String? = null,
    val ocrText: String? = null,
    val visualHash: Long = 0L,
    val isScreenshot: Boolean = false,
    val lastIndexedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["timestamp"])]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val searchType: String = "TEXT",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ocr_cache",
    indices = [Index(value = ["uriString"])]
)
data class OcrCacheEntity(
    @PrimaryKey
    val uriString: String,
    val extractedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
