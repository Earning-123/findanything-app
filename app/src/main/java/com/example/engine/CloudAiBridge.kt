package com.example.engine

import com.example.model.ParsedIntent

interface AIProvider {
    val providerName: String
    val isLocal: Boolean
    suspend fun analyzeQuery(query: String): ParsedIntent
}

class LocalAIProvider : AIProvider {
    override val providerName: String = "On-Device Local AI Engine"
    override val isLocal: Boolean = true

    override suspend fun analyzeQuery(query: String): ParsedIntent {
        return IntentEngine.parse(query)
    }
}

class CloudAIProvider : AIProvider {
    override val providerName: String = "Cloud AI Assistant (Optional)"
    override val isLocal: Boolean = false

    override suspend fun analyzeQuery(query: String): ParsedIntent {
        // Fallback to local parsing; cloud proxy can be plugged in here
        return IntentEngine.parse(query)
    }
}
