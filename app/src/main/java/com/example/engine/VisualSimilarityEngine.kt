package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.DuplicateCluster
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class VisualSimilarityEngine(private val context: Context) {

    suspend fun computeImageHash(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4 // Downsample for memory safety
                }
                val bitmap = BitmapFactory.decodeStream(stream, null, options) ?: return@use 0L
                computeBitmapHash(bitmap)
            } ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    fun computeBitmapHash(bitmap: Bitmap): Long {
        return try {
            // Resize to 8x8
            val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            val pixels = IntArray(64)
            scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)

            // Convert to grayscale and compute average
            var sum = 0L
            val grays = LongArray(64)
            for (i in 0 until 64) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (r * 299 + g * 587 + b * 114) / 1000L
                grays[i] = gray
                sum += gray
            }
            val avg = sum / 64

            // Generate 64-bit hash
            var hash = 0L
            for (i in 0 until 64) {
                if (grays[i] >= avg) {
                    hash = hash or (1L shl i)
                }
            }
            hash
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int {
        var x = hash1 xor hash2
        var dist = 0
        while (x != 0L) {
            dist += (x and 1L).toInt()
            x = x ushr 1
        }
        return dist
    }

    suspend fun findSimilarPhotos(referenceHash: Long, photoCandidates: List<SearchItem>, maxDistance: Int = 10): List<SearchItem> = withContext(Dispatchers.Default) {
        if (referenceHash == 0L) return@withContext emptyList()
        val scored = mutableListOf<Pair<SearchItem, Int>>()

        for (photo in photoCandidates) {
            val hash = if (photo.visualHash != 0L) photo.visualHash else {
                photo.uri?.let { computeImageHash(it) } ?: 0L
            }
            if (hash != 0L) {
                val dist = hammingDistance(referenceHash, hash)
                if (dist <= maxDistance) {
                    val similarityPercent = ((64 - dist) * 100) / 64
                    val matchReason = "Visual Match ($similarityPercent% similar)"
                    scored.add(photo.copy(visualHash = hash, matchReason = matchReason) to dist)
                }
            }
        }
        scored.sortBy { it.second }
        scored.map { it.first }
    }

    suspend fun detectDuplicates(photos: List<SearchItem>): List<DuplicateCluster> = withContext(Dispatchers.Default) {
        val clusters = mutableListOf<DuplicateCluster>()
        val processed = mutableSetOf<String>()

        // Precompute or retrieve hashes
        val withHashes = photos.map { photo ->
            if (photo.visualHash != 0L) photo else {
                val h = photo.uri?.let { computeImageHash(it) } ?: 0L
                photo.copy(visualHash = h)
            }
        }.filter { it.visualHash != 0L }

        for (i in withHashes.indices) {
            val itemA = withHashes[i]
            if (processed.contains(itemA.id)) continue

            val duplicates = mutableListOf<SearchItem>()
            for (j in i + 1 until withHashes.size) {
                val itemB = withHashes[j]
                if (processed.contains(itemB.id)) continue

                // Exact or near-duplicate (distance <= 4)
                val dist = hammingDistance(itemA.visualHash, itemB.visualHash)
                if (dist <= 4) {
                    val sim = ((64 - dist) * 100) / 64
                    duplicates.add(itemB.copy(matchReason = if (dist == 0) "Exact duplicate" else "Near duplicate ($sim%)"))
                    processed.add(itemB.id)
                }
            }

            if (duplicates.isNotEmpty()) {
                processed.add(itemA.id)
                val reclaimable = duplicates.sumOf { it.sizeBytes }
                clusters.add(
                    DuplicateCluster(
                        hash = itemA.visualHash,
                        original = itemA,
                        duplicates = duplicates,
                        totalReclaimableBytes = reclaimable
                    )
                )
            }
        }

        clusters.sortedByDescending { it.totalReclaimableBytes }
    }
}
