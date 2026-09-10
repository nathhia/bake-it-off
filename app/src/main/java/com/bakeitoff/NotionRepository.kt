package com.bakeitoff

import DicasComentario
import Ingrediente
import Receita
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Representa o JSON que o Gemini nos devolveu
data class GeminiRecipeJson(
    val titulo: String,
    val ingredientes: List<String>,
    val modo_preparo: List<String>,
    val tags: List<String>
)

class NotionRepository(private val integrationToken: String, private val databaseId: String) {

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Tempo para conectar
        .readTimeout(30, TimeUnit.SECONDS)    // Tempo para ler a resposta
        .writeTimeout(30, TimeUnit.SECONDS)   // Tempo para enviar os dados
        .build()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.notion.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
        .create(NotionApiService::class.java)

    private fun fatiarParaNotion(texto: String): List<TextObject> {
        if (texto.isEmpty()) return listOf(TextObject(TextContent("")))

        // Fatiamos em 1999 para garantir que fique abaixo do limite de 2000 da API
        return texto.chunked(1999).map { pedaco ->
            TextObject(TextContent(pedaco))
        }
    }

    // Recebe diretamente a sua classe Receita que já vem bonitinha do Gemini
    suspend fun saveRecipe(receita: Receita, linkOrigem: String?): Boolean {
        return try {

            // ==========================================
            // PARTE 1: Preparando textos para as Colunas (Propriedades)
            // ==========================================
//            val ingredientesString = receita.ingredientes
//                .groupBy { it.secao }
//                .entries.joinToString("\n\n") { (secao, listaDeIngredientes) ->
//                    val tituloSecao = if (!secao.isNullOrEmpty()) "**$secao:**\n" else ""
//                    val itens = listaDeIngredientes.joinToString("\n") { ing ->
//                        "• ${ing.quantidade} ${ing.unidade} de ${ing.item}"
//                    }
//                    tituloSecao + itens
//                }
            val ingredientesString = receita.ingredientes
                .groupBy { it.secao }
                .entries.joinToString("\n\n") { (secao, listaDeIngredientes) ->
                    val tituloSecao = if (!secao.isNullOrEmpty()) "**$secao:**\n" else ""
                    val itens = listaDeIngredientes.joinToString("\n") { ing ->

                        // Reconstrói a frase se a IA separou, ou usa a pronta se veio do Notion
                        val textoIngrediente = if (ing.quantidade.isNullOrBlank() && ing.unidade.isNullOrBlank()) {
                            ing.item
                        } else {
                            val q = ing.quantidade?.trim() ?: ""
                            val u = ing.unidade?.trim() ?: ""
                            val ligacao = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "

                            "$q $u$ligacao${ing.item}".replace(Regex("\\s+"), " ").trim()
                        }

                        "• $textoIngrediente"
                    }
                    tituloSecao + itens
                }

            val passosString = receita.passos.mapIndexed { index, passo ->
                "${index + 1}. $passo"
            }.joinToString("\n")

            val dicasString = receita.dicas_video.joinToString("\n") { dica ->
                val prefixo = when (dica.fonte) {
                    "IA" -> "[IA]"
                    "Pessoal" -> "[Pessoal]"
                    else -> "[Vídeo]"
                }
                "$prefixo ${dica.texto}"
            }

            val linkFinal = linkOrigem?.takeIf { it.isNotBlank() } ?: receita.link

            val properties = RecipeProperties(
                nome = NotionTitle(listOf(TextObject(TextContent(receita.titulo)))),
                tempoPreparo = NotionRichText(listOf(TextObject(TextContent(receita.tempoPreparo)))),
                ingredientes = NotionRichText(fatiarParaNotion(ingredientesString)),
                preparo = NotionRichText(fatiarParaNotion(passosString)),
                tags = NotionMultiSelect(receita.tags.map { SelectOption(it) }),
                favorito = NotionCheckbox(receita.favorito), // Preserva estado atual
                status = NotionStatus(StatusOption(receita.status ?: "Não feito")), // Preserva estado atual
                link = if (!linkFinal.isNullOrBlank()) NotionUrl(linkFinal) else null,
                dicas = NotionRichText(fatiarParaNotion(dicasString))
            )

            // ==========================================
            // PARTE 2: Verifica se é CRIAÇÃO ou ATUALIZAÇÃO
            // ==========================================

            if (receita.id.isNullOrBlank()) {
                val pageBlocks = mutableListOf<NotionBlock>()

                pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Ingredientes"))))))

//                val ingredientesAgrupados = receita.ingredientes.groupBy { it.secao }
//                ingredientesAgrupados.forEach { (secao, lista) ->
//                    if (!secao.isNullOrEmpty()) {
//                        pageBlocks.add(
//                            Heading3Block(
//                                NotionRichText(
//                                    listOf(
//                                        TextObject(
//                                            TextContent(
//                                                secao
//                                            )
//                                        )
//                                    )
//                                )
//                            )
//                        )
//                    }
//                    lista.forEach { ing ->
//                        //val textoIngrediente = "${ing.quantidade} ${ing.unidade} de ${ing.item}"
//                        val textoIngrediente = ing.item
//                        pageBlocks.add(
//                            BulletedListBlock(
//                                NotionRichText(
//                                    listOf(
//                                        TextObject(
//                                            TextContent(textoIngrediente)
//                                        )
//                                    )
//                                )
//                            )
//                        )
//                    }
//                }

                val ingredientesAgrupados = receita.ingredientes.groupBy { it.secao }
                ingredientesAgrupados.forEach { (secao, lista) ->
                    if (!secao.isNullOrEmpty()) {
                        pageBlocks.add(
                            Heading3Block(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(
                                                secao
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                    lista.forEach { ing ->
                        val textoIngrediente = if (ing.quantidade.isNullOrBlank() && ing.unidade.isNullOrBlank()) {
                            ing.item
                        } else {
                            val q = ing.quantidade?.trim() ?: ""
                            val u = ing.unidade?.trim() ?: ""
                            val ligacao = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "
                            "$q $u$ligacao${ing.item}".replace(Regex("\\s+"), " ").trim()
                        }

                        pageBlocks.add(
                            BulletedListBlock(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(textoIngrediente)
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                if (receita.dicas_video.isNotEmpty()) {
                    pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Dicas e Comentários"))))))

                    receita.dicas_video.forEach { dica ->
                        val icone = when (dica.fonte) {
                            "IA" -> "💡 "
                            "Pessoal" -> "📝 "
                            else -> "📹 "
                        }
                        pageBlocks.add(
                            BulletedListBlock(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(icone + dica.texto)
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Modo de Preparo"))))))

                receita.passos.forEach { passo ->
                    pageBlocks.add(
                        NumberedListBlock(
                            NotionRichText(
                                listOf(
                                    TextObject(
                                        TextContent(
                                            passo
                                        )
                                    )
                                )
                            )
                        )
                    )
                }

                // ==========================================
                // PARTE 3: Disparo da Requisição Final
                // ==========================================
                val request = NotionCreatePageRequest(
                    parent = NotionDatabaseParent(databaseId),
                    properties = properties,
                    children = pageBlocks
                )

                val response = api.addRecipe("Bearer $integrationToken", request)

                if (response.isSuccessful) {
                    Log.d(
                        "BakeItOffDebug",
                        "Sucesso Híbrido! Colunas preenchidas e página desenhada."
                    )
                    return true
                } else {
                    Log.e("BakeItOffDebug", "Erro do Notion: ${response.errorBody()?.string()}")
                    return false
                }
            } else {
                // ➔ É UMA RECEITA EXISTENTE (PATCH)
                val request = UpdateFullPageRequest(properties = properties)

                val response = api.updateFullPage(
                    token = "Bearer $integrationToken",
                    version = "2022-06-28",
                    pageId = receita.id!!,
                    request = request
                )

                return if (response.isSuccessful) {
                    Log.d("BakeItOffDebug", "Receita ATUALIZADA com sucesso no Notion!")
                    true
                } else {
                    Log.e("BakeItOffDebug", "Erro ao atualizar: ${response.errorBody()?.string()}")
                    false
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    suspend fun buscarReceitas(): Flow<List<Receita>> = flow {
        val todasReceitas = mutableListOf<Receita>()
        var cursorAtual: String? = null
        var temMaisPaginas = true

        try {
            while (temMaisPaginas) {
                val requestBody = QueryDatabaseRequest(start_cursor = cursorAtual)
                val response = api.queryDatabase("Bearer $integrationToken", databaseId, requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    temMaisPaginas = body.has_more
                    cursorAtual = body.next_cursor

                    val receitasDaPagina = body.results.map { page ->
                        val props = page.properties

                        val tituloStr = props.nome?.title?.firstOrNull()?.text?.content ?: "Sem Título"
                        val tempoStr = props.tempoPreparo?.rich_text?.firstOrNull()?.text?.content ?: "--"
                        val tagsList = props.tags?.multi_select?.map { it.name } ?: emptyList()
                        val dicasStr = props.dicas?.rich_text?.joinToString("") { it.text.content } ?: ""
                        val link = props.link?.url

                        val ingredientesStr = props.ingredientes?.rich_text?.joinToString("") { it.text.content } ?: ""
                        val passosStr = props.passos?.rich_text?.joinToString("") { it.text.content } ?: ""

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

                        val listaPassos: List<String> = passosStr
                            .split("\n")
                            .asSequence() // Aplicação da Sequence aqui também
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { linha ->
                                linha.replace(Regex("^[0-9]+[.)-]\\s*"), "").trim()
                            }
                            .toList() // Converte de volta para lista no final do fluxo

                        val listaDicas = dicasStr
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

                        Receita(
                            id = page.id,
                            titulo = tituloStr,
                            tempoPreparo = tempoStr,
                            tags = tagsList,
                            ingredientes = listaIngredientes,
                            passos = listaPassos,
                            favorito = props.favorito?.checkbox ?: false,
                            status = props.status?.status?.name ?: "Não feito",
                            link = link,
                            dicas_video = listaDicas
                        )
                    }

                    todasReceitas.addAll(receitasDaPagina)
                    emit(todasReceitas.toList())

                } else {
                    Log.e("BakeItOffDebug", "Erro ao buscar do Notion: ${response.errorBody()?.string()}")
                    temMaisPaginas = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Retorna o que foi possível salvar antes do erro, ou uma lista vazia se falhou de primeira
            if (todasReceitas.isNotEmpty()) {
                emit(todasReceitas.toList())
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun updateFavorito(pageId: String, favorito: Boolean): Boolean {
        return try {
            // Monta a estrutura que o Notion exige
            val request = UpdatePageRequest(
                properties = UpdateProperties(
                    favorito = CheckboxProperty(checkbox = favorito)
                )
            )

            val response = api.updatePageProperties(
                token = "Bearer $integrationToken",
                version = "2022-06-28", // <--- Adicione o valor da versão aqui
                pageId = pageId,
                request = request
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Favorito atualizado com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao atualizar favorito: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão: ${e.message}")
            false
        }
    }

    suspend fun updateStatus(pageId: String, novoStatus: String): Boolean {
        return try {
            // Monta a estrutura que o Notion exige
            val request = UpdatePageRequest(
                properties = UpdateProperties(
                    status = StatusProperty(StatusName(novoStatus))
                )
            )

            val response = api.updatePageProperties(
                token = "Bearer $integrationToken",
                version = "2022-06-28", // <--- Adicione o valor da versão aqui
                pageId = pageId,
                request = request
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Status atualizado com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao atualizar status: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão: ${e.message}")
            false
        }
    }
    suspend fun deleteRecipe(pageId: String): Boolean {
        return try {
            val response = api.archivePage(
                token = "Bearer $integrationToken",
                version = "2022-06-28",
                pageId = pageId,
                request = ArchivePageRequest() // Manda archived = true por padrão
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Receita arquivada/deletada com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao deletar: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão ao deletar: ${e.message}")
            false
        }
    }
}
