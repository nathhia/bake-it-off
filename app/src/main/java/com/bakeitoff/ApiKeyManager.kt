package com.bakeitoff

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ApiKeyManager{
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

    // Índice da chave atual
    private var currentIndex = 0

    fun getApiKey(): String = keys[currentIndex]

    @Synchronized
    fun toggleKey() {
        // Incrementa o índice e volta para 0 se chegar ao final da lista
        currentIndex = (currentIndex + 1) % keys.size
        _onKeyChanged.tryEmit(getActiveKeyName())
    }

    fun getActiveKeyName(): String = keyNames[currentIndex]
}