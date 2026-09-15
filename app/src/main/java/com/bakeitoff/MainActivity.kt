package com.bakeitoff

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bakeitoff.ui.screens.RecipeScreen
import com.bakeitoff.ui.theme.BakeItOffTheme
import com.bakeitoff.viewmodel.RecipeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RecipeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as BakeItOffApplication
                return RecipeViewModel(app, app.mediaPreparer, app.notionRepository, app.apiKeyManager) as T
            }
        }
    }

    // The pipeline runs fine without this permission (the foreground service still keeps it
    // alive) — this only controls whether she actually sees the progress/result notifications.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Log.d("BakeItOffDebug", "onCreate chamado! Action: ${intent?.action}, Data: ${intent?.data}")
        processIntent(intent)

        setContent {
            BakeItOffTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Calls the main screen and passes it the ViewModel
                    BakeItOffApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        intent?.let { it ->
            val action = it.action
            val type = it.type

            var videoUri: Uri? = null
            val imageUris = mutableListOf<Uri>()

            // Scenario 1: sharing MULTIPLE files (video + screenshots)
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
                    // Merge everything into a single list for the new function
                    val allUris = mutableListOf<Uri>()
                    videoUri?.let { allUris.add(it) }
                    allUris.addAll(imageUris)

                    viewModel.processMediaUris(
                        uris = allUris,
                        linkText = null,
                        extraPrompt = null
                    )
                }
            }
            // Scenario 2: sharing a SINGLE item (video, image or link)
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

                    // Merge everything into a single list for the new function
                    val allUris = mutableListOf<Uri>()
                    videoUri?.let { allUris.add(it) }
                    allUris.addAll(imageUris)

                    viewModel.processMediaUris(
                        uris = allUris,
                        linkText = null,
                        extraPrompt = null
                    )
                } else {
                    // If the URI is null, try to extract it as text (a TikTok/Insta link)
                    val sharedText = it.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        Log.d("BakeItOffDebug", "Link detectado, enviando para o ViewModel: $sharedText")
                        viewModel.processLink(sharedText)
                    }
                }
            }
        }

        // Consumes the intent to avoid reprocessing it if the Activity gets recreated
        // later (e.g. screen rotation), which would fire the whole pipeline again from scratch.
        setIntent(Intent())
    }
}
