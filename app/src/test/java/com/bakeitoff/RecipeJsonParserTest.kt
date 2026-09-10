package com.bakeitoff

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O Gson monta objetos por reflexão e ignora os valores padrão do Kotlin quando a IA
 * esquece uma chave no JSON (ver RecipeJsonParser.sanitize). Estes testes travam esse
 * comportamento: se alguém remover a sanitização "porque parece redundante", eles quebram.
 */
class RecipeJsonParserTest {

    @Test
    fun `parse com todos os campos presentes funciona normalmente`() {
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
        val receita = (result as RecipeParseResult.Success).receita
        assertEquals("Bolo de Cenoura", receita.titulo)
        assertEquals(listOf("Bolo", "Forno"), receita.tags)
        assertEquals(1, receita.ingredientes.size)
    }

    @Test
    fun `parse com campos ausentes nao quebra e usa valores seguros`() {
        // Sem "tags", sem "dicas_video" — exatamente o que a IA manda quando não tem dica nenhuma
        // ou esquece de incluir a chave.
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
        val receita = (result as RecipeParseResult.Success).receita

        // Se a sanitização for removida, estas linhas lançam NullPointerException.
        assertEquals(emptyList<String>(), receita.tags)
        assertEquals(emptyList<Any>(), receita.dicas_video)
        assertTrue(receita.tags.isEmpty())
    }

    @Test
    fun `sanitize substitui campos nulos por valores seguros`() {
        // Monta a receita "quebrada" com o próprio Gson (igual o app faz na vida real),
        // com um JSON vazio: todo campo sem default no construtor vira null de verdade,
        // por reflexão, ignorando por completo os tipos não-nulos do Kotlin.
        val receitaComNulls = Gson().fromJson("{}", Receita::class.java)

        val receitaSegura = RecipeJsonParser.sanitize(receitaComNulls)

        assertEquals("Receita sem título", receitaSegura.titulo)
        assertEquals("", receitaSegura.tempoPreparo)
        assertTrue(receitaSegura.ingredientes.isEmpty())
        assertTrue(receitaSegura.passos.isEmpty())
        assertTrue(receitaSegura.tags.isEmpty())
        assertTrue(receitaSegura.dicas_video.isEmpty())
    }
}
