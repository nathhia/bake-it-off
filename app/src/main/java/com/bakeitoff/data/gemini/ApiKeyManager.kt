package com.bakeitoff.data.gemini

import com.bakeitoff.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Rotates between each contributor's Gemini API key when one hits its quota
 * (429). One instance per app (injected via MainActivity) instead of a global
 * singleton, so code that depends on it can be tested with an isolated
 * instance instead of sharing global state across tests.
 */
class ApiKeyManager {
    private val api_key_nathaff = BuildConfig.GEMINI_API_KEY_NATHAFF
    private val api_key_nathhia = BuildConfig.GEMINI_API_KEY_NATHHIA
    private val api_key_anderson = BuildConfig.GEMINI_API_KEY_ANDERSON
    private val api_key_felipe = BuildConfig.GEMINI_API_KEY_FELIPE

    private val keys = listOf(
        api_key_nathaff,
        api_key_nathhia,
        api_key_anderson,
        api_key_felipe
    )

    val sizeKeys = keys.size

    private val _onKeyChanged = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val onKeyChanged = _onKeyChanged.asSharedFlow()

    private val keyNames = listOf("Nathaff", "Nathhia", "Anderson", "Felipe")

    // Index of the current key
    private var currentIndex = 0

    fun getApiKey(): String = keys[currentIndex]

    @Synchronized
    fun toggleKey() {
        // Increments the index and wraps back to 0 at the end of the list
        currentIndex = (currentIndex + 1) % keys.size
        _onKeyChanged.tryEmit(getActiveKeyName())
    }

    fun getActiveKeyName(): String = keyNames[currentIndex]
}