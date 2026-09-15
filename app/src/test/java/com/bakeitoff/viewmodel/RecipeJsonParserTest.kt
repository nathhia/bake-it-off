package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Recipe
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gson builds objects via reflection and ignores Kotlin's default values when the AI
 * forgets a key in the JSON (see RecipeJsonParser.sanitize). These tests pin down that
 * behavior: if someone removes the sanitization "because it looks redundant", they break.
 */
class RecipeJsonParserTest {

    @Test
    fun `parse with all fields present works normally`() {
        val json = """
            {
                "titulo": "Bolo de Cenoura",
                "tempo_preparo": "40 minutos",
                "ingredientes": [{"quantidade": "2", "unidade": "xícaras", "item": "cenoura"}],
                "passos": ["Bata tudo no liquidificador", "Asse por 40 minutos"],
                "tags": ["Bolo", "Forno"]
            }
        """.trimIndent()

        val result = RecipeJsonParser.parse(json)

        assertTrue(result is RecipeParseResult.Success)
        val recipe = (result as RecipeParseResult.Success).recipe
        assertEquals("Bolo de Cenoura", recipe.title)
        assertEquals(listOf("Bolo", "Forno"), recipe.tags)
        assertEquals(1, recipe.ingredients.size)
    }

    @Test
    fun `parse with missing fields does not break and falls back to safe values`() {
        // No "tags", no "dicas_video" — exactly what the AI sends when there are no tips
        // at all, or forgets to include the key.
        val json = """
            {
                "titulo": "Bolo Simples",
                "tempo_preparo": "30 minutos",
                "ingredientes": [],
                "passos": ["Misture e asse."]
            }
        """.trimIndent()

        val result = RecipeJsonParser.parse(json)

        assertTrue(result is RecipeParseResult.Success)
        val recipe = (result as RecipeParseResult.Success).recipe

        // If the sanitization is removed, these lines throw a NullPointerException.
        assertEquals(emptyList<String>(), recipe.tags)
        assertEquals(emptyList<Any>(), recipe.videoTips)
        assertTrue(recipe.tags.isEmpty())
    }

    @Test
    fun `sanitize replaces null fields with safe values`() {
        // Builds the "broken" recipe with Gson itself (just like the app does in real life),
        // from an empty JSON: every field with no constructor default becomes genuinely null,
        // via reflection, completely ignoring Kotlin's non-null types.
        val recipeWithNulls = Gson().fromJson("{}", Recipe::class.java)

        val safeRecipe = RecipeJsonParser.sanitize(recipeWithNulls)

        assertEquals("Receita sem título", safeRecipe.title)
        assertEquals("", safeRecipe.prepTime)
        assertTrue(safeRecipe.ingredients.isEmpty())
        assertTrue(safeRecipe.steps.isEmpty())
        assertTrue(safeRecipe.tags.isEmpty())
        assertTrue(safeRecipe.videoTips.isEmpty())
    }

    @Test
    fun `sanitize also replaces null fields inside individual ingredients and tips`() {
        // Reproduces a real crash: the AI omitted "fonte" on one tip, so Gson left
        // RecipeTip.source null at runtime — RecipeTipsSection.kt:39 did `when (tip.source)`
        // on it and threw a NullPointerException the moment that item scrolled into view.
        val json = """
            {
                "titulo": "Bolo de Cenoura",
                "tempo_preparo": "40 minutos",
                "ingredientes": [{"quantidade": "2", "item": "cenoura"}],
                "passos": ["Asse por 40 minutos"],
                "tags": ["Bolo"],
                "dicas_video": [{"texto": "Fica melhor gelado"}]
            }
        """.trimIndent()

        val result = RecipeJsonParser.parse(json)

        assertTrue(result is RecipeParseResult.Success)
        val recipe = (result as RecipeParseResult.Success).recipe

        // If the nested sanitization is removed, these are null instead of "" / "Vídeo",
        // and anything doing `when (tip.source)` or `.trim()` on them crashes.
        assertEquals("", recipe.ingredients[0].unit)
        assertEquals("Vídeo", recipe.videoTips[0].source)
        assertEquals("Fica melhor gelado", recipe.videoTips[0].text)
    }
}
