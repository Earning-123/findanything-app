package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LabelEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.SearchRepository
import com.example.engine.ActionEngine
import com.example.engine.VoiceEngine
import com.example.engine.VoiceState
import com.example.model.DuplicateCluster
import com.example.model.IndexDiagnostics
import com.example.model.ItemType
import com.example.model.LabelWithDetails
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

    val allLabels: StateFlow<List<LabelEntity>> = repository.allLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeLabelDetails = MutableStateFlow<LabelWithDetails?>(null)
    val activeLabelDetails: StateFlow<LabelWithDetails?> = _activeLabelDetails.asStateFlow()

    private val _diagnostics = MutableStateFlow(IndexDiagnostics())
    val diagnostics: StateFlow<IndexDiagnostics> = _diagnostics.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.voiceState
    val soundLevel: StateFlow<Float> = voiceEngine.soundLevel

    init {
        // Kick off incremental background indexing on startup
        triggerIncrementalIndex()
        loadDiagnostics()
    }

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

    fun rememberAsReference(labelName: String, uri: Uri) {
        if (labelName.isBlank()) return
        viewModelScope.launch {
            try {
                val label = repository.createOrGetLabel(labelName.trim())
                repository.addReferenceImageToLabel(label.id, uri)
                _uiState.value = _uiState.value.copy(statusMessage = "Saved as reference for '${label.name}'")
                // Trigger visual search for this label
                searchWithReferenceImage(uri)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Could not save reference: ${e.localizedMessage}")
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
            loadDiagnostics()
            _uiState.value = _uiState.value.copy(
                rawResults = emptyList(),
                hasSearched = false,
                statusMessage = "Local index & OCR cache cleared."
            )
        }
    }

    fun clearAllRememberedData() {
        viewModelScope.launch {
            repository.clearAllRememberedData()
            _activeLabelDetails.value = null
            loadDiagnostics()
            _uiState.value = _uiState.value.copy(statusMessage = "All remembered entities and references deleted.")
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

    // ----------------------------------------------------
    // People & Labels Management Methods
    // ----------------------------------------------------
    fun loadLabelDetails(labelId: Long) {
        viewModelScope.launch {
            val details = repository.getLabelWithDetails(labelId)
            _activeLabelDetails.value = details
        }
    }

    fun clearActiveLabelDetails() {
        _activeLabelDetails.value = null
    }

    fun createLabel(name: String, notes: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = repository.createOrGetLabel(name, notes)
            loadLabelDetails(created.id)
            loadDiagnostics()
        }
    }

    fun addReferenceToLabel(labelId: Long, uri: Uri) {
        viewModelScope.launch {
            repository.addReferenceImageToLabel(labelId, uri)
            loadLabelDetails(labelId)
            loadDiagnostics()
        }
    }

    fun removeReference(referenceId: Long, labelId: Long) {
        viewModelScope.launch {
            repository.removeReferenceImage(referenceId)
            loadLabelDetails(labelId)
            loadDiagnostics()
        }
    }

    fun renameLabel(labelId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameLabel(labelId, newName)
            loadLabelDetails(labelId)
        }
    }

    fun deleteLabel(labelId: Long) {
        viewModelScope.launch {
            repository.deleteLabel(labelId)
            _activeLabelDetails.value = null
            loadDiagnostics()
        }
    }

    fun mergeLabels(sourceId: Long, targetId: Long) {
        viewModelScope.launch {
            repository.mergeLabels(sourceId, targetId)
            loadLabelDetails(targetId)
            loadDiagnostics()
        }
    }

    fun confirmMatch(labelId: Long, mediaId: String) {
        viewModelScope.launch {
            repository.confirmMatch(labelId, mediaId)
            // Update rawResults in active search if present
            val updated = _uiState.value.rawResults.map {
                if (it.id == mediaId) it.copy(labelBadge = "Confirmed Match", isConfirmed = true, isPossibleMatch = false)
                else it
            }
            _uiState.value = _uiState.value.copy(rawResults = updated)
            loadLabelDetails(labelId)
        }
    }

    fun rejectMatch(labelId: Long, mediaId: String) {
        viewModelScope.launch {
            repository.rejectMatch(labelId, mediaId)
            // Remove from rawResults if rejecting
            val updated = _uiState.value.rawResults.filter { it.id != mediaId }
            _uiState.value = _uiState.value.copy(rawResults = updated)
            loadLabelDetails(labelId)
        }
    }

    // ----------------------------------------------------
    // Diagnostics & Indexing Operations
    // ----------------------------------------------------
    fun loadDiagnostics() {
        viewModelScope.launch {
            val diag = repository.getDiagnostics()
            _diagnostics.value = diag
        }
    }

    fun triggerIncrementalIndex() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Indexing phone content...")
            repository.indexDeviceContent(forceRebuild = false)
            loadDiagnostics()
            _uiState.value = _uiState.value.copy(statusMessage = null)
        }
    }

    fun triggerRebuildIndex() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Rebuilding local index from scratch...")
            repository.indexDeviceContent(forceRebuild = true)
            loadDiagnostics()
            _uiState.value = _uiState.value.copy(statusMessage = "Index rebuilt successfully!")
        }
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

