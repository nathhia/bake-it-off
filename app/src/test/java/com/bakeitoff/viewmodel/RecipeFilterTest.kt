package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeFilterTest {

    private fun recipe(
        title: String,
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
        status: String = "Não feito"
    ) = Recipe(
        id = title,
        title = title,
        prepTime = "10 minutos",
        ingredients = emptyList(),
        steps = emptyList(),
        tags = tags,
        favorite = favorite,
        status = status
    )

    private val bolo = recipe("Bolo de Cenoura", tags = listOf("Bolo", "Forno"), favorite = true, status = "Feito")
    private val panqueca = recipe("Panqueca Não-Fritada", tags = listOf("Café da Manhã"), status = "Quero fazer")
    private val torta = recipe("Torta de Limão", tags = listOf("Sobremesa", "Geladeira"))
    private val all = listOf(bolo, panqueca, torta)

    @Test
    fun `no filters returns everything`() {
        val result = RecipeFilter.apply(all, query = "", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(all, result)
    }

    @Test
    fun `title search is case-insensitive`() {
        val result = RecipeFilter.apply(all, query = "cenoura", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(bolo), result)
    }

    @Test
    fun `search ignores accents and hyphens`() {
        // "nao fritada" (no accent, no hyphen) needs to match "Não-Fritada"
        val result = RecipeFilter.apply(all, query = "nao fritada", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(panqueca), result)
    }

    @Test
    fun `search with multiple terms requires all present (AND) in title or tags`() {
        val result = RecipeFilter.apply(all, query = "torta geladeira", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), result)
    }

    @Test
    fun `search also matches by tag`() {
        val result = RecipeFilter.apply(all, query = "sobremesa", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), result)
    }

    @Test
    fun `tag filter requires all selected tags (AND)`() {
        val result = RecipeFilter.apply(all, query = "", selectedTags = setOf("Sobremesa", "Geladeira"), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), result)
    }

    @Test
    fun `tag filter does not match if recipe only has one of the tags`() {
        val result = RecipeFilter.apply(all, query = "", selectedTags = setOf("Sobremesa", "Forno"), favoriteOnly = false, status = null)
        assertEquals(emptyList<Recipe>(), result)
    }

    @Test
    fun `favorite filter`() {
        val result = RecipeFilter.apply(all, query = "", selectedTags = emptySet(), favoriteOnly = true, status = null)
        assertEquals(listOf(bolo), result)
    }

    @Test
    fun `status filter`() {
        val result = RecipeFilter.apply(all, query = "", selectedTags = emptySet(), favoriteOnly = false, status = "Quero fazer")
        assertEquals(listOf(panqueca), result)
    }

    @Test
    fun `filters combine (AND between search, tags, favorite and status)`() {
        val result = RecipeFilter.apply(
            all,
            query = "bolo",
            selectedTags = setOf("Forno"),
            favoriteOnly = true,
            status = "Feito"
        )
        assertEquals(listOf(bolo), result)
    }
}
