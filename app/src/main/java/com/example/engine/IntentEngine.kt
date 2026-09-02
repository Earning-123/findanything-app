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
        if (containsAny(lower, "delete", "hatao", "remove", "delete karo") && !lower.contains("kholo")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.DELETE_ITEM,
                targetAction = "DELETE",
                explanation = "Action: Delete requested"
            )
        }
        if (containsAny(lower, "share karo", "share this", "bhejo")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SHARE_ITEM,
                targetAction = "SHARE",
                explanation = "Action: Share requested"
            )
        }

        // 2. Visual similarity search query ("Is photo jaisi photos", "similar photos")
        if (containsAny(lower, "is photo jaisi", "aisi photos", "similar photo", "similar photos", "similar images", "similar picture")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_PHOTO,
                targetAction = "FIND_SIMILAR",
                explanation = "Visual similarity search requested"
            )
        }

        // 3. Duplicate detection intent
        if (containsAny(lower, "duplicate", "duplicates", "ek jaisi", "same photo", "near-duplicate", "cleanup")) {
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_DUPLICATES,
                explanation = "Looking for duplicate and similar photos"
            )
        }

        // 4. Large files intent
        if (containsAny(lower, "large file", "large files", "larger", "badi file", "bade video", "heavy file", "size", "heavy")) {
            val minSize = extractSizeFromQuery(lower) ?: (100L * 1024 * 1024) // default 100MB
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_LARGE_FILES,
                minSizeBytes = minSize,
                explanation = "Searching for large files (${minSize / (1024 * 1024)} MB+)"
            )
        }

        // 5. Contact / Phone number intent
        val phoneRegex = Regex("\\b\\d{10}\\b")
        val phoneMatch = phoneRegex.find(lower)
        if (phoneMatch != null || (containsAny(lower, "contact", "number", "ka number", "phone number", "call") && !containsAny(lower, "photo", "screenshot", "pdf"))) {
            val person = extractPersonName(lower, "number", "contact", "call")
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_CONTACT,
                targetPerson = person,
                searchTerms = listOfNotNull(phoneMatch?.value, person).filter { it.isNotBlank() },
                explanation = "Searching contacts for ${phoneMatch?.value ?: person ?: "numbers"}"
            )
        }

        // 6. App Launch intent ("Calculator kholo", "Open YouTube", "WhatsApp open karo")
        if (containsAny(lower, "kholo", "open karo", "open", "launch", "chalao") &&
            !containsAny(lower, "photo", "photos", "file", "files", "pdf", "video", "screenshot", "latest")
        ) {
            val appKeyword = cleanAppQuery(lower)
            return ParsedIntent(
                rawQuery = query,
                intentType = IntentType.SEARCH_APP,
                searchTerms = listOf(appKeyword),
                explanation = "Looking for application '$appKeyword' to open"
            )
        }

        // 7. Date extraction (Kal, Yesterday, Aaj, Today, Last month, Year e.g. 2026)
        val dateFilter = extractDateFilter(lower)
        val targetYear = extractYear(lower)

        // 8. Amount extraction (₹5000, 5000, 5k, Rs 5000)
        val amount = extractAmount(lower)

        // 9. Screenshot detection
        val isScreenshot = containsAny(lower, "screenshot", "screen shot", "payment", "upi", "gpay", "phonepe", "paytm")

        // 10. Document / PDF detection
        val isDoc = containsAny(lower, "pdf", "document", "doc", "docx", "aadhaar", "pan", "bill", "receipt", "insurance", "ticket")

        // 11. Photo / Video detection
        val isPhoto = containsAny(lower, "photo", "photos", "tasveer", "picture", "image", "pic", "pics")
        val isVideo = containsAny(lower, "video", "videos")

        // 12. Check if query asks to OPEN latest photo ("Rahul ki latest photo kholo")
        val isOpenAction = containsAny(lower, "kholo", "open karo", "open") && (isPhoto || lower.contains("latest"))

        // 13. Entity / Person extraction (e.g. "Rahul", "Ammi", "Aadhaar")
        val targetPerson = extractPersonEntity(lower)

        // Construct clean search terms
        val cleanedTerms = extractMeaningfulKeywords(lower)

        val intentType = when {
            targetPerson != null && isPhoto -> IntentType.SEARCH_ENTITY
            amount != null || isScreenshot -> IntentType.SEARCH_OCR
            isVideo -> IntentType.SEARCH_VIDEO
            isPhoto -> IntentType.SEARCH_PHOTO
            isDoc -> IntentType.SEARCH_DOCUMENT
            targetPerson != null -> IntentType.SEARCH_ENTITY
            else -> IntentType.SEARCH_ALL
        }

        val explanation = buildString {
            append("Searching ")
            if (targetPerson != null) append("for '$targetPerson' ")
            if (isScreenshot) append("screenshots ")
            if (amount != null) append("for amount ₹$amount ")
            if (dateFilter != null) append("from ${dateFilter.label} ")
            if (targetYear != null) append("in year $targetYear ")
            if (cleanedTerms.isNotEmpty()) append("matching: ${cleanedTerms.joinToString(", ")}")
            if (isOpenAction) append(" (will open latest)")
        }.trim()

        return ParsedIntent(
            rawQuery = query,
            intentType = intentType,
            searchTerms = cleanedTerms,
            targetPerson = targetPerson,
            targetAmount = amount,
            targetDateFilter = dateFilter,
            targetYear = targetYear,
            targetAction = if (isOpenAction) "OPEN_LATEST" else null,
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
            else -> {
                val year = extractYear(text)
                if (year != null) {
                    val yearCal = Calendar.getInstance()
                    yearCal.set(Calendar.YEAR, year)
                    yearCal.set(Calendar.MONTH, Calendar.JANUARY)
                    yearCal.set(Calendar.DAY_OF_MONTH, 1)
                    yearCal.set(Calendar.HOUR_OF_DAY, 0)
                    yearCal.set(Calendar.MINUTE, 0)
                    yearCal.set(Calendar.SECOND, 0)
                    val start = yearCal.timeInMillis

                    yearCal.set(Calendar.MONTH, Calendar.DECEMBER)
                    yearCal.set(Calendar.DAY_OF_MONTH, 31)
                    yearCal.set(Calendar.HOUR_OF_DAY, 23)
                    yearCal.set(Calendar.MINUTE, 59)
                    yearCal.set(Calendar.SECOND, 59)
                    val end = yearCal.timeInMillis
                    DateFilter(DateFilterType.SPECIFIC_YEAR, start, end, "Year $year")
                } else null
            }
        }
    }

    private fun extractYear(text: String): Int? {
        val yearRegex = Regex("\\b(20[1-3][0-9])\\b")
        val match = yearRegex.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun extractAmount(text: String): String? {
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

    private fun extractPersonEntity(text: String): String? {
        // e.g. "Rahul ki photos", "Ammi ki photo", "Rahul ki 2026 wali photos"
        val pattern = Regex("([a-zA-Z0-9]+)\\s+(?:ki|ka|ke)\\s+(?:photo|photos|tasveer|image|images|video|videos|doc|document|screenshot)", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) {
            val candidate = match.groupValues[1]
            val nonEntities = setOf("kal", "aaj", "pichle", "last", "meri", "mera", "mere", "is", "ye", "wo")
            if (candidate.lowercase(Locale.ROOT) !in nonEntities) {
                return candidate.replaceFirstChar { it.uppercase() }
            }
        }
        return null
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
            "photo", "photos", "file", "files", "video", "videos", "document", "documents",
            "dhoondo", "dhoondho", "latest", "wali"
        )
        return text.split("[\\s,]+".toRegex())
            .map { it.replace("[₹,.]".toRegex(), "").trim() }
            .filter { it.length > 1 && it !in stopWords }
    }
}

