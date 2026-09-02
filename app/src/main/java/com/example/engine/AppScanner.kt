package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.model.ItemType
import com.example.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppScanner(private val context: Context) {

    suspend fun getInstalledApps(): List<SearchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchItem>()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        try {
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                val appName = info.loadLabel(pm).toString()
                val pkgName = info.activityInfo.packageName

                // Filter out self if needed
                if (pkgName == context.packageName) continue

                results.add(
                    SearchItem(
                        id = "app_$pkgName",
                        title = appName,
                        subtitle = "Installed App • $pkgName",
                        type = ItemType.APP,
                        packageName = pkgName
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun findMatchingApps(query: String): List<SearchItem> {
        val apps = getInstalledApps()
        val q = query.trim().lowercase()
        return apps.filter {
            it.title.lowercase().contains(q) ||
                    it.packageName?.lowercase()?.contains(q) == true ||
                    // Common synonyms
                    (q.contains("calc") && it.title.lowercase().contains("calc")) ||
                    (q.contains("camera") && it.title.lowercase().contains("cam")) ||
                    (q.contains("setting") && it.title.lowercase().contains("sett"))
        }
    }
}
