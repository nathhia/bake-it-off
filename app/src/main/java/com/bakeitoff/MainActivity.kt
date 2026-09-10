package com.bakeitoff

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bakeitoff.data.gemini.ApiKeyManager
import com.bakeitoff.data.gemini.GeminiFileUploader
import com.bakeitoff.data.gemini.MediaPreparer
import com.bakeitoff.data.gemini.RecipeExtractionRepository
import com.bakeitoff.data.gemini.RecipeExtractor
import com.bakeitoff.data.notion.NotionRepository
import com.bakeitoff.ui.screens.RecipeScreen
import com.bakeitoff.ui.theme.BakeItOffTheme
import com.bakeitoff.viewmodel.RecipeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RecipeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val notionToken = BuildConfig.NOTION_TOKEN
                val notionDatabaseId = BuildConfig.NOTION_DATABASE_ID

                val apiKeyManager = ApiKeyManager()
                val uploader = GeminiFileUploader(applicationContext, apiKeyManager)
                val extractor = RecipeExtractor(apiKeyManager)
                val extractionRepository = RecipeExtractionRepository(uploader, extractor, apiKeyManager)
                val mediaPreparer = MediaPreparer()
                val notionRepo = NotionRepository(notionToken, notionDatabaseId)

                return RecipeViewModel(mediaPreparer, extractionRepository, notionRepo, apiKeyManager) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("BakeItOffDebug", "onCreate chamado! Action: ${intent?.action}, Data: ${intent?.data}")
        processarIntent(intent)

        setContent {
            BakeItOffTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 2. Você chama a tela principal e passa o ViewModel para ela
                    BakeItOffApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processarIntent(intent)
    }

    private fun processarIntent(intent: Intent?) {
        intent?.let { it ->
            val action = it.action
            val type = it.type

            var videoUri: Uri? = null
            val imageUris = mutableListOf<Uri>()

            // Cenário 1: Compartilhamento de MÚLTIPLOS arquivos (Vídeo + Prints)
            if (action == Intent.ACTION_SEND_MULTIPLE) {
                val uris = it.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)

                uris?.forEach { uri ->
                    val mimeType = this.contentResolver.getType(uri) ?: ""
                    if (mimeType.startsWith("video/")) {
                        videoUri = uri
                    } else if (mimeType.startsWith("image/")) {
                        imageUris.add(uri)
                    }
                }

                if (videoUri != null || imageUris.isNotEmpty()) {
                    // Juntamos tudo numa lista só para a nova função
                    val todasAsUris = mutableListOf<Uri>()
                    videoUri?.let { todasAsUris.add(it) }
                    todasAsUris.addAll(imageUris)

                    viewModel.processMediaUris(
                        uris = todasAsUris,
                        context = applicationContext,
                        linkTexto = null,
                        promptExtra = null
                    )
                }
            }
            // Cenário 2: Compartilhamento de UM ÚNICO item (Vídeo, Imagem ou Link)
            else if (action == Intent.ACTION_SEND) {
                val uri = it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: it.clipData?.getItemAt(0)?.uri

                if (uri != null) {
                    val mimeType = this.contentResolver.getType(uri) ?: type ?: ""

                    if (mimeType.startsWith("video/")) {
                        videoUri = uri
                    } else if (mimeType.startsWith("image/")) {
                        imageUris.add(uri)
                    }

                    // Juntamos tudo numa lista só para a nova função
                    val todasAsUris = mutableListOf<Uri>()
                    videoUri?.let { todasAsUris.add(it) }
                    todasAsUris.addAll(imageUris)

                    viewModel.processMediaUris(
                        uris = todasAsUris,
                        context = applicationContext,
                        linkTexto = null,
                        promptExtra = null
                    )
                } else {
                    // Se a URI for nula, tentamos extrair como texto (Link do TikTok/Insta)
                    val sharedText = it.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        Log.d("BakeItOffDebug", "Link detectado, enviando para o ViewModel: $sharedText")
                        viewModel.processLink(sharedText)
                    }
                }
            }
        }

        // Consome o intent para evitar reprocessamento se a Activity for recriada
        // depois (ex: rotação de tela), o que disparia o pipeline de novo do zero.
        setIntent(Intent())
    }
}