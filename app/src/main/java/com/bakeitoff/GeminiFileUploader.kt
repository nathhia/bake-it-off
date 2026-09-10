package com.bakeitoff

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import okio.use
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiFileUploader(private val context: Context) {

    // Aumentamos o timeout porque uploads de vídeo levam mais tempo que requisições de texto
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun isVideoReady(fileUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentKey = ApiKeyManager.getApiKey()
            // Faz uma chamada GET simples, que não consome cota de geração de texto
            val request = Request.Builder()
                .url("$fileUri?key=$currentKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val jsonObject = JSONObject(body)
                        val state = jsonObject.optString("state", "")
                        return@withContext state == "ACTIVE"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro na checagem de status: ${e.message}")
        }
        return@withContext false
    }

suspend fun uploadVideo(videoUri: Uri): String? = withContext(Dispatchers.IO) {
    var attempts = 0
    val maxAttempts = 4

    var quotaAttempts = 0
    val maxQuotaAttempts = 3

    while (attempts < maxAttempts && coroutineContext.isActive) {
        try {
            // 1. Pega a chave DENTRO do loop, garantindo que ela se atualize após um erro
            val currentKey = ApiKeyManager.getApiKey()
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(videoUri) ?: "video/mp4"

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()

                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(videoUri)?.source()?.use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$currentKey")
                .post(requestBody)
                .addHeader("X-Goog-Upload-Protocol", "raw")
                .build()

            Log.d("BakeItOffDebug", "Iniciando upload (Tentativa ${attempts + 1}/$maxAttempts) com a chave: ${ApiKeyManager.getActiveKeyName()}")

            // 2. Executa a chamada
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val fileObject = jsonObject.getJSONObject("file")
                        Log.d("BakeItOffDebug", "Upload concluído com sucesso!")
                        return@withContext fileObject.getString("uri")
                    }
                } else {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""

                    attempts++ // Falhou, queima uma tentativa

                    when (code) {
                        429 -> { // Erro de Cota ou Crédito Esgotado
                            quotaAttempts++
                            if (quotaAttempts > maxQuotaAttempts) {
                                Log.e("BakeItOffDebug", "Limite de cota excedido no upload. Abortando.")
                                return@withContext null
                            }
                            val tempoEspera = (quotaAttempts * 2000L)
                            Log.w("BakeItOffDebug", "Cota estourada no Upload (429)! Trocando chave e tentando novamente...")
                            ApiKeyManager.toggleKey() // Gira a chave globalmente
                            delay(tempoEspera)
                        }
                        503 -> { // Servidor do Google sobrecarregado
                            quotaAttempts++
                            if (quotaAttempts > maxQuotaAttempts) return@withContext null

                            Log.w("BakeItOffDebug", "Servidor sobrecarregado no Upload (503). Aguardando 10s...")
                            delay(10000)
                        }
                        else -> { // Outros erros não recuperáveis (ex: arquivo muito grande, 400 Bad Request)
                            Log.e("BakeItOffDebug", "Erro fatal no upload: $code - $errorBody")
                            return@withContext null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Exceção de rede no upload: ${e.message}", e)
            attempts++
            delay(2000) // Respiro curto em caso de timeout de soquete ou oscilação de Wi-Fi
        }
    }

    Log.e("BakeItOffDebug", "Upload falhou após $maxAttempts tentativas.")
    return@withContext null
}
}