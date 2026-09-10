package com.bakeitoff.viewmodel

import com.bakeitoff.data.model.Receita
import java.text.Normalizer

/**
 * Cruza a busca por texto com os filtros de tags, favorito e status.
 * Extraído do RecipeViewModel pra poder ser testado sem precisar instanciar
 * o ViewModel inteiro (que depende de Context/rede pelas outras classes).
 */
object RecipeFilter {

    fun apply(
        receitas: List<Receita>,
        query: String,
        selectedTags: Set<String>,
        favoriteOnly: Boolean,
        status: String?
    ): List<Receita> {
        val queryLimpa = normalizarParaBusca(query).lowercase()
        val termosBuscados = if (queryLimpa.isBlank()) emptyList() else queryLimpa.split(" ")

        return receitas.filter { receita ->
            val matchBusca = if (termosBuscados.isEmpty()) {
                true
            } else {
                val tituloPronto = normalizarParaBusca(receita.titulo).lowercase()
                val tagsProntas = receita.tags.map { normalizarParaBusca(it).lowercase() }

                termosBuscados.all { termo ->
                    tituloPronto.contains(termo) || tagsProntas.any { tagPronta -> tagPronta.contains(termo) }
                }
            }

            val matchTags = selectedTags.all { tag -> receita.tags.contains(tag) }
            val matchFav = !favoriteOnly || receita.favorito
            val matchStatus = status == null || receita.status == status

            matchBusca && matchTags && matchFav && matchStatus
        }
    }

    /**
     * Remove acentos, troca hífen por espaço e colapsa espaços duplos —
     * pra "não-fritar" e "nao fritar" darem match na mesma busca.
     */
    fun normalizarParaBusca(texto: String): String {
        val normalizada = Normalizer.normalize(texto, Normalizer.Form.NFD)
        val semAcento = normalizada.replace("\\p{Mn}+".toRegex(), "")
        val semHifen = semAcento.replace("-", " ")
        return semHifen.replace("\\s+".toRegex(), " ").trim()
    }
}
