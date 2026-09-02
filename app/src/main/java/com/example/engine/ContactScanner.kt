package com.example.engine

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.model.ItemType
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactScanner(private val context: Context) {

    suspend fun searchContacts(query: String, limit: Int = 30): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext results
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val cleanQuery = query.trim()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%", "%$cleanQuery%")

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                if (cleanQuery.isBlank()) null else selection,
                if (cleanQuery.isBlank()) null else selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Contact"
                    val number = cursor.getString(numCol) ?: ""
                    val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)

                    results.add(
                        SearchItem(
                            id = "contact_$id",
                            title = name,
                            subtitle = number,
                            type = ItemType.CONTACT,
                            uri = contactUri,
                            phoneNumber = number
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
