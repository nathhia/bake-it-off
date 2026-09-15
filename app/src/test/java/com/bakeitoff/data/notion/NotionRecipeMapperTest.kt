package com.bakeitoff.data.notion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionRecipeMapperTest {

    private fun textProp(vararg texts: String) = NotionPropertyRichText(texts.map { TextObject(TextContent(it)) })

    @Test
    fun `parseIngredients groups by section and ignores blank lines`() {
        val text = """
            **Massa:**
            • 2 xícaras de farinha
            • 1 ovo

            **Recheio:**
            • 200 g de doce de leite
        """.trimIndent()

        val result = NotionRecipeMapper.parseIngredients(text)

        assertEquals(3, result.size)
        assertEquals("Massa", result[0].section)
        assertEquals("2 xícaras de farinha", result[0].item)
        assertEquals("Massa", result[1].section)
        assertEquals("Recheio", result[2].section)
        assertEquals("200 g de doce de leite", result[2].item)
    }

    @Test
    fun `parseIngredients without a section uses an empty string`() {
        val result = NotionRecipeMapper.parseIngredients("• sal a gosto\n• pimenta a gosto")

        assertEquals(2, result.size)
        assertTrue(result.all { it.section == "" })
    }

    @Test
    fun `parseSteps strips numbering and keeps order`() {
        val text = "1. Bata os ovos\n2) Adicione o açúcar\n3- Leve ao forno"

        val result = NotionRecipeMapper.parseSteps(text)

        assertEquals(listOf("Bata os ovos", "Adicione o açúcar", "Leve ao forno"), result)
    }

    @Test
    fun `parseTips identifies the source from the prefix`() {
        val text = "[IA] Guarde na geladeira\n[Pessoal] Fica melhor gelado\n[Vídeo] Ponto do doce de leite"

        val result = NotionRecipeMapper.parseTips(text)

        assertEquals(3, result.size)
        assertEquals("IA", result[0].source)
        assertTrue(result[0].enriched)
        assertEquals("Pessoal", result[1].source)
        assertFalse(result[1].enriched)
        assertEquals("Vídeo", result[2].source)
        assertEquals("Ponto do doce de leite", result[2].text)
    }

    @Test
    fun `toRecipe maps every property that is present`() {
        val page = NotionPageResponse(
            id = "abc123",
            properties = NotionPageProperties(
                name = NotionPropertyTitle(listOf(TextObject(TextContent("Bolo de Cenoura")))),
                prepTime = textProp("40 minutos"),
                tags = NotionPropertyMultiSelect(listOf(SelectOption("Bolo"), SelectOption("Forno"))),
                ingredients = textProp("• 2 cenouras"),
                instructions = textProp("1. Bata tudo"),
                favorite = NotionPropertyCheckbox(true),
                status = StatusProperty(StatusName("Feito")),
                link = UrlProperty("https://exemplo.com/receita"),
                tips = textProp("[IA] Sirva morno")
            )
        )

        val recipe = NotionRecipeMapper.toRecipe(page)

        assertEquals("abc123", recipe.id)
        assertEquals("Bolo de Cenoura", recipe.title)
        assertEquals("40 minutos", recipe.prepTime)
        assertEquals(listOf("Bolo", "Forno"), recipe.tags)
        assertEquals(1, recipe.ingredients.size)
        assertEquals(listOf("Bata tudo"), recipe.steps)
        assertTrue(recipe.favorite)
        assertEquals("Feito", recipe.status)
        assertEquals("https://exemplo.com/receita", recipe.link)
        assertEquals(1, recipe.videoTips.size)
    }

    @Test
    fun `toRecipe uses default values when properties come back null`() {
        val page = NotionPageResponse(
            id = "sem-props",
            properties = NotionPageProperties(
                name = null,
                prepTime = null,
                tags = null,
                ingredients = null,
                instructions = null,
                favorite = null,
                status = null,
                link = null,
                tips = null
            )
        )

        val recipe = NotionRecipeMapper.toRecipe(page)

        assertEquals("Sem Título", recipe.title)
        assertEquals("--", recipe.prepTime)
        assertEquals(emptyList<String>(), recipe.tags)
        assertEquals(emptyList<Any>(), recipe.ingredients)
        assertEquals(emptyList<String>(), recipe.steps)
        assertFalse(recipe.favorite)
        assertEquals("Não feito", recipe.status)
        assertEquals(null, recipe.link)
        assertEquals(emptyList<Any>(), recipe.videoTips)
    }
}
