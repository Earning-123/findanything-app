package com.example.model

import com.example.data.local.LabelEntity
import com.example.data.local.ReferenceImageEntity

data class LabelWithDetails(
    val label: LabelEntity,
    val references: List<ReferenceImageEntity> = emptyList(),
    val confirmedPhotos: List<SearchItem> = emptyList(),
    val possibleMatches: List<SearchItem> = emptyList()
) {
    val totalPhotosCount: Int
        get() = confirmedPhotos.size
}

data class IndexDiagnostics(
    val totalMedia: Int = 0,
    val totalFiles: Int = 0,
    val totalOcr: Int = 0,
    val totalFeatures: Int = 0,
    val totalLabels: Int = 0,
    val totalReferences: Int = 0,
    val isIndexing: Boolean = false,
    val lastIndexTime: Long = 0L,
    val lastError: String? = null
)
