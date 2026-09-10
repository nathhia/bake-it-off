package com.bakeitoff.viewmodel

import android.util.Log
import com.bakeitoff.data.model.DicasComentario
import com.bakeitoff.data.model.Ingrediente
import com.bakeitoff.data.model.Receita
import com.google.gson.Gson

sealed interface RecipeParseResult {
    data class Success(val receita: Receita) : RecipeParseResult
    data class Failure(val message: String) : RecipeParseResult
}

object RecipeJsonParser {

    fun parse(rawJson: String?): RecipeParseResult {
        if (rawJson == null) {
            return RecipeParseResult.Failure("Não foi possível extrair a receita dessa mídia.")
        }

        Log.d("BakeItOffDebug", "Tentando fazer parse do JSON...")

        // 1. Encontra onde o JSON começa
        val inicio = rawJson.indexOf('{')
        if (inicio == -1) {
            Log.e("BakeItOffDebug", "Texto não possui estrutura JSON.")
            return RecipeParseResult.Failure("A resposta da IA não estava no formato correto.")
        }

        // 2. Algoritmo rastreador de chaves para achar o final EXATO
        var chavesAbertas = 0
        var fim = -1
        for (i in inicio until rawJson.length) {
            when (rawJson[i]) {
                '{' -> chavesAbertas++
                '}' -> {
                    chavesAbertas--
                    if (chavesAbertas == 0) {
                        fim = i
                        break
                    }
                }
            }
        }

        if (fim == -1) {
            Log.e("BakeItOffDebug", "Não encontrou o fechamento da chave.")
            return RecipeParseResult.Failure("A resposta da IA estava incompleta.")
        }

        // 3. Se encontrou um começo e um fim válidos, corta e envia pro Gson
        val jsonLimpo = rawJson.substring(inicio, fim + 1)
        return try {
            RecipeParseResult.Success(sanitize(Gson().fromJson(jsonLimpo, Receita::class.java)))
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro ao converter o JSON limpo: ${e.message}")
            RecipeParseResult.Failure("A IA não retornou um JSON válido.")
        }
    }

    /**
     * O Gson monta objetos por reflexão e pula o construtor do Kotlin — se a IA esquecer
     * uma chave no JSON, o campo correspondente vira `null` mesmo quando o tipo Kotlin diz
     * que não pode ser nulo, e mesmo quando há um valor padrão declarado (`= emptyList()`,
     * `= "..."`). Os casts pra tipo nullable abaixo são propositais: sem eles o Kotlin nem
     * deixaria escrever o "?:", mas o valor real em tempo de execução pode mesmo ser nulo.
     *
     * IMPORTANTE: é preciso passar TODO campo não-nulo aqui, mesmo os que "não deveriam"
     * estar em risco (ex: status) — o `.copy()` gerado pelo Kotlin valida como não-nulo
     * até o valor padrão (`this.status`) dos parâmetros que a gente não sobrescreve, e
     * lança NullPointerException nele mesmo se esse valor padrão já vier nulo do Gson.
     */
    fun sanitize(receita: Receita): Receita = receita.copy(
        titulo = (receita.titulo as String?) ?: "Receita sem título",
        tempoPreparo = (receita.tempoPreparo as String?) ?: "",
        ingredientes = (receita.ingredientes as List<Ingrediente>?) ?: emptyList(),
        passos = (receita.passos as List<String>?) ?: emptyList(),
        tags = (receita.tags as List<String>?) ?: emptyList(),
        dicas_video = (receita.dicas_video as List<DicasComentario>?) ?: emptyList(),
        status = (receita.status as String?) ?: "Não feito"
    )
}
