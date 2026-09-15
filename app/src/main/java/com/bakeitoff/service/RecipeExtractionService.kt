package com.bakeitoff.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.bakeitoff.BakeItOffApplication
import com.bakeitoff.R
import com.bakeitoff.data.gemini.ExtractionOutcome
import com.bakeitoff.data.gemini.ExtractionPhase
import com.bakeitoff.data.gemini.MediaPreparationException
import com.bakeitoff.data.gemini.PreparedMedia
import com.bakeitoff.data.model.Recipe
import com.bakeitoff.notifications.NotificationHelper
import com.bakeitoff.viewmodel.RecipeExtractionState
import com.bakeitoff.viewmodel.RecipeJsonParser
import com.bakeitoff.viewmodel.RecipeParseResult
import com.bakeitoff.viewmodel.RecipeUiState
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the recipe extraction pipeline (media upload, Gemini polling/extraction, link
 * scraping, text generation) as a foreground service instead of inside RecipeViewModel's
 * viewModelScope. A ViewModel is cleared as soon as its screen goes away, and Android is
 * free to kill an app process that's simply sitting in the background with no foreground
 * service — both of which used to silently drop an in-progress extraction if the user
 * switched to another app mid-pipeline. Running it here, behind an ongoing notification,
 * keeps it alive, and RecipeExtractionState + a final notification report back the result
 * whether or not the app is in the foreground when it lands.
 */
class RecipeExtractionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_CANCEL) {
            currentJob?.cancel()
            currentJob = null
            RecipeExtractionState.resetToInitial()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Only reachable with a null intent if the system restarts this service with no
        // pending redelivery (we return START_REDELIVER_INTENT below, so that shouldn't
        // happen in practice) — nothing to run in that case.
        if (intent == null) {
            return START_NOT_STICKY
        }

        // Don't let a second share/request interrupt one that's already cooking. Cancelling
        // it to start the new one would silently throw away whatever was already in
        // flight — exactly the kind of silent loss this whole service exists to prevent,
        // just triggered by a second share instead of the OS killing the app. Simplest safe
        // behavior: finish what's running; she can re-share the new one once it's done.
        if (currentJob?.isActive == true) {
            Log.w("BakeItOffDebug", "Extração já em andamento, ignorando novo pedido ($action) até terminar.")
            return START_NOT_STICKY
        }

        startForeground(
            NotificationHelper.PROGRESS_NOTIFICATION_ID,
            NotificationHelper.buildProgressNotification(this, progressTextFor(action))
        )

        val app = application as BakeItOffApplication

