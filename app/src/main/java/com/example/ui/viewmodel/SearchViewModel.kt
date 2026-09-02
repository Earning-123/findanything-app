package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.SearchRepository
import com.example.engine.ActionEngine
import com.example.engine.VoiceEngine
import com.example.engine.VoiceState
import com.example.model.DuplicateCluster
import com.example.model.ItemType
import com.example.model.ParsedIntent
import com.example.model.SearchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val parsedIntent: ParsedIntent? = null,
    val rawResults: List<SearchItem> = emptyList(),
    val activeFilter: ItemType? = null,
    val selectedItemForAction: SearchItem? = null,
    val showDeleteConfirm: Boolean = false,
    val itemToDelete: SearchItem? = null,
    val duplicateClusters: List<DuplicateCluster> = emptyList(),
    val isScanningDuplicates: Boolean = false,
    val referenceImageUri: Uri? = null,
    val referenceOcrText: String? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class SearchViewModel(
    val repository: SearchRepository,
    val actionEngine: ActionEngine,
    val voiceEngine: VoiceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceState: StateFlow<VoiceState> = voiceEngine.voiceState
    val soundLevel: StateFlow<Float> = voiceEngine.soundLevel

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery, errorMessage = null)
    }

    fun executeSearch(query: String? = null) {
        val q = (query ?: _uiState.value.query).trim()
        if (q.isBlank()) return

        _uiState.value = _uiState.value.copy(
            query = q,
            isSearching = true,
            hasSearched = true,
            errorMessage = null,
            statusMessage = "Analyzing & searching phone..."
        )

        viewModelScope.launch {
            try {
                val (intent, items) = repository.executeSearch(q)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    parsedIntent = intent,
                    rawResults = items,
                    statusMessage = if (items.isEmpty()) "No matching items found." else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = "Search failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun setFilter(filter: ItemType?) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun startVoiceSearch() {
        voiceEngine.reset()
        voiceEngine.startListening()
    }

    fun stopVoiceSearch() {
        voiceEngine.stopListening()
    }

    fun onVoiceResultReceived(spokenText: String) {
        _uiState.value = _uiState.value.copy(query = spokenText)
        executeSearch(spokenText)
    }

    fun searchWithReferenceImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            isSearching = true,
            hasSearched = true,
            referenceImageUri = uri,
            statusMessage = "Analyzing reference image..."
        )

        viewModelScope.launch {
            try {
                val (extractedText, ocrMatches) = repository.searchByReferenceOcr(uri)
                val visualMatches = repository.searchByReferenceImage(uri)
                val combined = (visualMatches + ocrMatches).distinctBy { it.id }

                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    rawResults = combined,
                    referenceOcrText = extractedText.ifBlank { null },
                    statusMessage = if (combined.isEmpty()) "No visually similar photos or matching text found." else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = "Failed to process reference: ${e.localizedMessage}"
                )
            }
        }
    }

    fun loadSmartCollection(name: String) {
        _uiState.value = _uiState.value.copy(
            isSearching = true,
            hasSearched = true,
            query = name,
            statusMessage = "Loading $name collection..."
        )

        viewModelScope.launch {
            try {
                val items = repository.getCollection(name)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    rawResults = items,
                    statusMessage = if (items.isEmpty()) "Collection is empty." else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = "Failed to load collection: ${e.localizedMessage}"
                )
            }
        }
    }

    fun scanDuplicates() {
        _uiState.value = _uiState.value.copy(isScanningDuplicates = true)
        viewModelScope.launch {
            try {
                val photos = repository.getCollection("Photos")
                val clusters = repository.visualEngine.detectDuplicates(photos)
                _uiState.value = _uiState.value.copy(
                    duplicateClusters = clusters,
                    isScanningDuplicates = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanningDuplicates = false,
                    errorMessage = "Failed to scan duplicates: ${e.localizedMessage}"
                )
            }
        }
    }

    fun requestDeleteItem(item: SearchItem) {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirm = true,
            itemToDelete = item
        )
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirm = false,
            itemToDelete = null
        )
    }

    fun confirmDeleteItem() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            val success = actionEngine.deleteItem(item)
            if (success) {
                val updated = _uiState.value.rawResults.filter { it.id != item.id }
                _uiState.value = _uiState.value.copy(
                    rawResults = updated,
                    showDeleteConfirm = false,
                    itemToDelete = null,
                    statusMessage = "Item deleted successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    showDeleteConfirm = false,
                    itemToDelete = null,
                    errorMessage = "Could not delete item. Android permission or file access may be restricted."
                )
            }
        }
    }

    fun openItem(item: SearchItem) {
        actionEngine.openItem(item)
    }

    fun shareItem(item: SearchItem) {
        actionEngine.shareItem(item)
    }

    fun copyText(text: String, label: String = "Text") {
        actionEngine.copyText(text, label)
    }

    fun deleteRecentSearch(id: Long) {
        viewModelScope.launch {
            repository.deleteSearchQuery(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun clearIndexAndCache() {
        viewModelScope.launch {
            repository.clearIndexAndCache()
            _uiState.value = _uiState.value.copy(
                rawResults = emptyList(),
                hasSearched = false,
                statusMessage = "Local index & OCR cache cleared."
            )
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            query = "",
            hasSearched = false,
            rawResults = emptyList(),
            parsedIntent = null,
            referenceImageUri = null,
            referenceOcrText = null,
            activeFilter = null
        )
    }
}

class SearchViewModelFactory(
    private val repository: SearchRepository,
    private val actionEngine: ActionEngine,
    private val voiceEngine: VoiceEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository, actionEngine, voiceEngine) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
