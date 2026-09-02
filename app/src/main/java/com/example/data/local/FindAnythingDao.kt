package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FindAnythingDao {
    // ----------------------------------------------------
    // Media Items (Photos, Videos)
    // ----------------------------------------------------
    @Query("SELECT * FROM media_items ORDER BY dateModified DESC")
    fun getAllMediaItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateModified DESC")
    suspend fun getAllMediaList(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE isScreenshot = 1 ORDER BY dateModified DESC")
    fun getScreenshotsMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY dateModified DESC")
    fun getMediaByCategory(category: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE dateModified >= :startTime AND dateModified <= :endTime ORDER BY dateModified DESC")
    suspend fun getMediaInDateRange(startTime: Long, endTime: Long): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE sizeBytes >= :minSize ORDER BY sizeBytes DESC")
    fun getLargeMedia(minSize: Long): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun clearAllMedia()

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getMediaCount(): Int

    @Query("SELECT MAX(dateModified) FROM media_items")
    suspend fun getLatestMediaDateModified(): Long?

    // ----------------------------------------------------
    // File Items (Documents, PDFs, Archives)
    // ----------------------------------------------------
    @Query("SELECT * FROM file_items ORDER BY dateModified DESC")
    fun getAllFileItems(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM file_items WHERE category = :category ORDER BY dateModified DESC")
    fun getFilesByCategory(category: String): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM file_items WHERE extension = :extension COLLATE NOCASE ORDER BY dateModified DESC")
    fun getFilesByExtension(extension: String): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM file_items WHERE title LIKE '%' || :query || '%' ORDER BY dateModified DESC")
    suspend fun searchFilesByTitle(query: String): List<FileItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileItems(items: List<FileItemEntity>)

    @Query("DELETE FROM file_items WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM file_items")
    suspend fun clearAllFiles()

    @Query("SELECT COUNT(*) FROM file_items")
    suspend fun getFileCount(): Int

    // ----------------------------------------------------
    // OCR Text
    // ----------------------------------------------------
    @Query("SELECT * FROM ocr_text WHERE uriString = :uriString LIMIT 1")
    suspend fun getOcrTextRecord(uriString: String): OcrTextEntity?

    @Query("SELECT * FROM ocr_text WHERE extractedText LIKE '%' || :query || '%'")
    suspend fun searchOcrByText(query: String): List<OcrTextEntity>

    @Query("SELECT * FROM ocr_text WHERE amountsFound LIKE '%' || :amount || '%'")
    suspend fun searchOcrByAmount(amount: String): List<OcrTextEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrRecord(ocr: OcrTextEntity)

    @Query("DELETE FROM ocr_text")
    suspend fun clearOcrText()

    @Query("SELECT COUNT(*) FROM ocr_text")
    suspend fun getOcrCount(): Int

    // ----------------------------------------------------
    // Visual Features
    // ----------------------------------------------------
    @Query("SELECT * FROM visual_features WHERE uriString = :uriString LIMIT 1")
    suspend fun getVisualFeature(uriString: String): VisualFeatureEntity?

    @Query("SELECT * FROM visual_features")
    suspend fun getAllVisualFeatures(): List<VisualFeatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualFeature(feature: VisualFeatureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualFeatures(features: List<VisualFeatureEntity>)

    @Query("DELETE FROM visual_features")
    suspend fun clearVisualFeatures()

    @Query("SELECT COUNT(*) FROM visual_features")
    suspend fun getVisualFeatureCount(): Int

    // ----------------------------------------------------
    // Labels / Entities (Rahul, Ammi, Office, Bike, etc.)
    // ----------------------------------------------------
    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun getAllLabels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY name ASC")
    suspend fun getAllLabelsList(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE id = :id LIMIT 1")
    suspend fun getLabelById(id: Long): LabelEntity?

    @Query("SELECT * FROM labels WHERE name LIKE :name LIMIT 1")
    suspend fun getLabelByName(name: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: LabelEntity): Long

    @Update
    suspend fun updateLabel(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteLabel(id: Long)

    @Query("SELECT COUNT(*) FROM labels")
    suspend fun getLabelCount(): Int

    // ----------------------------------------------------
    // Reference Images per Label
    // ----------------------------------------------------
    @Query("SELECT * FROM reference_images WHERE labelId = :labelId ORDER BY addedAt DESC")
    fun getReferencesForLabel(labelId: Long): Flow<List<ReferenceImageEntity>>

    @Query("SELECT * FROM reference_images WHERE labelId = :labelId ORDER BY addedAt DESC")
    suspend fun getReferencesForLabelList(labelId: Long): List<ReferenceImageEntity>

    @Query("SELECT * FROM reference_images")
    suspend fun getAllReferenceImages(): List<ReferenceImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferenceImage(ref: ReferenceImageEntity): Long

    @Query("DELETE FROM reference_images WHERE id = :id")
    suspend fun deleteReferenceImage(id: Long)

    @Query("DELETE FROM reference_images WHERE labelId = :labelId")
    suspend fun deleteReferencesForLabel(labelId: Long)

    @Query("SELECT COUNT(*) FROM reference_images")
    suspend fun getReferenceImageCount(): Int

    // ----------------------------------------------------
    // Label-Media Associations & Confirmed Matches
    // ----------------------------------------------------
    @Query("SELECT * FROM label_media_associations WHERE labelId = :labelId")
    suspend fun getAssociationsForLabel(labelId: Long): List<LabelMediaAssociationEntity>

    @Query("SELECT * FROM label_media_associations WHERE mediaId = :mediaId")
    suspend fun getAssociationsForMedia(mediaId: String): List<LabelMediaAssociationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociation(association: LabelMediaAssociationEntity): Long

    @Query("DELETE FROM label_media_associations WHERE labelId = :labelId AND mediaId = :mediaId")
    suspend fun deleteAssociation(labelId: Long, mediaId: String)

    @Query("SELECT * FROM confirmed_matches WHERE labelId = :labelId")
    fun getConfirmedMatchesForLabel(labelId: Long): Flow<List<ConfirmedMatchEntity>>

    @Query("SELECT * FROM confirmed_matches WHERE labelId = :labelId")
    suspend fun getConfirmedMatchesForLabelList(labelId: Long): List<ConfirmedMatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfirmedMatch(match: ConfirmedMatchEntity): Long

    @Query("DELETE FROM confirmed_matches WHERE labelId = :labelId AND mediaId = :mediaId")
    suspend fun deleteConfirmedMatch(labelId: Long, mediaId: String)

    // ----------------------------------------------------
    // Duplicate Groups
    // ----------------------------------------------------
    @Query("SELECT * FROM duplicate_groups ORDER BY totalReclaimableBytes DESC")
    fun getAllDuplicateGroups(): Flow<List<DuplicateGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuplicateGroups(groups: List<DuplicateGroupEntity>)

    @Query("DELETE FROM duplicate_groups")
    suspend fun clearDuplicateGroups()

    // ----------------------------------------------------
    // Index Status & Settings
    // ----------------------------------------------------
    @Query("SELECT * FROM index_status WHERE id = 1 LIMIT 1")
    fun getIndexStatusFlow(): Flow<IndexStatusEntity?>

    @Query("SELECT * FROM index_status WHERE id = 1 LIMIT 1")
    suspend fun getIndexStatus(): IndexStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateIndexStatus(status: IndexStatusEntity)

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateUserSettings(settings: UserSettingsEntity)

    // ----------------------------------------------------
    // Search History
    // ----------------------------------------------------
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // ----------------------------------------------------
    // Legacy Compatibility Queries
    // ----------------------------------------------------
    @Query("SELECT * FROM indexed_items ORDER BY dateModified DESC")
    fun getAllIndexedItems(): Flow<List<IndexedItemEntity>>

    @Query("SELECT * FROM indexed_items WHERE type = :type ORDER BY dateModified DESC")
    fun getItemsByType(type: String): Flow<List<IndexedItemEntity>>

    @Query("SELECT * FROM indexed_items WHERE isScreenshot = 1 ORDER BY dateModified DESC")
    fun getScreenshots(): Flow<List<IndexedItemEntity>>

    @Query("SELECT * FROM indexed_items WHERE sizeBytes >= :minSize ORDER BY sizeBytes DESC")
    fun getLargeFiles(minSize: Long): Flow<List<IndexedItemEntity>>

    @Query("SELECT * FROM indexed_items WHERE visualHash != 0 GROUP BY visualHash HAVING count(*) > 1")
    suspend fun getDuplicateCandidates(): List<IndexedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<IndexedItemEntity>)

    @Query("DELETE FROM indexed_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM indexed_items")
    suspend fun clearAllItems()

    @Query("SELECT COUNT(*) FROM indexed_items")
    suspend fun getItemCount(): Int

    @Query("SELECT extractedText FROM ocr_cache WHERE uriString = :uriString LIMIT 1")
    suspend fun getOcrText(uriString: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcr(ocr: OcrCacheEntity)

    @Query("DELETE FROM ocr_cache")
    suspend fun clearOcrCache()
}