        currentJob = serviceScope.launch {
            try {
                when (action) {
                    ACTION_PROCESS_MEDIA -> runMediaExtraction(app, intent)
                    ACTION_PROCESS_LINK -> runLinkExtraction(app, intent)
                    ACTION_CREATE_FROM_TEXT -> runTextExtraction(app, intent)
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        // Redeliver the same intent if the process gets killed mid-pipeline (e.g. the
        // system reclaiming memory) so the extraction just restarts from scratch instead
        // of silently vanishing — exactly the failure mode this service exists to avoid.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun runMediaExtraction(app: BakeItOffApplication, intent: Intent) {
        val uris = intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java) ?: arrayListOf()
        val extraPrompt = intent.getStringExtra(EXTRA_EXTRA_PROMPT)

        RecipeExtractionState.setOriginLink(intent.getStringExtra(EXTRA_LINK_TEXT))
        RecipeExtractionState.update(RecipeUiState.Uploading)
        var preparedMedia: PreparedMedia? = null

        try {
            // PHASE 1: local preparation (video compression + image decoding)
            preparedMedia = app.mediaPreparer.prepare(applicationContext, uris)

            val finalPrompt = buildExtractionPrompt(extraPrompt)

            // PHASE 2: network pipeline (upload, polling and extraction, with a silent re-upload on key rotation)
            val outcome = app.extractionRepository.extractFromMedia(
                compressedVideoUri = preparedMedia.compressedVideoUri,
                images = preparedMedia.bitmaps,
                prompt = finalPrompt
            ) { phase ->
                val uiPhase = when (phase) {
                    ExtractionPhase.Uploading -> RecipeUiState.Uploading
                    ExtractionPhase.Extracting -> RecipeUiState.Extracting
                }
                RecipeExtractionState.update(uiPhase)
                updateProgressNotification(uiPhase)
            }

            val finalState = when (outcome) {
                is ExtractionOutcome.Success -> toUiState(RecipeJsonParser.parse(outcome.json))
                is ExtractionOutcome.Failure -> RecipeUiState.Error(outcome.message)
            }
            publishResult(finalState)

        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaPreparationException) {
            publishResult(RecipeUiState.Error(e.message ?: "Falha ao preparar a mídia."))
        } catch (e: Exception) {
            publishResult(RecipeUiState.Error("Erro ao processar mídias: ${e.localizedMessage}"))
        } finally {
            preparedMedia?.compressedVideoUri?.let { app.mediaPreparer.deleteTemporaryVideo(it) }
        }
    }

    private suspend fun runLinkExtraction(app: BakeItOffApplication, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        RecipeExtractionState.update(RecipeUiState.Extracting)

        val recipeJson = app.extractionRepository.extractFromUrl(url)
        if (recipeJson != null) {
            try {
                val recipe = RecipeJsonParser.sanitize(Gson().fromJson(recipeJson, Recipe::class.java))
                publishResult(RecipeUiState.Success(recipe))
            } catch (e: Exception) {
                publishResult(RecipeUiState.Error("A IA retornou um formato inesperado do link."))
            }
        } else {
            publishResult(RecipeUiState.Error("Não foi possível extrair a receita do link."))
        }
    }

    private suspend fun runTextExtraction(app: BakeItOffApplication, intent: Intent) {
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: return
        RecipeExtractionState.update(RecipeUiState.Extracting)

        try {
            val returnedJson = app.extractionRepository.generateFromText(description)
            publishResult(toUiState(RecipeJsonParser.parse(returnedJson)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            publishResult(RecipeUiState.Error("Erro ao gerar a receita: ${e.message}"))
        }
    }

    private fun publishResult(state: RecipeUiState) {
        RecipeExtractionState.update(state)
        when (state) {
            is RecipeUiState.Success -> NotificationHelper.notifySuccess(this, state.recipe.title)
            is RecipeUiState.Error -> NotificationHelper.notifyError(this, state.message)
            else -> {}
        }
    }

    private fun toUiState(result: RecipeParseResult): RecipeUiState = when (result) {
        is RecipeParseResult.Success -> RecipeUiState.Success(result.recipe)
        is RecipeParseResult.Failure -> RecipeUiState.Error(result.message)
    }

    private fun buildExtractionPrompt(extraPrompt: String?): String {
        val basePrompt = "Analise o conteúdo e extraia a receita em JSON."
        return if (!extraPrompt.isNullOrBlank()) {
            """
            $basePrompt

            ATENÇÃO - ADAPTAÇÃO SOLICITADA:
            $extraPrompt

            Aja como um Chef de Cozinha: modifique os ingredientes, as quantidades e o modo de preparo para acomodar perfeitamente essa mudança antes de gerar o JSON. Ajuste as tags conforme necessário.
            """.trimIndent()
        } else {
            basePrompt
        }
    }

    private fun progressTextFor(action: String?): String = when (action) {
        ACTION_PROCESS_MEDIA -> getString(R.string.enviando_midias)
        else -> getString(R.string.ia_extraindo_receita)
    }

    private fun updateProgressNotification(state: RecipeUiState) {
        val text = when (state) {
            RecipeUiState.Uploading -> getString(R.string.enviando_midias)
            RecipeUiState.Extracting -> getString(R.string.ia_extraindo_receita)
            else -> return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NotificationHelper.PROGRESS_NOTIFICATION_ID, NotificationHelper.buildProgressNotification(this, text))
    }

    companion object {
        const val ACTION_PROCESS_MEDIA = "com.bakeitoff.action.PROCESS_MEDIA"
        const val ACTION_PROCESS_LINK = "com.bakeitoff.action.PROCESS_LINK"
        const val ACTION_CREATE_FROM_TEXT = "com.bakeitoff.action.CREATE_FROM_TEXT"
        const val ACTION_CANCEL = "com.bakeitoff.action.CANCEL"

        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_LINK_TEXT = "extra_link_text"
        const val EXTRA_EXTRA_PROMPT = "extra_extra_prompt"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DESCRIPTION = "extra_description"
    }
}
