package com.bakeitoff.data.notion

import com.bakeitoff.data.model.DicasComentario
import com.bakeitoff.data.model.Ingrediente
import com.bakeitoff.data.model.Receita

/**
 * Converte uma página do Notion (NotionPageResponse) numa Receita.
 * Extraído de NotionRepository.buscarReceitas pra poder ser testado sem
 * precisar de uma chamada de rede de verdade.
 */
object NotionRecipeMapper {

    fun toReceita(page: NotionPageResponse): Receita {
        val props = page.properties

        val tituloStr = props.nome?.title?.firstOrNull()?.text?.content ?: "Sem Título"
        val tempoStr = props.tempoPreparo?.rich_text?.firstOrNull()?.text?.content ?: "--"
        val tagsList = props.tags?.multi_select?.map { it.name } ?: emptyList()
        val dicasStr = props.dicas?.rich_text?.joinToString("") { it.text.content } ?: ""
        val link = props.link?.url

        val ingredientesStr = props.ingredientes?.rich_text?.joinToString("") { it.text.content } ?: ""
        val passosStr = props.passos?.rich_text?.joinToString("") { it.text.content } ?: ""

        return Receita(
            id = page.id,
            titulo = tituloStr,
            tempoPreparo = tempoStr,
            tags = tagsList,
            ingredientes = parseIngredientes(ingredientesStr),
            passos = parsePassos(passosStr),
            favorito = props.favorito?.checkbox ?: false,
            status = props.status?.status?.name ?: "Não feito",
            link = link,
            dicas_video = parseDicas(dicasStr)
        )
    }

    /**
     * O texto vem como linhas "• item" com cabeçalhos opcionais "**Seção:**"
     * (formato escrito por NotionRepository.saveRecipe/fatiarParaNotion).
     */
    fun parseIngredientes(ingredientesStr: String): List<Ingrediente> {
        var secaoAtual = ""
        val listaIngredientes = mutableListOf<Ingrediente>()

        // Aplicação da Sequence para performance de memória
        ingredientesStr.split("\n")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { linha ->
                val textoLimpo = linha.removePrefix("•").removePrefix("-").trim()

                if (textoLimpo.startsWith("**") && textoLimpo.endsWith("**")) {
                    secaoAtual = textoLimpo.replace("**", "").replace(":", "").trim()
                } else {
                    listaIngredientes.add(
                        Ingrediente(
                            quantidade = "",
                            unidade = "",
                            item = textoLimpo,
                            secao = secaoAtual
                        )
                    )
                }
            }

        return listaIngredientes
    }

    /** O texto vem como linhas "1. passo", "2. passo" etc. */
    fun parsePassos(passosStr: String): List<String> =
        passosStr
            .split("\n")
            .asSequence() // Aplicação da Sequence aqui também
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { linha ->
                linha.replace(Regex("^[0-9]+[.)-]\\s*"), "").trim()
            }
            .toList() // Converte de volta para lista no final do fluxo

    /** O texto vem como linhas "[IA] dica", "[Vídeo] dica" ou "[Pessoal] dica". */
    fun parseDicas(dicasStr: String): List<DicasComentario> =
        dicasStr
            .split("\n")
            .asSequence()
            .filter { it.isNotBlank() }
            .map { linha ->
                val textoLimpo = linha.removePrefix("[IA] ").removePrefix("[Vídeo] ").removePrefix("[Pessoal] ").trim()
                val fonte = when {
                    linha.startsWith("[IA]") -> "IA"
                    linha.startsWith("[Pessoal]") -> "Pessoal"
                    else -> "Vídeo"
                }

                DicasComentario(texto = textoLimpo, fonte = fonte, enriquecida = (fonte == "IA"))
            }
            .toList()
}
