package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Recipe
import java.text.Normalizer

/**
 * Combines the text search with the tag, favorite and status filters.
 * Extracted from RecipeViewModel so it can be tested without instantiating
 * the whole ViewModel (which depends on Context/network via other classes).
 */
object RecipeFilter {

    fun apply(
        recipes: List<Recipe>,
        query: String,
        selectedTags: Set<String>,
        favoriteOnly: Boolean,
        status: String?
    ): List<Recipe> {
        val cleanQuery = normalizeForSearch(query).lowercase()
        val searchTerms = if (cleanQuery.isBlank()) emptyList() else cleanQuery.split(" ")

        return recipes.filter { recipe ->
            val matchesSearch = if (searchTerms.isEmpty()) {
                true
            } else {
                val normalizedTitle = normalizeForSearch(recipe.title).lowercase()
                val normalizedTags = recipe.tags.map { normalizeForSearch(it).lowercase() }

                searchTerms.all { term ->
                    normalizedTitle.contains(term) || normalizedTags.any { normalizedTag -> normalizedTag.contains(term) }
                }
            }

            val matchesTags = selectedTags.all { tag -> recipe.tags.contains(tag) }
            val matchesFavorite = !favoriteOnly || recipe.favorite
            val matchesStatus = status == null || recipe.status == status

            matchesSearch && matchesTags && matchesFavorite && matchesStatus
        }
    }

    /**
     * Strips accents, turns hyphens into spaces and collapses double spaces —
     * so "não-fritar" and "nao fritar" match the same search.
     */
    fun normalizeForSearch(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace("\\p{Mn}+".toRegex(), "")
        val withoutHyphens = withoutAccents.replace("-", " ")
        return withoutHyphens.replace("\\s+".toRegex(), " ").trim()
    }
}
