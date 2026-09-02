package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.ItemType
import com.example.model.SearchItem
import java.io.File

class ActionEngine(private val context: Context) {

    fun openItem(item: SearchItem): Boolean {
        return try {
            when (item.type) {
                ItemType.APP -> {
                    if (item.packageName != null) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return true
                        }
                    }
                    false
                }
                ItemType.CONTACT -> {
                    if (!item.phoneNumber.isNullOrBlank()) {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${item.phoneNumber}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dialIntent)
                        return true
                    } else if (item.uri != null) {
                        val viewIntent = Intent(Intent.ACTION_VIEW, item.uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(viewIntent)
                        return true
                    }
                    false
                }
                ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.FILE -> {
                    val uri = item.uri ?: getUriForFile(item.filePath) ?: return false
                    val mime = if (item.mimeType.isNotBlank()) item.mimeType else "*/*"

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this item", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareItem(item: SearchItem): Boolean {
        return try {
            when (item.type) {
                ItemType.CONTACT -> {
                    val text = "${item.title}: ${item.phoneNumber}"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Contact").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    true
                }
                else -> {
                    val uri = item.uri ?: getUriForFile(item.filePath) ?: return false
                    val mime = if (item.mimeType.isNotBlank()) item.mimeType else "*/*"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share ${item.title}").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    true
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share item: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    suspend fun deleteItem(item: SearchItem): Boolean {
        return try {
            var deleted = false
            if (item.uri != null) {
                try {
                    val rows = context.contentResolver.delete(item.uri, null, null)
                    deleted = rows > 0
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!deleted && !item.filePath.isNullOrBlank()) {
                val file = File(item.filePath)
                if (file.exists()) {
                    deleted = file.delete()
                }
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copyText(text: String, label: String = "Text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun getUriForFile(filePath: String?): Uri? {
        if (filePath.isNullOrBlank()) return null
        return try {
            val file = File(filePath)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(File(filePath))
        }
    }
}
