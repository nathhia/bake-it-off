package com.bakeitoff.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bakeitoff.data.gemini.ApiKeyManager
import com.bakeitoff.data.gemini.MediaPreparer
import com.bakeitoff.data.model.Recipe
import com.bakeitoff.data.notion.NotionRepository
import com.bakeitoff.service.RecipeExtractionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecipeViewModel(
    application: Application,
    private val mediaPreparer: MediaPreparer,
    private val notionRepository: NotionRepository,
    private val apiKeyManager: ApiKeyManager
) : AndroidViewModel(application) {

    // The pipeline itself runs in RecipeExtractionService (see its class doc) — this just
    // mirrors its state, so it keeps updating correctly even if this screen wasn't open
    // when the extraction finished.
    val uiState: StateFlow<RecipeUiState> = RecipeExtractionState.uiState

    val isCompressing: StateFlow<Boolean> = mediaPreparer.isCompressing
    val compressionProgress: StateFlow<Float> = mediaPreparer.compressionProgress

    // Called by the UI (BackHandler) while uiState is Uploading/Extracting, since until
    // now there was no way to back out if the user changed their mind.
    fun cancelProcessing() {
        val intent = Intent(getApplication(), RecipeExtractionService::class.java).apply {
            action = RecipeExtractionService.ACTION_CANCEL
        }
        getApplication<Application>().startService(intent)
    }

    fun processLink(url: String) {
        val intent = Intent(getApplication(), RecipeExtractionService::class.java).apply {
            action = RecipeExtractionService.ACTION_PROCESS_LINK
            putExtra(RecipeExtractionService.EXTRA_URL, url)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun resetToInitial() {
        RecipeExtractionState.resetToInitial()
    }

    fun processMediaUris(uris: List<Uri>, linkText: String?, extraPrompt: String?) {
        val intent = Intent(getApplication(), RecipeExtractionService::class.java).apply {
            action = RecipeExtractionService.ACTION_PROCESS_MEDIA
            putParcelableArrayListExtra(RecipeExtractionService.EXTRA_URIS, ArrayList(uris))
            putExtra(RecipeExtractionService.EXTRA_LINK_TEXT, linkText)
            putExtra(RecipeExtractionService.EXTRA_EXTRA_PROMPT, extraPrompt)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun createRecipeFromText(description: String) {
        val intent = Intent(getApplication(), RecipeExtractionService::class.java).apply {
            action = RecipeExtractionService.ACTION_CREATE_FROM_TEXT
            putExtra(RecipeExtractionService.EXTRA_DESCRIPTION, description)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    private val _isSavingToNotion = mutableStateOf(false)
    val isSavingToNotion: State<Boolean> = _isSavingToNotion

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    // Function the list screen calls when a card is clicked
    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
    }

    // Function to clear it when leaving the detail screen
    fun clearSelectedRecipe() {
        _selectedRecipe.value = null
    }

    // State holding the list of recipes that comes from Notion
    private val _savedRecipes = MutableStateFlow<List<Recipe>>(emptyList())
    val savedRecipes: StateFlow<List<Recipe>> = _savedRecipes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // State controlling whether the favorites filter is active
    private val _isFavoriteFilter = MutableStateFlow(false)
    val isFavoriteFilter = _isFavoriteFilter.asStateFlow()

    fun toggleFavoriteFilter() {
        _isFavoriteFilter.value = !_isFavoriteFilter.value
    }

    private val _selectedStatus = MutableStateFlow<String?>(null)
    val selectedStatus: StateFlow<String?> = _selectedStatus.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    fun onTagSelected(tag: String) {
        _selectedTags.update { currentTags ->
            if (currentTags.contains(tag)) {
                currentTags - tag // Already there, remove it (deselect)
            } else {
                currentTags + tag // Not there, add it
            }
        }
    }

    val allStatuses: StateFlow<List<String>> = _savedRecipes.map { list ->
        list.mapNotNull { it.status } // Remove nulls
            .filter { it.isNotBlank() } // Remove empty strings
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onStatusChanged(newStatus: String?) {
        _selectedStatus.value = newStatus
    }

    // Reactive list combining the search with the selected tags, favorites and status
    val displayedRecipes: StateFlow<List<Recipe>> = combine(
        _savedRecipes,
        _searchQuery,
        _selectedTags,
        _isFavoriteFilter,
        _selectedStatus
    ) { recipes, query, tags, isFavoriteOnly, status ->
        RecipeFilter.apply(recipes, query, tags, isFavoriteOnly, status)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Functions for the UI to call on user interaction
    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    // State to show a loading spinner while data is being fetched
    private val _isLoadingRecipes = MutableStateFlow(false)
    val isLoadingRecipes: StateFlow<Boolean> = _isLoadingRecipes.asStateFlow()

    // Tracks whether we've already successfully fetched from Notion this session. We can no
    // longer use "the list is empty" as a signal for that: after saving a new recipe, it goes
    // straight into savedRecipes (see saveRecipeToNotion) even without ever having fetched the
    // rest — so the list stops being empty without ever having been loaded. Only marked done on
    // SUCCESS — if the fetch fails (timeout, no internet), whoever calls it again (e.g. reopening
    // the list screen) should be able to retry the fetch.
    private var recipesLoadedSuccessfully = false

    // Called when the list screen opens: only fetches from Notion if we don't already have a
    // successful fetch this session, and if there isn't a fetch already in progress.
    fun loadRecipesIfNeeded() {
        if (!recipesLoadedSuccessfully && !_isLoadingRecipes.value) {
            loadRecipesFromNotion()
        }
    }

    // Function that fetches the data
    fun loadRecipesFromNotion() {
        viewModelScope.launch {
            _isLoadingRecipes.value = true
            try {
                notionRepository.fetchRecipes().collect { partialList ->
                    _savedRecipes.value = partialList
                    _isLoadingRecipes.value = false
                }
                recipesLoadedSuccessfully = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BakeItOffDebug", "Falha ao carregar receitas do Notion", e)
                // Keeps whatever list we already had (doesn't reset to "no recipes") and warns
                // that the fetch failed, instead of making it look like the recipes vanished.
                _uiEvent.emit("Não consegui atualizar suas receitas. Verifique sua internet.")
            } finally {
                _isLoadingRecipes.value = false
            }
        }
    }

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        // Listens for key changes from the ApiKeyManager
        viewModelScope.launch {
            apiKeyManager.onKeyChanged.collect { newName ->
                _uiEvent.emit("Chave trocada para: $newName 🔄")
            }
        }
    }

    // The UI (long-press on the logo) calls this instead of touching the ApiKeyManager
    // directly, since it's no longer a singleton accessible from anywhere.
    fun toggleApiKey() {
        apiKeyManager.toggleKey()
    }

    // Suspends and returns the result: the caller decides what to do on screen
    // (close the edit view, navigate, etc.) only after knowing whether it actually saved.
    suspend fun saveRecipeToNotion(recipe: Recipe): Boolean {
        _isSavingToNotion.value = true
        try {
            val pageId = notionRepository.saveRecipe(recipe, RecipeExtractionState.originLink)
            if (pageId != null) {
                _uiEvent.emit("Receita salva no Notion! 🎉")
                val savedRecipe = recipe.copy(id = pageId)
                // Reflects the saved result immediately on the detail screen (if editing).
                _selectedRecipe.value = savedRecipe

                // Updates the local list right away instead of fetching everything again from
                // Notion: Notion's query API can take a few seconds to "see" a page that was
                // just created (eventual consistency), which made the new recipe disappear from
                // the list right after saving even though it was really already there.
                _savedRecipes.value = if (recipe.id.isNullOrBlank()) {
                    listOf(savedRecipe) + _savedRecipes.value
                } else {
                    _savedRecipes.value.map { if (it.id == pageId) savedRecipe else it }
                }
            } else {
                _uiEvent.emit("Erro ao salvar. Verifique o Logcat!")
            }
            return pageId != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiEvent.emit("Falha de conexão: ${e.message}")
            return false
        } finally {
            _isSavingToNotion.value = false
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        Log.d("BakeItOffDebug", "Clique recebido para receita: ${recipe.title}")
        viewModelScope.launch {
            val newState = !recipe.favorite
            recipe.id?.let { id ->
                val success = notionRepository.updateFavorite(pageId = id, favorite = newState)

                if (success) {
                    // 1. Updates the overall list (for the Home screen, if navigated back to)
                    _savedRecipes.value = _savedRecipes.value.map {
                        if (it.id == recipe.id) it.copy(favorite = newState) else it
                    }.toList()

                    // 2. Updates the specifically selected recipe
                    // This forces the detail screen to "re-render" with the new favorite status
                    _selectedRecipe.value = _selectedRecipe.value?.copy(favorite = newState)
                }
            }
        }
    }

    fun updateStatus(recipe: Recipe, newStatus: String) {
        viewModelScope.launch {
            recipe.id?.let { id ->
                val success = notionRepository.updateStatus(id, newStatus)

                if (success) {
                    // Updates the overall list
                    _savedRecipes.value = _savedRecipes.value.map {
                        if (it.id == recipe.id) it.copy(status = newStatus) else it
                    }.toList()

                    // Updates the selected recipe so the screen reflects it immediately
                    _selectedRecipe.value = _selectedRecipe.value?.copy(status = newStatus)

                    Log.d("BakeItOffDebug", "Status atualizado para: $newStatus")
                } else {
                    Log.e("BakeItOffDebug", "Erro ao atualizar status no Notion")
                }
            }
        }
    }

    fun cleanTags() {
        _selectedTags.value = emptySet()
    }

    // Suspends and returns the result: the screen only navigates back if the deletion
    // actually succeeded, instead of leaving the screen before knowing the outcome.
    suspend fun deleteRecipe(recipe: Recipe): Boolean {
        val id = recipe.id ?: return false
        val success = notionRepository.deleteRecipe(id)
        if (success) {
            _uiEvent.emit("Receita deletada com sucesso! 🗑️")
            // Updates the list, removing the recipe that was just deleted
            _savedRecipes.value = _savedRecipes.value.filter { it.id != id }
            clearSelectedRecipe()
        } else {
            _uiEvent.emit("Erro ao deletar receita.")
        }
        return success
    }
}
