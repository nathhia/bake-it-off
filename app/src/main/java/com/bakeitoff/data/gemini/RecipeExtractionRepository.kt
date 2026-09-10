package com.bakeitoff.data.gemini

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay

sealed interface ExtractionPhase {
    object Uploading : ExtractionPhase
    object Extracting : ExtractionPhase
}

sealed interface ExtractionOutcome {
    data class Success(val json: String) : ExtractionOutcome
    data class Failure(val message: String) : ExtractionOutcome
}

class RecipeExtractionRepository(
    private val uploader: GeminiFileUploader,
    private val extractor: RecipeExtractor
) {

    suspend fun extractFromUrl(url: String): String? = extractor.extractFromUrl(url)

    suspend fun generateFromText(description: String): String? = extractor.generateFromText(description)

    suspend fun extractFromMedia(
        compressedVideoUri: Uri?,
        images: List<Bitmap>,
        prompt: String,
        onPhaseChange: suspend (ExtractionPhase) -> Unit
    ): ExtractionOutcome {
        val maxPipelineAttempts = ApiKeyManager.sizeKeys
        var pipelineAttempts = 0

        while (pipelineAttempts < maxPipelineAttempts) {
            try {
                var geminiVideoUri: String? = null

                if (compressedVideoUri != null) {
                    onPhaseChange(ExtractionPhase.Uploading)
                    geminiVideoUri = uploader.uploadVideo(compressedVideoUri)
                        ?: return ExtractionOutcome.Failure("Falha ao fazer o upload do vídeo na nuvem.")
                }

                onPhaseChange(ExtractionPhase.Extracting)

                Log.d("BakeItOffDebug", "Iniciando checagem de mídia...")

                if (geminiVideoUri != null && !waitUntilReady(geminiVideoUri)) {
                    return ExtractionOutcome.Failure("O vídeo demorou demais para processar. Tente novamente.")
                }

                val json = extractor.extractFromMultiMedia(
                    videoUri = geminiVideoUri,
                    images = images,
                    prompt = prompt
                )

                return if (json != null) {
                    ExtractionOutcome.Success(json)
                } else {
                    ExtractionOutcome.Failure("Não foi possível extrair a receita pela IA.")
                }
            } catch (e: KeyRotatedException) {
                pipelineAttempts++
                Log.w(
                    "BakeItOffDebug",
                    "Chave rotacionada capturada! Refazendo Upload Silencioso... (Tentativa $pipelineAttempts de $maxPipelineAttempts)"
                )
                delay(3000)
            }
        }

        return ExtractionOutcome.Failure("Todas as chaves de contingência falharam ou estão sem cota.")
    }

    private suspend fun waitUntilReady(fileUri: String, maxChecks: Int = 24): Boolean {
        var isReady = false
        var checks = 0

        while (!isReady && checks < maxChecks) {
            isReady = uploader.isVideoReady(fileUri)
            if (!isReady) {
                Log.d("BakeItOffDebug", "Vídeo ainda processando no Google (checagem ${checks + 1}/$maxChecks)...")
                delay(5000)
                checks++
            }
        }

        return isReady
    }
}
