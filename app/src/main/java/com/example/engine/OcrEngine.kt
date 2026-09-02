package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.FindAnythingDao
import com.example.data.local.OcrCacheEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OcrEngine(
    private val context: Context,
    private val dao: FindAnythingDao
) {
    private val recognizer by lazy {
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun extractTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        // Check cache first
        val cached = dao.getOcrText(uriStr)
        if (cached != null) return@withContext cached

        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val extracted = processInputImage(inputImage)
            if (extracted.isNotBlank()) {
                dao.insertOcr(OcrCacheEntity(uriString = uriStr, extractedText = extracted))
            }
            extracted
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            processInputImage(inputImage)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private suspend fun processInputImage(image: InputImage): String {
        val client = recognizer ?: return ""
        return suspendCancellableCoroutine { continuation ->
            client.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { error ->
                    error.printStackTrace()
                    continuation.resume("")
                }
        }
    }
}
