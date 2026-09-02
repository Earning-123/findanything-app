package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FindAnythingDao {
    // Indexed items
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

    // Search history
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // OCR Cache
    @Query("SELECT extractedText FROM ocr_cache WHERE uriString = :uriString LIMIT 1")
    suspend fun getOcrText(uriString: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcr(ocr: OcrCacheEntity)

    @Query("DELETE FROM ocr_cache")
    suspend fun clearOcrCache()
}
