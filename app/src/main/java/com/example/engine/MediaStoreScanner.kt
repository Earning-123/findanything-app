package com.example.engine

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.ItemType
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val context: Context) {

    suspend fun scanPhotos(limit: Int = 300): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Photo_$id"
                    val dateSec = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val isScreenshot = (name.contains("screenshot", ignoreCase = true) ||
                            (path != null && path.contains("screenshot", ignoreCase = true)))

                    results.add(
                        SearchItem(
                            id = "photo_$id",
                            title = name,
                            subtitle = if (isScreenshot) "Screenshot • $mime" else mime,
                            type = ItemType.PHOTO,
                            uri = uri,
                            filePath = path,
                            sizeBytes = size,
                            dateModified = dateSec * 1000L,
                            mimeType = mime,
                            isScreenshot = isScreenshot
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun scanVideos(limit: Int = 100): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val dateSec = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    results.add(
                        SearchItem(
                            id = "video_$id",
                            title = name,
                            subtitle = mime,
                            type = ItemType.VIDEO,
                            uri = uri,
                            filePath = path,
                            sizeBytes = size,
                            dateModified = dateSec * 1000L,
                            mimeType = mime
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun scanDocumentsAndFiles(limit: Int = 200): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )

        // Query documents, pdfs, txt, docs, downloads
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%pdf%", "%text%", "%.pdf", "%.doc%")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            val contentUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "File_$id"
                    val dateSec = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(contentUri, id)

                    val isDoc = name.endsWith(".pdf", ignoreCase = true) ||
                            name.endsWith(".doc", ignoreCase = true) ||
                            name.endsWith(".docx", ignoreCase = true) ||
                            name.endsWith(".txt", ignoreCase = true) ||
                            mime.contains("pdf") || mime.contains("document")

                    results.add(
                        SearchItem(
                            id = "file_$id",
                            title = name,
                            subtitle = if (isDoc) "Document • $mime" else mime,
                            type = if (isDoc) ItemType.DOCUMENT else ItemType.FILE,
                            uri = uri,
                            filePath = path,
                            sizeBytes = size,
                            dateModified = dateSec * 1000L,
                            mimeType = mime
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }
}
