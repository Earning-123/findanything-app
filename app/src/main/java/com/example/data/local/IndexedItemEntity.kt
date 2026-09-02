package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 1. Media Items (Photos, Videos)
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["type"]),
        Index(value = ["dateModified"]),
        Index(value = ["isScreenshot"]),
        Index(value = ["category"]),
        Index(value = ["visualHash"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String, // "PHOTO", "VIDEO"
    val uriString: String,
    val filePath: String? = null,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val isScreenshot: Boolean = false,
    val category: String = "Camera Photos", // Screenshots, Documents, Camera Photos, Downloads, Memes, Receipts
    val visualHash: Long = 0L,
    val lastIndexedTimestamp: Long = System.currentTimeMillis()
)

// 2. File Items (Documents, PDFs, Text files, Spreadsheets, Archives)
@Entity(
    tableName = "file_items",
    indices = [
        Index(value = ["title"]),
        Index(value = ["dateModified"]),
        Index(value = ["category"]),
        Index(value = ["extension"])
    ]
)
data class FileItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val uriString: String,
    val filePath: String? = null,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val extension: String = "",
    val category: String = "Document",
    val lastIndexedTimestamp: Long = System.currentTimeMillis()
)

// 3. OCR Text extracted on-device
@Entity(
    tableName = "ocr_text",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["detectedType"])
    ]
)
data class OcrTextEntity(
    @PrimaryKey
    val uriString: String,
    val mediaId: String? = null,
    val extractedText: String = "",
    val amountsFound: String? = null, // e.g. "5000"
    val detectedType: String = "General", // Receipt, Invoice, Aadhaar, Ticket, General
    val timestamp: Long = System.currentTimeMillis()
)

// 4. Visual Feature data for similarity search
@Entity(
    tableName = "visual_features",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["perceptualHash"])
    ]
)
data class VisualFeatureEntity(
    @PrimaryKey
    val uriString: String,
    val mediaId: String? = null,
    val perceptualHash: Long = 0L,
    val colorDescriptor: String = "", // spatial grid color histogram string
    val aspectRatio: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

// 5. Labels / People / Entities (e.g. Rahul, Ammi, Office, My Bike, Aadhaar)
@Entity(
    tableName = "labels",
    indices = [Index(value = ["name"], unique = true)]
)
data class LabelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String = "#6750A4"
)

// 6. Multiple Reference Images per Label
@Entity(
    tableName = "reference_images",
    indices = [Index(value = ["labelId"])]
)
data class ReferenceImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val labelId: Long,
    val uriString: String,
    val perceptualHash: Long = 0L,
    val colorDescriptor: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

// 7. Label-Media Associations (Photos tagged with a label)
@Entity(
    tableName = "label_media_associations",
    indices = [
        Index(value = ["labelId"]),
        Index(value = ["mediaId"])
    ]
)
data class LabelMediaAssociationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val labelId: Long,
    val mediaId: String,
    val isAutoSuggested: Boolean = false,
    val similarityScore: Float = 1.0f,
    val taggedAt: Long = System.currentTimeMillis()
)

// 8. Confirmed / Rejected Matches by user
@Entity(
    tableName = "confirmed_matches",
    indices = [
        Index(value = ["labelId"]),
        Index(value = ["mediaId"])
    ]
)
data class ConfirmedMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val labelId: Long,
    val mediaId: String,
    val status: String, // "CONFIRMED", "REJECTED"
    val updatedAt: Long = System.currentTimeMillis()
)

// 9. Search History
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

// 10. Duplicate Group Entity
@Entity(
    tableName = "duplicate_groups",
    indices = [Index(value = ["representativeHash"])]
)
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val representativeHash: Long,
    val originalMediaId: String,
    val duplicateCount: Int,
    val totalReclaimableBytes: Long,
    val detectedAt: Long = System.currentTimeMillis()
)

// 11. Index Status for incremental indexing & diagnostics
@Entity(tableName = "index_status")
data class IndexStatusEntity(
    @PrimaryKey
    val id: Int = 1,
    val totalMediaDiscovered: Int = 0,
    val totalMediaIndexed: Int = 0,
    val totalFilesIndexed: Int = 0,
    val totalOcrExtracted: Int = 0,
    val totalFeaturesExtracted: Int = 0,
    val lastIndexStartTime: Long = 0L,
    val lastIndexEndTime: Long = 0L,
    val isIndexing: Boolean = false,
    val lastError: String? = null
)

// 12. User Settings
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val isSearchHistoryEnabled: Boolean = true,
    val isAutoIndexingPaused: Boolean = false,
    val maxOcrFileSize: Long = 10L * 1024 * 1024,
    val duplicateSensitivityThreshold: Int = 4
)

// Legacy Compatibility Entities
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
    tableName = "ocr_cache",
    indices = [Index(value = ["uriString"])]
)
data class OcrCacheEntity(
    @PrimaryKey
    val uriString: String,
    val extractedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

