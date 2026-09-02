package com.example.model

import android.net.Uri

enum class ItemType {
    PHOTO,
    VIDEO,
    DOCUMENT,
    FILE,
    APP,
    CONTACT
}

enum class IntentType {
    SEARCH_ALL,
    SEARCH_PHOTO,
    SEARCH_VIDEO,
    SEARCH_DOCUMENT,
    SEARCH_FILE,
    SEARCH_APP,
    SEARCH_CONTACT,
    SEARCH_DUPLICATES,
    SEARCH_LARGE_FILES,
    SEARCH_OCR,
    SEARCH_ENTITY, // For tagged people / entities e.g. Rahul, Ammi
    OPEN_ITEM,
    SHARE_ITEM,
    DELETE_ITEM,
    CAMERA_SEARCH,
    UPLOAD_REFERENCE
}

data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: ItemType,
    val uri: Uri? = null,
    val filePath: String? = null,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val packageName: String? = null,
    val phoneNumber: String? = null,
    val matchReason: String = "",
    val ocrText: String? = null,
    val visualHash: Long = 0L,
    val isScreenshot: Boolean = false,
    val labelBadge: String? = null, // "User Tagged", "Confirmed Match", "Possible Match"
    val isConfirmed: Boolean = false,
    val isPossibleMatch: Boolean = false,
    val associatedLabelId: Long? = null
) {
    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> ""
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }

    val formattedDate: String
        get() {
            if (dateModified <= 0) return ""
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(dateModified))
        }
}

data class ParsedIntent(
    val rawQuery: String,
    val intentType: IntentType,
    val searchTerms: List<String> = emptyList(),
    val targetPerson: String? = null,
    val targetAmount: String? = null,
    val targetFileType: String? = null,
    val targetDateFilter: DateFilter? = null,
    val targetYear: Int? = null,
    val targetAction: String? = null, // "OPEN_LATEST", "SHARE", "DELETE", "FIND_SIMILAR"
    val isScreenshotTargeted: Boolean = false,
    val minSizeBytes: Long? = null,
    val explanation: String = ""
)

enum class DateFilterType {
    TODAY,
    YESTERDAY,
    LAST_WEEK,
    LAST_MONTH,
    SPECIFIC_MONTH,
    SPECIFIC_YEAR
}

data class DateFilter(
    val type: DateFilterType,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val label: String
)

data class DuplicateCluster(
    val hash: Long,
    val original: SearchItem,
    val duplicates: List<SearchItem>,
    val totalReclaimableBytes: Long
)

