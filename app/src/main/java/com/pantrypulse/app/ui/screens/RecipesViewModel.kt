package com.pantrypulse.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.data.remote.OnlineRecipeSummary
import com.pantrypulse.app.domain.Recipe
import com.pantrypulse.app.domain.RecipeMatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RecipesViewModel(private val container: AppContainer) : ViewModel() {
    var ingredient by mutableStateOf<String?>(null)
        private set
    var online by mutableStateOf<List<OnlineRecipeSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun search(keyword: String) {
        if (keyword == ingredient && (online.isNotEmpty() || loading)) return
        ingredient = keyword
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            error = null
            online = try {
                container.mealDb.byIngredient(keyword).take(12)
            } catch (e: Exception) {
                error = "Online recipes need an internet connection."
                emptyList()
            }
            loading = false
        }
    }

    // ---- detail ----
    var detail by mutableStateOf<Recipe?>(null)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set

    fun open(id: String) {
        detail = null
        detailError = null
        RecipeMatcher.builtIn.firstOrNull { it.id == id }?.let { detail = it; return }
        viewModelScope.launch {
            try {
                detail = container.mealDb.details(id) ?: run { detailError = "Recipe not found."; null }
            } catch (e: Exception) {
                detailError = "Couldn't load this recipe. Check your connection."
            }
        }
    }
}
