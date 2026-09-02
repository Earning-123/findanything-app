package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.FindAnythingDatabase
import com.example.data.local.IndexedItemEntity
import com.example.data.local.SearchHistoryEntity
import com.example.engine.AppScanner
import com.example.engine.ContactScanner
import com.example.engine.IntentEngine
import com.example.engine.MediaStoreScanner
import com.example.engine.OcrEngine
import com.example.engine.VisualSimilarityEngine
import com.example.model.DuplicateCluster
import com.example.model.IntentType
import com.example.model.ItemType
import com.example.model.ParsedIntent
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SearchRepository(
    private val context: Context,
    private val database: FindAnythingDatabase
) {
    private val dao = database.dao()
    private val mediaScanner = MediaStoreScanner(context)
    private val appScanner = AppScanner(context)
    private val contactScanner = ContactScanner(context)
    val ocrEngine = OcrEngine(context, dao)
    val visualEngine = VisualSimilarityEngine(context)

    // Observable search history
    val recentSearches: Flow<List<SearchHistoryEntity>> = dao.getRecentSearches()

    suspend fun saveSearchQuery(query: String, type: String = "TEXT") {
        if (query.isBlank()) return
        dao.insertSearch(SearchHistoryEntity(query = query.trim(), searchType = type))
    }

    suspend fun deleteSearchQuery(id: Long) {
        dao.deleteSearchById(id)
    }

    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }

    suspend fun clearIndexAndCache() {
        dao.clearAllItems()
        dao.clearOcrCache()
    }

    suspend fun getIndexedCount(): Int = dao.getItemCount()

    /**
     * Unified Universal Search Engine:
     * Accepts any natural language query (Text or Voice) and executes the unified pipeline.
     */
    suspend fun executeSearch(rawQuery: String): Pair<ParsedIntent, List<SearchItem>> = withContext(Dispatchers.Default) {
        val parsedIntent = IntentEngine.parse(rawQuery)
        saveSearchQuery(rawQuery, "TEXT")

        val results = when (parsedIntent.intentType) {
            IntentType.SEARCH_APP -> {
                val appTerm = parsedIntent.searchTerms.firstOrNull() ?: rawQuery
                val apps = appScanner.findMatchingApps(appTerm)
                apps.map { it.copy(matchReason = "Installed app matching '$appTerm'") }
            }
            IntentType.SEARCH_CONTACT -> {
                val personOrNum = parsedIntent.targetPerson ?: parsedIntent.searchTerms.firstOrNull() ?: ""
                val contacts = contactScanner.searchContacts(personOrNum)
                contacts.map { it.copy(matchReason = "Contact matching '$personOrNum'") }
            }
            IntentType.SEARCH_DUPLICATES -> {
                val photos = mediaScanner.scanPhotos(limit = 150)
                val clusters = visualEngine.detectDuplicates(photos)
                val duplicateItems = mutableListOf<SearchItem>()
                clusters.forEach { cluster ->
                    duplicateItems.add(cluster.original.copy(matchReason = "Primary Original (${cluster.duplicates.size} copies)"))
                    duplicateItems.addAll(cluster.duplicates)
                }
                duplicateItems
            }
            IntentType.SEARCH_LARGE_FILES -> {
                val minSize = parsedIntent.minSizeBytes ?: (100L * 1024 * 1024)
                val videos = mediaScanner.scanVideos(limit = 100).filter { it.sizeBytes >= minSize }
                val files = mediaScanner.scanDocumentsAndFiles(limit = 100).filter { it.sizeBytes >= minSize }
                (videos + files).sortedByDescending { it.sizeBytes }.map {
                    it.copy(matchReason = "Large file (${it.formattedSize})")
                }
            }
            IntentType.SEARCH_OCR -> {
                // Focused OCR on screenshots and recent photos
                val photos = mediaScanner.scanPhotos(limit = 120)
                val candidates = if (parsedIntent.isScreenshotTargeted) {
                    photos.filter { it.isScreenshot }
                } else photos

                val matched = mutableListOf<SearchItem>()
                val targetAmount = parsedIntent.targetAmount
                val targetTerms = parsedIntent.searchTerms

                for (item in candidates) {
                    val uri = item.uri ?: continue
                    val ocr = ocrEngine.extractTextFromUri(uri)
                    if (ocr.isBlank()) continue

                    val hasAmount = targetAmount != null && (ocr.contains(targetAmount) || ocr.contains("₹$targetAmount") || ocr.contains(targetAmount.replace(",", "")))
                    val hasTerms = targetTerms.isNotEmpty() && targetTerms.any { ocr.contains(it, ignoreCase = true) }

                    if (hasAmount || hasTerms) {
                        val reason = when {
                            hasAmount && hasTerms -> "OCR matched: ₹$targetAmount and ${targetTerms.joinToString()}"
                            hasAmount -> "OCR matched amount: ₹$targetAmount"
                            else -> "OCR text match"
                        }
                        matched.add(item.copy(ocrText = ocr, matchReason = reason))
                    }
                }
                matched
            }
            IntentType.SEARCH_PHOTO -> {
                val photos = mediaScanner.scanPhotos(limit = 200)
                filterAndRankMedia(photos, parsedIntent)
            }
            IntentType.SEARCH_VIDEO -> {
                val videos = mediaScanner.scanVideos(limit = 100)
                filterAndRankMedia(videos, parsedIntent)
            }
            IntentType.SEARCH_DOCUMENT -> {
                val docs = mediaScanner.scanDocumentsAndFiles(limit = 150)
                filterAndRankMedia(docs, parsedIntent)
            }
            IntentType.SEARCH_ALL, IntentType.SEARCH_FILE, IntentType.DELETE_ITEM, IntentType.SHARE_ITEM, IntentType.OPEN_ITEM, IntentType.CAMERA_SEARCH, IntentType.UPLOAD_REFERENCE -> {
                // Search across all authorized sources
                val photos = mediaScanner.scanPhotos(limit = 150)
                val videos = mediaScanner.scanVideos(limit = 50)
                val docs = mediaScanner.scanDocumentsAndFiles(limit = 100)
                val apps = appScanner.getInstalledApps()
                val contacts = if (parsedIntent.searchTerms.isNotEmpty()) contactScanner.searchContacts(parsedIntent.searchTerms.first()) else emptyList()

                val allMedia = photos + videos + docs
                val matchedMedia = filterAndRankMedia(allMedia, parsedIntent)
                val matchedApps = apps.filter { app ->
                    parsedIntent.searchTerms.any { term -> app.title.contains(term, ignoreCase = true) }
                }.map { it.copy(matchReason = "App name match") }

                matchedApps + contacts + matchedMedia
            }
        }

        parsedIntent to results
    }

    /**
     * Visual Similarity Search (Used by Reference Upload & Camera Search)
     */
    suspend fun searchByReferenceImage(referenceUri: Uri): List<SearchItem> = withContext(Dispatchers.Default) {
        val refHash = visualEngine.computeImageHash(referenceUri)
        if (refHash == 0L) return@withContext emptyList()
        val photos = mediaScanner.scanPhotos(limit = 150)
        visualEngine.findSimilarPhotos(refHash, photos, maxDistance = 14)
    }

    /**
     * Text OCR Search for Reference Image (Camera / Upload)
     */
    suspend fun searchByReferenceOcr(referenceUri: Uri): Pair<String, List<SearchItem>> = withContext(Dispatchers.Default) {
        val extracted = ocrEngine.extractTextFromUri(referenceUri)
        if (extracted.isBlank()) return@withContext ("No readable text found" to emptyList())

        // Extract key terms
        val words = extracted.split("\\s+".toRegex())
            .map { it.replace("[^a-zA-Z0-9]".toRegex(), "") }
            .filter { it.length > 3 }
            .take(5)

        if (words.isEmpty()) return@withContext (extracted to emptyList())

        val photos = mediaScanner.scanPhotos(limit = 100)
        val matched = mutableListOf<SearchItem>()

        for (photo in photos) {
            val uri = photo.uri ?: continue
            val text = ocrEngine.extractTextFromUri(uri)
            val matchedWord = words.firstOrNull { text.contains(it, ignoreCase = true) }
            if (matchedWord != null) {
                matched.add(photo.copy(matchReason = "Matching text: '$matchedWord'", ocrText = text))
            }
        }

        extracted to matched
    }

    /**
     * Smart Collections generator
     */
    suspend fun getCollection(type: String): List<SearchItem> = withContext(Dispatchers.Default) {
        when (type) {
            "Screenshots" -> mediaScanner.scanPhotos(200).filter { it.isScreenshot }
            "Documents" -> mediaScanner.scanDocumentsAndFiles(200).filter { it.type == ItemType.DOCUMENT }
            "Large Files" -> {
                val vids = mediaScanner.scanVideos(100).filter { it.sizeBytes > 50 * 1024 * 1024 }
                val files = mediaScanner.scanDocumentsAndFiles(100).filter { it.sizeBytes > 50 * 1024 * 1024 }
                (vids + files).sortedByDescending { it.sizeBytes }
            }
            "Downloads" -> mediaScanner.scanDocumentsAndFiles(200).filter {
                it.filePath?.contains("Download", ignoreCase = true) == true
            }
            "Videos" -> mediaScanner.scanVideos(150)
            else -> mediaScanner.scanPhotos(100)
        }
    }

    private fun filterAndRankMedia(items: List<SearchItem>, intent: ParsedIntent): List<SearchItem> {
        val results = mutableListOf<SearchItem>()
        val dateFilter = intent.targetDateFilter
        val terms = intent.searchTerms
        val targetFileType = intent.targetFileType

        for (item in items) {
            var score = 0
            val reasons = mutableListOf<String>()

            // 1. Date Filter
            if (dateFilter != null) {
                if (item.dateModified in dateFilter.startTimestamp..dateFilter.endTimestamp) {
                    score += 50
                    reasons.add("Date: ${dateFilter.label}")
                } else {
                    // If date filter was explicitly requested and failed, skip
                    continue
                }
            }

            // 2. File type filter
            if (targetFileType != null) {
                if (item.title.endsWith(targetFileType, ignoreCase = true) || item.mimeType.contains(targetFileType, ignoreCase = true)) {
                    score += 40
                    reasons.add("Type: ${targetFileType.uppercase()}")
                }
            }

            // 3. Screenshot filter
            if (intent.isScreenshotTargeted) {
                if (item.isScreenshot) {
                    score += 40
                    reasons.add("Screenshot")
                }
            }

            // 4. Keyword matches in Title / Path
            if (terms.isNotEmpty()) {
                val matchedTerms = terms.filter {
                    item.title.contains(it, ignoreCase = true) || item.filePath?.contains(it, ignoreCase = true) == true
                }
                if (matchedTerms.isNotEmpty()) {
                    score += matchedTerms.size * 30
                    reasons.add("Name contains '${matchedTerms.joinToString()}'")
                }
            }

            if (score > 0 || (terms.isEmpty() && dateFilter != null)) {
                results.add(
                    item.copy(
                        matchReason = if (reasons.isNotEmpty()) reasons.joinToString(" • ") else "Relevant candidate"
                    )
                )
            }
        }

        return results
    }
}
