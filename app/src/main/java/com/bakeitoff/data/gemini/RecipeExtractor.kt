package com.bakeitoff.data.gemini

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class RecipeExtractor(private val apiKeyManager: ApiKeyManager) {

    private val generativeModel: GenerativeModel
        get() = GenerativeModel(
        modelName = "gemini-3.5-flash",
        apiKey = apiKeyManager.getApiKey(),
        generationConfig = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json"
        },
            requestOptions = RequestOptions(timeout = 3.minutes),
        systemInstruction = content {
            text("""
                Você é um assistente culinário especializado em extração estruturada de dados.
                Seu objetivo é analisar mídias (vídeos/imagens) OU requisições em texto livre, convertendo-os ESTRITAMENTE em um objeto JSON válido, atuando como um Chef analítico.
                
                ### 1. COMPORTAMENTO DE BASE (MÍDIA VS. TEXTO)
                - **Se receber Mídia (Vídeo/Imagem):** Atue como um extrator fiel. Nunca altere a lista de ingredientes originais ou o método de preparo base extraído da mídia. Eles são a fonte da verdade.
                - **Se receber apenas Texto:** Atue como um Chef criativo. Crie a melhor versão possível da receita solicitada, estruturando os ingredientes e o passo a passo com precisão e clareza.
                
                ### 2. REGRAS DE ESTRUTURAÇÃO
                - **Substituições**: Sempre que o vídeo sugerir que um ingrediente pode ser substituído por outro, mapeie essa informação claramente no campo de ingredientes da receita, podendo colocar isso nas dicas_video também. Não ignore as sugestões de substituição
                - **Secionamento:** Se a receita tiver os ingredientes divididos em partes (ex: massa, recheio, cobertura), preencha o campo "secao". Se for uma receita simples, omita o campo "secao".
                - **Desmembramento de Ingredientes:** Separe a `quantidade` (ex: "2", "1/2") da `unidade` (ex: "xícaras", "colheres"). Se for "a gosto" ou não especificado, deixe a quantidade vazia e coloque a informação no `item`.
                - **Tempo e Pausas:** No campo `tempo_preparo`, consolide o tempo total. É obrigatório sinalizar tempos de inatividade longos (ex: "40 min (inclui 30 min de forno)"), inclua o tempo total e adicione observações sobre tempos de espera ou descanso (ex: forno, marinada ou geladeira), se houver necessidade e for importante.
                
                ### 3. DICAS E ENRIQUECIMENTO
                - **Enriquecimento por IA:** Você DEVE adicionar dicas extras geradas por você (ex: dicas de armazenamento, harmonização, alertas técnicos, melhorias sugeridas, variações de tempero). Marque-as com `enriquecida: true` e `fonte: "IA"`.
                - **Dicas da Fonte (Apenas para Mídia):** Se houver um vídeo e o autor der dicas verbais (ex: "o ponto ideal é quando o queijo borbulha") ou sugerir substituições, mapeie isso marcando com `enriquecida: false` e `fonte: "Video"`. Dicas técnicas ou comentários mencionados explicitamente pelo autor no vídeo.  Se for apenas uma requisição em texto, sem video ou imagens anexadas, ignore esta regra.
                - **Rendimento e Porções:** Informações sobre quantidade (ex: "Rende 10 porções", "Serve 4 pessoas") podem ser incluídas aqui. Se a informação for extraída da mídia original, marque com `fonte: "Video"` e `enriquecida: false`. Se for uma estimativa calculada por você, marque com `fonte: "IA"` e `enriquecida: true`.
                - Use a seção dicas_video como uma lista de objetos, cada um com texto, fonte e enriquecida (booleano).         
                
                ### 4. SISTEMA DE TAGS INTELIGENTE
                Gere um array de `tags` relevantes para facilitar a busca no banco de dados.
                - **Ingredientes-Chave:**  Se a receita exigir um ingrediente muito específico, marcante ou central para a sua identidade (ex: Mascarpone, Azeite Trufado, Parmesão, Peixe, Grão de Bico, Lentilha), inclua obrigatoriamente o nome desse ingrediente na lista de "tags".
                - **Método/Ocasião:** Inclua o equipamento (ex: "Forno", "Airfryer") e a ocasião (ex: "Sobremesa", "Jantar").
                
                ### 5. TRATAMENTO DE EXCEÇÃO (FALLBACK)
                Se o conteúdo enviado NÃO for uma receita culinária ou não tiver relação direta com comida, retorne um JSON válido com a exata mesma estrutura, mas preencha o campo `titulo` com "Conteúdo não culinário detectado". Retorne listas vazias (`[]`) nos campos `ingredientes`, `passos`, `tags` e `dicas_video`. O `tempo_preparo` deve ser `""`.
                
                ### 6. FORMATO DE SAÍDA OBRIGATÓRIO
                Retorne APENAS o objeto JSON abaixo. Nenhuma formatação extra ou texto Markdown antes ou depois.
         
                
                {
                    "titulo": "Nome da receita",
                    "tempo_preparo": "ex: 40 minutos",
                    "ingredientes": [
                        {"quantidade": "2", "unidade": "xícaras", "item": "açúcar", "secao": "Massa"}
                    ],
                    "passos": ["Passo 1", "Passo 2"],
                    "tags": ["Sobremesa", "Forno", "Almoço", "Acompanhamento", "Aperitivo", "Mascarpone"],
                    "dicas_video": [
                        {
                            "texto": "O ponto ideal é quando o queijo borbulha.",
                            "fonte": "Video",
                            "enriquecida": false
                        },
                        {
                            "texto": "Armazene em recipiente hermético por até 3 dias.",
                            "fonte": "IA",
                            "enriquecida": true
                        }
                    ]
                }
            """.trimIndent())
        }
    )


    suspend fun extractFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "Analise o conteúdo desta URL e extraia a receita em formato JSON: $url"
            val response = generativeModel.generateContent(prompt)
            return@withContext response.text?.replace("```json", "")?.replace("```", "")?.trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun higienizarJson(textoBruto: String?): String? {
        val texto = textoBruto?.trim() ?: return null
        return if (texto.startsWith("```")) {
            texto.replace(Regex("^```json\\s*|\\s*```$"), "").trim()
        } else {
            texto
        }
    }

    suspend fun extractFromMultiMedia(
        videoUri: String? = null,
        images: List<Bitmap> = emptyList(),
        prompt: String = "Analise o conteúdo e extraia a receita em JSON."
    ): String? = withContext(Dispatchers.IO) {

        var attempts = 0
        val maxAttempts = 15 // Mantém o polling de ~1 minuto e meio para processamento de vídeo

        var quotaAttempts = 0
        val maxQuotaAttempts = 4 // Reduzido, pois a troca de chave mitiga a espera

        // coroutineContext.isActive garante o cancelamento limpo se o usuário sair da tela
        while (attempts < maxAttempts && coroutineContext.isActive) {
            try {
                val inputContent = content {
                    if (videoUri != null) {
                        fileData(uri = videoUri, mimeType = "video/mp4")
                    }
                    images.forEach { image(it) }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                Log.d("BakeItOffDebug", "Sucesso! Extração concluída na tentativa ${attempts + 1}.")

                return@withContext higienizarJson(response.text)

            } catch (e: CancellationException) {
                // Cancelamento pedido pelo usuário — não é um erro de rede pra tratar/repetir.
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: ""
                attempts++ // Incremento universal por tentativa de requisição

                when {
                    // Cenário 1: Vídeo ainda processando no servidor do Google
                    errorMessage.contains("FAILED_PRECONDITION", ignoreCase = true) ||
                            errorMessage.contains("processing", ignoreCase = true) -> {
                        Log.d("BakeItOffDebug", "Vídeo processando (Tentativa $attempts/$maxAttempts). Aguardando 5s...")
                        delay(5000)
                    }

                    // Cenário 2: Erro de Cota / Limite de Requisições (429)
                    errorMessage.contains("Quota", ignoreCase = true) || errorMessage.contains("429") -> {
                        quotaAttempts++
                        if (quotaAttempts > maxQuotaAttempts) {
                            Log.e("BakeItOffDebug", "Limite de erros de cota excedido na mídia. Abortando.")
                            return@withContext null
                        }

                        val tempoEspera = (quotaAttempts * 2000L)
                        Log.w("BakeItOffDebug", "Cota atingida! Rotacionando chave de API instantaneamente...")
                        apiKeyManager.toggleKey() // Troca a chave sem congelar o app por 15s
                        delay(tempoEspera)

                        if (videoUri != null) {
                            throw KeyRotatedException()
                        }
                    }

                    // Cenário 3: Instabilidade no servidor do Google (503)
                    errorMessage.contains("503") || errorMessage.contains("high demand", ignoreCase = true) -> {
                        quotaAttempts++
                        if (quotaAttempts > maxQuotaAttempts) return@withContext null

                        val tempoEspera = (quotaAttempts * quotaAttempts) * 5000L
                        Log.w("BakeItOffDebug", "Servidor sobrecarregado (503) na mídia. Aplicando Backoff de ${tempoEspera/1000}s...")
                        delay(tempoEspera)
                    }

                    errorMessage.contains("403") || errorMessage.contains("PERMISSION_DENIED") || errorMessage.contains("MissingFieldException") -> {
                        Log.e("BakeItOffDebug", "Erro 403: Chave dessincronizada do arquivo. Solicitando re-upload...")
                        throw KeyRotatedException()
                    }

                    // Cenário 4: Qualquer outro erro fatal (Sem internet, Token inválido, etc)
                    else -> {
                        Log.e("BakeItOffDebug", "Erro fatal não recuperável na extração de mídia: $errorMessage", e)
                        return@withContext null
                    }
                }
            }
        }
        return@withContext null
    }

    suspend fun generateFromText(descricaoUsuario: String): String? = withContext(Dispatchers.IO) {

        var attempts = 0
        val maxAttempts = 4 // Reduzido: sem polling de arquivo, falhas aqui são apenas infra ou cota

        var quotaAttempts = 0
        val maxQuotaAttempts = 3

        while (attempts < maxAttempts && coroutineContext.isActive) {
            try {
                Log.d("BakeItOffDebug", "Geração por texto: Tentativa ${attempts + 1} de $maxAttempts...")

                val prompt = """
                O usuário descreveu: "$descricaoUsuario".
                Crie uma receita completa e retorne estritamente o objeto JSON.
            """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                Log.d("BakeItOffDebug", "Sucesso! Resposta de texto recebida.")

                return@withContext higienizarJson(response.text)

            } catch (e: CancellationException) {
                // Cancelamento pedido pelo usuário — não é um erro de rede pra tratar/repetir.
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: ""
                attempts++

                when {
                    // Cenário 1: Erro de Cota (429)
                    errorMessage.contains("Quota", ignoreCase = true) || errorMessage.contains("429") -> {
                        quotaAttempts++
                        if (quotaAttempts > maxQuotaAttempts) {
                            Log.e("BakeItOffDebug", "Limite de erros de cota excedido no texto. Abortando.")
                            return@withContext null
                        }
                        val tempoEspera = (quotaAttempts * 2000L)
                        Log.w("BakeItOffDebug", "Cota atingida no texto! Alternando API Key...")
                        apiKeyManager.toggleKey()
                        delay(tempoEspera)
                    }

                    // Cenário 2: Servidor instável (503)
                    errorMessage.contains("503") || errorMessage.contains("high demand", ignoreCase = true) -> {
                        quotaAttempts++
                        if (quotaAttempts > maxQuotaAttempts) return@withContext null

                        val tempoEspera = (quotaAttempts * quotaAttempts) * 5000L
                        Log.w("BakeItOffDebug", "Servidor sobrecarregado (503) no texto. Aplicando Backoff de ${tempoEspera/1000}s...")
                        delay(tempoEspera)
                    }

                    // Cenário 3: Erro fatal
                    else -> {
                        Log.e("BakeItOffDebug", "Erro fatal na geração de texto: $errorMessage", e)
                        return@withContext null
                    }
                }
            }
        }
        return@withContext null
    }

}

class KeyRotatedException : Exception("A chave foi rotacionada. Necessário re-upload silencioso.")