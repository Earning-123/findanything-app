package com.example.engine

import com.example.model.DateFilter
import com.example.model.DateFilterType
import com.example.model.IntentType
import com.example.model.ParsedIntent
import java.util.Calendar
import java.util.Locale

object IntentEngine {

    fun parse(rawQuery: String): ParsedIntent {
        val query = rawQuery.trim()
        val lower = query.lowercase(Locale.getDefault())

        // 1. Detect destructive / immediate actions
        if (containsAny(lower, "delete", "hatao", "remove", "delete karo")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.DELETE_ITEM,
                explanation = "Action: Delete requested"
            )
        }
        if (containsAny(lower, "share karo", "share this", "bhejo")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SHARE_ITEM,
                explanation = "Action: Share requested"
            )
        }

        // 2. Duplicate detection intent
        if (containsAny(lower, "duplicate", "duplicates", "ek jaisi", "same photo", "near-duplicate")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_DUPLICATES,
                explanation = "Looking for duplicate and similar photos"
            )
        }

        // 3. Large files intent
        if (containsAny(lower, "large file", "large", "larger", "badi file", "bade video", "heavy file", "size", "heavy")) {
            val minSize = extractSizeFromQuery(lower) ?: (100L * 1024 * 1024) // default 100MB
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_LARGE_FILES,
                minSizeBytes = minSize,
                explanation = "Searching for large files (${minSize / (1024 * 1024)} MB+)"
            )
        }

        // 4. Contact / Phone number intent
        val phoneRegex = Regex("\\b\\d{10}\\b")
        val phoneMatch = phoneRegex.find(lower)
        if (phoneMatch != null || containsAny(lower, "contact", "number", "ka number", "phone number", "call")) {
            val person = extractPersonName(lower, "number", "contact", "call")
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_CONTACT,
                targetPerson = person,
                searchTerms = listOfNotNull(phoneMatch?.value, person).filter { it.isNotBlank() },
                explanation = "Searching contacts for ${phoneMatch?.value ?: person ?: "numbers"}"
            )
        }

        // 5. App Launch intent ("Calculator kholo", "Open YouTube", "WhatsApp open karo", "Settings")
        if (containsAny(lower, "kholo", "open karo", "open", "launch", "chalao") &&
            !containsAny(lower, "photo", "file", "pdf", "video", "screenshot")
        ) {
            val appKeyword = cleanAppQuery(lower)
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_APP,
                searchTerms = listOf(appKeyword),
                explanation = "Looking for application '$appKeyword' to open"
            )
        }

        // 6. Date extraction (Kal, Yesterday, Aaj, Today, Last month, January, etc.)
        val dateFilter = extractDateFilter(lower)

        // 7. Amount extraction (₹5000, 5000, 5k, Rs 5000)
        val amount = extractAmount(lower)

        // 8. Screenshot detection
        val isScreenshot = containsAny(lower, "screenshot", "screen shot", "payment", "upi", "gpay", "phonepe", "paytm")

        // 9. Document / PDF detection
        val isDoc = containsAny(lower, "pdf", "document", "doc", "docx", "aadhaar", "pan", "bill", "receipt", "insurance", "ticket")

        // 10. Photo / Video detection
        val isPhoto = containsAny(lower, "photo", "photos", "tasveer", "picture", "image", "pic", "pics")
        val isVideo = containsAny(lower, "video", "videos")

        // Construct clean search terms by stripping common conversational words
        val cleanedTerms = extractMeaningfulKeywords(lower)

        val intentType = when {
            amount != null || isScreenshot -> IntentType.SEARCH_OCR
            isVideo -> IntentType.SEARCH_VIDEO
            isPhoto -> IntentType.SEARCH_PHOTO
            isDoc -> IntentType.SEARCH_DOCUMENT
            else -> IntentType.SEARCH_ALL
        }

        val explanation = buildString {
            append("Searching ")
            if (isScreenshot) append("screenshots ")
            if (amount != null) append("for amount ₹$amount ")
            if (dateFilter != null) append("from ${dateFilter.label} ")
            if (cleanedTerms.isNotEmpty()) append("matching: ${cleanedTerms.joinToString(", ")}")
        }.trim()

        return ParsedIntent(
            rawQuery = query,
            intentType = intentType,
            searchTerms = cleanedTerms,
            targetAmount = amount,
            targetDateFilter = dateFilter,
            isScreenshotTargeted = isScreenshot,
            targetFileType = if (isDoc && lower.contains("pdf")) "pdf" else null,
            explanation = explanation
        )
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun extractDateFilter(text: String): DateFilter? {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when {
            text.contains("kal") || text.contains("yesterday") -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                DateFilter(DateFilterType.YESTERDAY, start, end, "Yesterday")
            }
            text.contains("aaj") || text.contains("today") -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                DateFilter(DateFilterType.TODAY, start, now, "Today")
            }
            text.contains("last month") || text.contains("pichle mahine") -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                val end = cal.timeInMillis
                DateFilter(DateFilterType.LAST_MONTH, start, end, "Last Month")
            }
            text.contains("last week") || text.contains("pichle hafte") -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                DateFilter(DateFilterType.LAST_WEEK, cal.timeInMillis, now, "Last 7 Days")
            }
            else -> null
        }
    }

    private fun extractAmount(text: String): String? {
        // Match ₹5000, Rs 5000, 5000 rs, 5000 rupees, 5k
        val rupeeSymbolRegex = Regex("(?:[₹]|rs\\.?|rupees?\\s*)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        rupeeSymbolRegex.find(text)?.let { return it.groupValues[1] }

        val kRegex = Regex("(\\d+)\\s*k\\b", RegexOption.IGNORE_CASE)
        kRegex.find(text)?.let {
            val num = it.groupValues[1].toLongOrNull() ?: 0L
            return (num * 1000).toString()
        }

        val standaloneNumberRegex = Regex("\\b(\\d{3,6})\\b")
        val numberMatch = standaloneNumberRegex.find(text)
        if (numberMatch != null && (text.contains("payment") || text.contains("rupaye") || text.contains("screenshot") || text.contains("bill"))) {
            return numberMatch.value
        }
        return null
    }

    private fun extractSizeFromQuery(text: String): Long? {
        val mbRegex = Regex("(\\d+)\\s*(?:mb|gb)", RegexOption.IGNORE_CASE)
        val match = mbRegex.find(text) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        return if (match.value.lowercase(Locale.ROOT).contains("gb")) {
            value * 1024L * 1024L * 1024L
        } else {
            value * 1024L * 1024L
        }
    }

    private fun extractPersonName(text: String, vararg excludeKeywords: String): String? {
        val words = text.split("\\s+".toRegex())
        val stopWords = setOf("find", "karo", "dhoondho", "dikhao", "show", "me", "ka", "ki", "ke", "wala", "wali", "related", "se", "hai", "hain", "to") + excludeKeywords.toSet()
        return words.firstOrNull { it !in stopWords && it.length > 2 && it.none { char -> char.isDigit() } }
    }

    private fun cleanAppQuery(text: String): String {
        return text
            .replace("kholo", "")
            .replace("open karo", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("chalao", "")
            .replace("app", "")
            .trim()
    }

    private fun extractMeaningfulKeywords(text: String): List<String> {
        val stopWords = setOf(
            "dikhao", "karo", "find", "show", "me", "wala", "wali", "wale", "ki", "ka", "ke",
            "search", "open", "kholo", "se", "hai", "hain", "ko", "in", "on", "at", "the",
            "my", "mera", "meri", "mere", "all", "sab", "saare", "bhi", "aur", "and", "or",
            "photo", "photos", "file", "files", "video", "videos", "document", "documents"
        )
        return text.split("[\\s,]+".toRegex())
            .map { it.replace("[₹,.]".toRegex(), "").trim() }
            .filter { it.length > 1 && it !in stopWords }
    }
}
