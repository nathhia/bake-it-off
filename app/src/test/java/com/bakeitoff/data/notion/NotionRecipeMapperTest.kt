package com.bakeitoff.data.notion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionRecipeMapperTest {

    private fun textProp(vararg textos: String) = NotionPropertyRichText(textos.map { TextObject(TextContent(it)) })

    @Test
    fun `parseIngredientes agrupa por secao e ignora linhas vazias`() {
        val texto = """
            **Massa:**
            • 2 xícaras de farinha
            • 1 ovo

            **Recheio:**
            • 200 g de doce de leite
        """.trimIndent()

        val resultado = NotionRecipeMapper.parseIngredientes(texto)

        assertEquals(3, resultado.size)
        assertEquals("Massa", resultado[0].secao)
        assertEquals("2 xícaras de farinha", resultado[0].item)
        assertEquals("Massa", resultado[1].secao)
        assertEquals("Recheio", resultado[2].secao)
        assertEquals("200 g de doce de leite", resultado[2].item)
    }

    @Test
    fun `parseIngredientes sem secao usa string vazia`() {
        val resultado = NotionRecipeMapper.parseIngredientes("• sal a gosto\n• pimenta a gosto")

        assertEquals(2, resultado.size)
        assertTrue(resultado.all { it.secao == "" })
    }

    @Test
    fun `parsePassos remove numeracao e mantem ordem`() {
        val texto = "1. Bata os ovos\n2) Adicione o açúcar\n3- Leve ao forno"

        val resultado = NotionRecipeMapper.parsePassos(texto)

        assertEquals(listOf("Bata os ovos", "Adicione o açúcar", "Leve ao forno"), resultado)
    }

    @Test
    fun `parseDicas identifica a fonte pelo prefixo`() {
        val texto = "[IA] Guarde na geladeira\n[Pessoal] Fica melhor gelado\n[Vídeo] Ponto do doce de leite"

        val resultado = NotionRecipeMapper.parseDicas(texto)

        assertEquals(3, resultado.size)
        assertEquals("IA", resultado[0].fonte)
        assertTrue(resultado[0].enriquecida)
        assertEquals("Pessoal", resultado[1].fonte)
        assertFalse(resultado[1].enriquecida)
        assertEquals("Vídeo", resultado[2].fonte)
        assertEquals("Ponto do doce de leite", resultado[2].texto)
    }

    @Test
    fun `toReceita mapeia todas as propriedades presentes`() {
        val page = NotionPageResponse(
            id = "abc123",
            properties = NotionPageProperties(
                nome = NotionPropertyTitle(listOf(TextObject(TextContent("Bolo de Cenoura")))),
                tempoPreparo = textProp("40 minutos"),
                tags = NotionPropertyMultiSelect(listOf(SelectOption("Bolo"), SelectOption("Forno"))),
                ingredientes = textProp("• 2 cenouras"),
                passos = textProp("1. Bata tudo"),
                favorito = NotionPropertyCheckbox(true),
                status = StatusProperty(StatusName("Feito")),
                link = UrlProperty("https://exemplo.com/receita"),
                dicas = textProp("[IA] Sirva morno")
            )
        )

        val receita = NotionRecipeMapper.toReceita(page)

        assertEquals("abc123", receita.id)
        assertEquals("Bolo de Cenoura", receita.titulo)
        assertEquals("40 minutos", receita.tempoPreparo)
        assertEquals(listOf("Bolo", "Forno"), receita.tags)
        assertEquals(1, receita.ingredientes.size)
        assertEquals(listOf("Bata tudo"), receita.passos)
        assertTrue(receita.favorito)
        assertEquals("Feito", receita.status)
        assertEquals("https://exemplo.com/receita", receita.link)
        assertEquals(1, receita.dicas_video.size)
    }

    @Test
    fun `toReceita usa valores padrao quando propriedades vem nulas`() {
        val page = NotionPageResponse(
            id = "sem-props",
            properties = NotionPageProperties(
                nome = null,
                tempoPreparo = null,
                tags = null,
                ingredientes = null,
                passos = null,
                favorito = null,
                status = null,
                link = null,
                dicas = null
            )
        )

        val receita = NotionRecipeMapper.toReceita(page)

        assertEquals("Sem Título", receita.titulo)
        assertEquals("--", receita.tempoPreparo)
        assertEquals(emptyList<String>(), receita.tags)
        assertEquals(emptyList<Any>(), receita.ingredientes)
        assertEquals(emptyList<String>(), receita.passos)
        assertFalse(receita.favorito)
        assertEquals("Não feito", receita.status)
        assertEquals(null, receita.link)
        assertEquals(emptyList<Any>(), receita.dicas_video)
    }
}
