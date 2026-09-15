package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Represents the screen states during the extraction process
sealed interface RecipeUiState {
    object Initial : RecipeUiState
    object Uploading : RecipeUiState
    object Extracting : RecipeUiState
    data class Success(val recipe: Recipe) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

/**
 * Process-wide holder for the extraction pipeline's state. The actual work runs inside
 * RecipeExtractionService (a foreground service, so it survives the app being backgrounded),
 * not inside RecipeViewModel's viewModelScope — a ViewModel gets cleared when its screen goes
 * away, which would silently kill an in-flight extraction. RecipeViewModel just observes this
 * StateFlow instead of owning the state itself, so the UI reflects whatever the service is
 * doing whether or not a screen was open when it finished.
 */
object RecipeExtractionState {
    private val _uiState = MutableStateFlow<RecipeUiState>(RecipeUiState.Initial)
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    // The optional link the user typed alongside attached media, needed later to save the
    // recipe with its source link. Kept here instead of as RecipeViewModel instance state so
    // it survives the same process-death-and-restart that RecipeExtractionService is built to
    // survive — a ViewModel field would just come back null in that case.
    var originLink: String? = null
        private set

    fun update(state: RecipeUiState) {
        _uiState.value = state
    }

    fun setOriginLink(link: String?) {
        originLink = link
    }

    fun resetToInitial() {
        _uiState.value = RecipeUiState.Initial
        originLink = null
    }
}
