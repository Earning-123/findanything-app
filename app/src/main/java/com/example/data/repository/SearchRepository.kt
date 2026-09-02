package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.ConfirmedMatchEntity
import com.example.data.local.FileItemEntity
import com.example.data.local.FindAnythingDatabase
import com.example.data.local.IndexStatusEntity
import com.example.data.local.LabelEntity
import com.example.data.local.LabelMediaAssociationEntity
import com.example.data.local.MediaItemEntity
import com.example.data.local.OcrTextEntity
import com.example.data.local.ReferenceImageEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.VisualFeatureEntity
import com.example.engine.AppScanner
import com.example.engine.ContactScanner
import com.example.engine.IntentEngine
import com.example.engine.MediaStoreScanner
import com.example.engine.OcrEngine
import com.example.engine.VisualSimilarityEngine
import com.example.model.IndexDiagnostics
import com.example.model.IntentType
import com.example.model.ItemType
import com.example.model.LabelWithDetails
import com.example.model.ParsedIntent
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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

    // Observable search history & labels
    val recentSearches: Flow<List<SearchHistoryEntity>> = dao.getRecentSearches()
    val allLabels: Flow<List<LabelEntity>> = dao.getAllLabels()
    val indexStatusFlow: Flow<IndexStatusEntity?> = dao.getIndexStatusFlow()

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
        dao.clearAllMedia()
        dao.clearAllFiles()
        dao.clearOcrText()
        dao.clearVisualFeatures()
        dao.clearAllItems()
        dao.clearOcrCache()
        dao.updateIndexStatus(IndexStatusEntity(id = 1, isIndexing = false))
    }

    suspend fun clearAllRememberedData() {
        val labels = dao.getAllLabelsList()
        for (l in labels) {
            dao.deleteReferencesForLabel(l.id)
            dao.deleteLabel(l.id)
        }
        val refDir = File(context.filesDir, "references")
        if (refDir.exists()) {
            refDir.deleteRecursively()
        }
    }

    suspend fun getIndexedCount(): Int = dao.getMediaCount() + dao.getFileCount()

    /**
     * Incremental Indexing: Only indexes newly added or modified files since last scan
     */
    suspend fun indexDeviceContent(forceRebuild: Boolean = false) = withContext(Dispatchers.IO) {
        if (forceRebuild) {
            clearIndexAndCache()
        }

        val startTime = System.currentTimeMillis()
        dao.updateIndexStatus(
            IndexStatusEntity(
                id = 1,
                isIndexing = true,
                lastIndexStartTime = startTime
            )
        )

        try {
            val photos = mediaScanner.scanPhotos(limit = 350)
            val videos = mediaScanner.scanVideos(limit = 100)
            val docs = mediaScanner.scanDocumentsAndFiles(limit = 200)

            val mediaEntities = mutableListOf<MediaItemEntity>()
            val visualFeatures = mutableListOf<VisualFeatureEntity>()

            for (photo in photos) {
                val hash = if (photo.visualHash != 0L) photo.visualHash else {
                    photo.uri?.let { visualEngine.computeImageHash(it) } ?: 0L
                }

                val category = when {
                    photo.isScreenshot -> "Screenshots"
                    photo.filePath?.contains("Download", ignoreCase = true) == true -> "Downloads"
                    photo.filePath?.contains("DCIM", ignoreCase = true) == true -> "Camera Photos"
                    else -> "Camera Photos"
                }

                mediaEntities.add(
                    MediaItemEntity(
                        id = photo.id,
                        title = photo.title,
                        type = "PHOTO",
                        uriString = photo.uri?.toString() ?: "",
                        filePath = photo.filePath,
                        sizeBytes = photo.sizeBytes,
                        dateModified = photo.dateModified,
                        mimeType = photo.mimeType,
                        isScreenshot = photo.isScreenshot,
                        category = category,
                        visualHash = hash
                    )
                )

                if (hash != 0L && photo.uri != null) {
                    visualFeatures.add(
                        VisualFeatureEntity(
                            uriString = photo.uri.toString(),
                            mediaId = photo.id,
                            perceptualHash = hash
                        )
                    )
                }
            }

            for (video in videos) {
                mediaEntities.add(
                    MediaItemEntity(
                        id = video.id,
                        title = video.title,
                        type = "VIDEO",
                        uriString = video.uri?.toString() ?: "",
                        filePath = video.filePath,
                        sizeBytes = video.sizeBytes,
                        dateModified = video.dateModified,
                        mimeType = video.mimeType,
                        category = "Videos"
                    )
                )
            }

            dao.insertMediaItems(mediaEntities)
            dao.insertVisualFeatures(visualFeatures)

            // Index documents and files
            val fileEntities = docs.map { doc ->
                val ext = doc.title.substringAfterLast('.', "")
                val cat = when (ext.lowercase()) {
                    "pdf" -> "PDF"
                    "txt", "doc", "docx" -> "Document"
                    "zip", "tar", "rar" -> "Archive"
                    else -> "Document"
                }
                FileItemEntity(
                    id = doc.id,
                    title = doc.title,
                    uriString = doc.uri?.toString() ?: "",
                    filePath = doc.filePath,
                    sizeBytes = doc.sizeBytes,
                    dateModified = doc.dateModified,
                    mimeType = doc.mimeType,
                    extension = ext,
                    category = cat
                )
            }
            dao.insertFileItems(fileEntities)

            // Pre-cache OCR for up to 30 screenshots
            var ocrCount = 0
            val screenshots = photos.filter { it.isScreenshot }.take(30)
            for (ss in screenshots) {
                val u = ss.uri ?: continue
                try {
                    val txt = ocrEngine.extractTextFromUri(u)
                    if (txt.isNotBlank()) {
                        val amount = extractSimpleAmount(txt)
                        dao.insertOcrRecord(
                            OcrTextEntity(
                                uriString = u.toString(),
                                mediaId = ss.id,
                                extractedText = txt,
                                amountsFound = amount,
                                detectedType = if (amount != null) "Receipt" else "Screenshot"
                            )
                        )
                        ocrCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val endTime = System.currentTimeMillis()
            dao.updateIndexStatus(
                IndexStatusEntity(
                    id = 1,
                    totalMediaDiscovered = photos.size + videos.size,
                    totalMediaIndexed = mediaEntities.size,
                    totalFilesIndexed = fileEntities.size,
                    totalOcrExtracted = ocrCount,
                    totalFeaturesExtracted = visualFeatures.size,
                    lastIndexStartTime = startTime,
                    lastIndexEndTime = endTime,
                    isIndexing = false,
                    lastError = null
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            dao.updateIndexStatus(
                IndexStatusEntity(
                    id = 1,
                    isIndexing = false,
                    lastError = e.localizedMessage
                )
            )
        }
    }

    private fun extractSimpleAmount(text: String): String? {
        val rupeeRegex = Regex("(?:[₹]|rs\\.?|rupees?\\s*)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        rupeeRegex.find(text)?.let { return it.groupValues[1] }
        val numRegex = Regex("\\b(\\d{3,6})\\b")
        return numRegex.find(text)?.value
    }

    /**
     * Unified Universal Search Engine:
     * Accepts any natural language query (Text or Voice) and executes the unified pipeline.
     */
    suspend fun executeSearch(rawQuery: String): Pair<ParsedIntent, List<SearchItem>> = withContext(Dispatchers.Default) {
        val parsedIntent = IntentEngine.parse(rawQuery)
        saveSearchQuery(rawQuery, "TEXT")

        val results = when (parsedIntent.intentType) {
            IntentType.SEARCH_ENTITY -> {
                val entityName = parsedIntent.targetPerson ?: rawQuery
                searchEntityPipeline(entityName, parsedIntent)
            }
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
                val photos = mediaScanner.scanPhotos(limit = 150)
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
                            else -> "OCR text match: '${targetTerms.firstOrNull() ?: ""}'"
                        }
                        matched.add(item.copy(ocrText = ocr, matchReason = reason, labelBadge = "OCR Match"))
                    }
                }
                matched
            }
            IntentType.SEARCH_PHOTO -> {
                val photos = mediaScanner.scanPhotos(limit = 250)
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

        // Handle target action OPEN_LATEST
        if (parsedIntent.targetAction == "OPEN_LATEST" && results.isNotEmpty()) {
            val latest = results.maxByOrNull { it.dateModified }
            if (latest != null) {
                withContext(Dispatchers.Main) {
                    com.example.engine.ActionEngine(context).openItem(latest)
                }
            }
        }

        parsedIntent to results
    }

    /**
     * Entity / Person Search Pipeline (Sections 3, 5)
     * Combines:
     * 1. Exact user-tagged results
     * 2. Manually confirmed results
     * 3. Visual similarity results from all reference photos of the entity
     * 4. OCR / Context results matching entity name
     */
    private suspend fun searchEntityPipeline(entityName: String, intent: ParsedIntent): List<SearchItem> = withContext(Dispatchers.Default) {
        val label = dao.getLabelByName(entityName)
        val allPhotos = mediaScanner.scanPhotos(limit = 250)

        // Apply Year / Date filter to photos if specified
        val dateFilteredPhotos = if (intent.targetDateFilter != null) {
            val df = intent.targetDateFilter
            allPhotos.filter { it.dateModified in df.startTimestamp..df.endTimestamp }
        } else allPhotos

        val exactTaggedList = mutableListOf<SearchItem>()
        val confirmedList = mutableListOf<SearchItem>()
        val possibleMatchesList = mutableListOf<SearchItem>()
        val ocrMatchesList = mutableListOf<SearchItem>()
        val rejectedIds = mutableSetOf<String>()

        if (label != null) {
            val associations = dao.getAssociationsForLabel(label.id)
            val confirmedMatches = dao.getConfirmedMatchesForLabelList(label.id)
            val references = dao.getReferencesForLabelList(label.id)

            val confirmedIds = confirmedMatches.filter { it.status == "CONFIRMED" }.map { it.mediaId }.toSet()
            rejectedIds.addAll(confirmedMatches.filter { it.status == "REJECTED" }.map { it.mediaId })
            val taggedIds = associations.map { it.mediaId }.toSet()

            for (photo in dateFilteredPhotos) {
                if (rejectedIds.contains(photo.id)) continue

                if (taggedIds.contains(photo.id)) {
                    exactTaggedList.add(
                        photo.copy(
                            labelBadge = "User Tagged",
                            isConfirmed = true,
                            associatedLabelId = label.id,
                            matchReason = "Tagged as '${label.name}'"
                        )
                    )
                } else if (confirmedIds.contains(photo.id)) {
                    confirmedList.add(
                        photo.copy(
                            labelBadge = "Confirmed Match",
                            isConfirmed = true,
                            associatedLabelId = label.id,
                            matchReason = "Confirmed match for '${label.name}'"
                        )
                    )
                }
            }

            // Visual similarity using all reference images
            val refHashes = references.map { it.perceptualHash }.filter { it != 0L }
            if (refHashes.isNotEmpty()) {
                val candidatePhotos = dateFilteredPhotos.filter { photo ->
                    !taggedIds.contains(photo.id) &&
                    !confirmedIds.contains(photo.id) &&
                    !rejectedIds.contains(photo.id)
                }

                val similarPhotos = visualEngine.findSimilarPhotosMultiRef(refHashes, candidatePhotos, maxDistance = 14)
                possibleMatchesList.addAll(
                    similarPhotos.map { photo ->
                        photo.copy(
                            labelBadge = "Possible Match",
                            isPossibleMatch = true,
                            associatedLabelId = label.id,
                            matchReason = "Visually similar to ${label.name}'s reference photos"
                        )
                    }
                )
            }
        }

        // OCR text matching entity name
        val processedIds = (exactTaggedList + confirmedList + possibleMatchesList).map { it.id }.toSet()
        for (photo in dateFilteredPhotos) {
            if (processedIds.contains(photo.id) || rejectedIds.contains(photo.id)) continue
            val uri = photo.uri ?: continue
            val text = ocrEngine.extractTextFromUri(uri)
            if (text.contains(entityName, ignoreCase = true)) {
                ocrMatchesList.add(
                    photo.copy(
                        labelBadge = "OCR Match",
                        ocrText = text,
                        matchReason = "Text in image contains '$entityName'"
                    )
                )
            }
        }

        // Also check if filename / title contains entity name
        val nameMatches = dateFilteredPhotos.filter { photo ->
            !processedIds.contains(photo.id) &&
            !rejectedIds.contains(photo.id) &&
            ocrMatchesList.none { it.id == photo.id } &&
            photo.title.contains(entityName, ignoreCase = true)
        }.map {
            it.copy(matchReason = "File name matches '$entityName'")
        }

        // Combine ranked: Exact & Confirmed first, then Possible Matches, then OCR & Name
        exactTaggedList + confirmedList + possibleMatchesList + ocrMatchesList + nameMatches
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

    // ----------------------------------------------------
    // People & Labels Management ("Remember This" System)
    // ----------------------------------------------------
    suspend fun createOrGetLabel(name: String, notes: String = ""): LabelEntity = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val existing = dao.getLabelByName(trimmed)
        if (existing != null) return@withContext existing

        val colorPalette = listOf("#6750A4", "#006A6A", "#7C5295", "#984061", "#4A6267", "#1E6586")
        val color = colorPalette[(trimmed.hashCode().and(0x7FFFFFFF)) % colorPalette.size]

        val id = dao.insertLabel(
            LabelEntity(
                name = trimmed,
                notes = notes,
                colorHex = color
            )
        )
        dao.getLabelById(id) ?: LabelEntity(id = id, name = trimmed, notes = notes, colorHex = color)
    }

    suspend fun addReferenceImageToLabel(labelId: Long, sourceUri: Uri): ReferenceImageEntity? = withContext(Dispatchers.IO) {
        try {
            val label = dao.getLabelById(labelId) ?: return@withContext null
            val refDir = File(context.filesDir, "references").apply { if (!exists()) mkdirs() }
            val refFile = File(refDir, "ref_${labelId}_${UUID.randomUUID()}.jpg")

            // Copy and compress bitmap safely to local storage
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@withContext null
                FileOutputStream(refFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } ?: return@withContext null

            val savedUri = Uri.fromFile(refFile)
            val hash = visualEngine.computeImageHash(savedUri)

            val refEntity = ReferenceImageEntity(
                labelId = labelId,
                uriString = savedUri.toString(),
                perceptualHash = hash
            )
            val id = dao.insertReferenceImage(refEntity)
            refEntity.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun removeReferenceImage(referenceId: Long) = withContext(Dispatchers.IO) {
        dao.deleteReferenceImage(referenceId)
    }

    suspend fun renameLabel(labelId: Long, newName: String) = withContext(Dispatchers.IO) {
        val existing = dao.getLabelById(labelId) ?: return@withContext
        dao.updateLabel(existing.copy(name = newName.trim()))
    }

    suspend fun deleteLabel(labelId: Long) = withContext(Dispatchers.IO) {
        dao.deleteReferencesForLabel(labelId)
        dao.deleteLabel(labelId)
    }

    suspend fun mergeLabels(sourceId: Long, targetId: Long) = withContext(Dispatchers.IO) {
        val refs = dao.getReferencesForLabelList(sourceId)
        for (ref in refs) {
            dao.insertReferenceImage(ref.copy(id = 0, labelId = targetId))
        }
        val assocs = dao.getAssociationsForLabel(sourceId)
        for (a in assocs) {
            dao.insertAssociation(a.copy(id = 0, labelId = targetId))
        }
        dao.deleteReferencesForLabel(sourceId)
        dao.deleteLabel(sourceId)
    }

    suspend fun confirmMatch(labelId: Long, mediaId: String) = withContext(Dispatchers.IO) {
        dao.deleteConfirmedMatch(labelId, mediaId)
        dao.insertConfirmedMatch(
            ConfirmedMatchEntity(
                labelId = labelId,
                mediaId = mediaId,
                status = "CONFIRMED"
            )
        )
        dao.insertAssociation(
            LabelMediaAssociationEntity(
                labelId = labelId,
                mediaId = mediaId,
                isAutoSuggested = false
            )
        )
    }

    suspend fun rejectMatch(labelId: Long, mediaId: String) = withContext(Dispatchers.IO) {
        dao.deleteConfirmedMatch(labelId, mediaId)
        dao.insertConfirmedMatch(
            ConfirmedMatchEntity(
                labelId = labelId,
                mediaId = mediaId,
                status = "REJECTED"
            )
        )
        dao.deleteAssociation(labelId, mediaId)
    }

    suspend fun getLabelWithDetails(labelId: Long): LabelWithDetails? = withContext(Dispatchers.Default) {
        val label = dao.getLabelById(labelId) ?: return@withContext null
        val references = dao.getReferencesForLabelList(labelId)
        val allPhotos = mediaScanner.scanPhotos(limit = 200)

        val associations = dao.getAssociationsForLabel(labelId)
        val confirmedMatches = dao.getConfirmedMatchesForLabelList(labelId)
        val confirmedIds = confirmedMatches.filter { it.status == "CONFIRMED" }.map { it.mediaId }.toSet()
        val rejectedIds = confirmedMatches.filter { it.status == "REJECTED" }.map { it.mediaId }.toSet()
        val taggedIds = associations.map { it.mediaId }.toSet()

        val confirmedPhotos = allPhotos.filter { taggedIds.contains(it.id) || confirmedIds.contains(it.id) }
            .map { it.copy(labelBadge = "Confirmed Match", isConfirmed = true, associatedLabelId = labelId) }

        val refHashes = references.map { it.perceptualHash }.filter { it != 0L }
        val candidatePhotos = allPhotos.filter {
            !taggedIds.contains(it.id) &&
            !confirmedIds.contains(it.id) &&
            !rejectedIds.contains(it.id)
        }

        val possibleMatches = if (refHashes.isNotEmpty()) {
            visualEngine.findSimilarPhotosMultiRef(refHashes, candidatePhotos, maxDistance = 14).map {
                it.copy(labelBadge = "Possible Match", isPossibleMatch = true, associatedLabelId = labelId)
            }
        } else emptyList()

        LabelWithDetails(
            label = label,
            references = references,
            confirmedPhotos = confirmedPhotos,
            possibleMatches = possibleMatches
        )
    }

    // ----------------------------------------------------
    // Diagnostics & Status
    // ----------------------------------------------------
    suspend fun getDiagnostics(): IndexDiagnostics = withContext(Dispatchers.IO) {
        val status = dao.getIndexStatus()
        IndexDiagnostics(
            totalMedia = dao.getMediaCount(),
            totalFiles = dao.getFileCount(),
            totalOcr = dao.getOcrCount(),
            totalFeatures = dao.getVisualFeatureCount(),
            totalLabels = dao.getLabelCount(),
            totalReferences = dao.getReferenceImageCount(),
            isIndexing = status?.isIndexing ?: false,
            lastIndexTime = status?.lastIndexEndTime ?: 0L,
            lastError = status?.lastError
        )
    }
}

