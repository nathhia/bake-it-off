package com.bakeitoff

import android.app.Application
import com.bakeitoff.data.gemini.ApiKeyManager
import com.bakeitoff.data.gemini.GeminiFileUploader
import com.bakeitoff.data.gemini.MediaPreparer
import com.bakeitoff.data.gemini.RecipeExtractionRepository
import com.bakeitoff.data.gemini.RecipeExtractor
import com.bakeitoff.data.notion.NotionRepository
import com.bakeitoff.notifications.NotificationHelper

class BakeItOffApplication : Application() {

    // Shared singletons: RecipeExtractionService runs the actual pipeline in the
    // background, while RecipeViewModel only observes progress (isCompressing,
    // compressionProgress, key-rotation toasts) — both need the exact same
    // instances, otherwise the UI would watch state nobody is updating.
    val apiKeyManager by lazy { ApiKeyManager() }
    val mediaPreparer by lazy { MediaPreparer() }
    val notionRepository by lazy { NotionRepository(BuildConfig.NOTION_TOKEN, BuildConfig.NOTION_DATABASE_ID) }
    val extractionRepository by lazy {
        val uploader = GeminiFileUploader(this, apiKeyManager)
        val extractor = RecipeExtractor(apiKeyManager)
        RecipeExtractionRepository(uploader, extractor, apiKeyManager)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
