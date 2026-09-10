package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Receita
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeFilterTest {

    private fun receita(
        titulo: String,
        tags: List<String> = emptyList(),
        favorito: Boolean = false,
        status: String = "Não feito"
    ) = Receita(
        id = titulo,
        titulo = titulo,
        tempoPreparo = "10 minutos",
        ingredientes = emptyList(),
        passos = emptyList(),
        tags = tags,
        favorito = favorito,
        status = status
    )

    private val bolo = receita("Bolo de Cenoura", tags = listOf("Bolo", "Forno"), favorito = true, status = "Feito")
    private val panqueca = receita("Panqueca Não-Fritada", tags = listOf("Café da Manhã"), status = "Quero fazer")
    private val torta = receita("Torta de Limão", tags = listOf("Sobremesa", "Geladeira"))
    private val todas = listOf(bolo, panqueca, torta)

    @Test
    fun `sem filtro nenhum retorna tudo`() {
        val resultado = RecipeFilter.apply(todas, query = "", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(todas, resultado)
    }

    @Test
    fun `busca por titulo eh case-insensitive`() {
        val resultado = RecipeFilter.apply(todas, query = "cenoura", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(bolo), resultado)
    }

    @Test
    fun `busca ignora acentos e hifen`() {
        // "nao fritada" (sem acento, sem hífen) precisa achar "Não-Fritada"
        val resultado = RecipeFilter.apply(todas, query = "nao fritada", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(panqueca), resultado)
    }

    @Test
    fun `busca com varios termos exige todos presentes (AND) no titulo ou tags`() {
        val resultado = RecipeFilter.apply(todas, query = "torta geladeira", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), resultado)
    }

    @Test
    fun `busca tambem acha por tag`() {
        val resultado = RecipeFilter.apply(todas, query = "sobremesa", selectedTags = emptySet(), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), resultado)
    }

    @Test
    fun `filtro de tags exige todas as tags selecionadas (AND)`() {
        val resultado = RecipeFilter.apply(todas, query = "", selectedTags = setOf("Sobremesa", "Geladeira"), favoriteOnly = false, status = null)
        assertEquals(listOf(torta), resultado)
    }

    @Test
    fun `filtro de tags nao acha se receita so tem uma das tags`() {
        val resultado = RecipeFilter.apply(todas, query = "", selectedTags = setOf("Sobremesa", "Forno"), favoriteOnly = false, status = null)
        assertEquals(emptyList<Receita>(), resultado)
    }

    @Test
    fun `filtro de favoritos`() {
        val resultado = RecipeFilter.apply(todas, query = "", selectedTags = emptySet(), favoriteOnly = true, status = null)
        assertEquals(listOf(bolo), resultado)
    }

    @Test
    fun `filtro de status`() {
        val resultado = RecipeFilter.apply(todas, query = "", selectedTags = emptySet(), favoriteOnly = false, status = "Quero fazer")
        assertEquals(listOf(panqueca), resultado)
    }

    @Test
    fun `filtros se combinam (AND entre busca, tags, favorito e status)`() {
        val resultado = RecipeFilter.apply(
            todas,
            query = "bolo",
            selectedTags = setOf("Forno"),
            favoriteOnly = true,
            status = "Feito"
        )
        assertEquals(listOf(bolo), resultado)
    }
}
