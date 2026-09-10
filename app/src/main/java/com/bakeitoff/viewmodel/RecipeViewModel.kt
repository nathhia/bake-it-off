package com.bakeitoff.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakeitoff.data.gemini.ApiKeyManager
import com.bakeitoff.data.gemini.ExtractionOutcome
import com.bakeitoff.data.gemini.ExtractionPhase
import com.bakeitoff.data.gemini.MediaPreparer
import com.bakeitoff.data.gemini.MediaPreparationException
import com.bakeitoff.data.gemini.PreparedMedia
import com.bakeitoff.data.gemini.RecipeExtractionRepository
import com.bakeitoff.data.model.Receita
import com.bakeitoff.data.notion.NotionRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Representa os estados da tela durante o processo
sealed interface RecipeUiState {
    object Initial : RecipeUiState
    object Uploading : RecipeUiState
    object Extracting : RecipeUiState
    data class Success(val receita: Receita) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

class RecipeViewModel(
    private val mediaPreparer: MediaPreparer,
    private val extractionRepository: RecipeExtractionRepository,
    private val notionRepository: NotionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeUiState>(RecipeUiState.Initial)
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()
    private var currentLink: String? = null

    val isCompressing: StateFlow<Boolean> = mediaPreparer.isCompressing
    val compressionProgress: StateFlow<Float> = mediaPreparer.compressionProgress

    fun processLink(url: String) {
        viewModelScope.launch {
            _uiState.value = RecipeUiState.Extracting

            // Passamos a URL para o repositório de extração
            val jsonReceita = extractionRepository.extractFromUrl(url)

            if (jsonReceita != null) {
                try {
                    // Converte a string JSON para o objeto Kotlin Receita
                    val receitaObj = RecipeJsonParser.sanitize(Gson().fromJson(jsonReceita, Receita::class.java))

                    // Agora sim, passamos o objeto tipado para o estado de Sucesso
                    _uiState.value = RecipeUiState.Success(receitaObj)

                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = RecipeUiState.Error("A IA retornou um formato inesperado do link.")
                }
            } else {
                _uiState.value = RecipeUiState.Error("Não foi possível extrair a receita do link.")
            }
        }
    }

    fun resetToInitial() {
        currentLink = null
        _uiState.value = RecipeUiState.Initial
    }

    fun processMediaUris(uris: List<Uri>, context: Context, linkTexto: String?, promptExtra: String?) {
        currentLink = linkTexto

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = RecipeUiState.Uploading
            var preparedMedia: PreparedMedia? = null

            try {
                // FASE 1: Preparação local (compressão de vídeo + decodificação de imagens)
                preparedMedia = mediaPreparer.prepare(context, uris)

                val promptFinal = buildExtractionPrompt(promptExtra)

                // FASE 2: Pipeline de rede (upload, polling e extração, com re-upload silencioso em caso de rotação de chave)
                val outcome = extractionRepository.extractFromMedia(
                    compressedVideoUri = preparedMedia.compressedVideoUri,
                    images = preparedMedia.bitmaps,
                    prompt = promptFinal
                ) { phase ->
                    _uiState.value = when (phase) {
                        ExtractionPhase.Uploading -> RecipeUiState.Uploading
                        ExtractionPhase.Extracting -> RecipeUiState.Extracting
                    }
                }

                _uiState.value = when (outcome) {
                    is ExtractionOutcome.Success -> toUiState(RecipeJsonParser.parse(outcome.json))
                    is ExtractionOutcome.Failure -> RecipeUiState.Error(outcome.message)
                }

            } catch (e: MediaPreparationException) {
                _uiState.value = RecipeUiState.Error(e.message ?: "Falha ao preparar a mídia.")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = RecipeUiState.Error("Erro ao processar mídias: ${e.localizedMessage}")
            } finally {
                preparedMedia?.compressedVideoUri?.let { mediaPreparer.deleteTemporaryVideo(it) }
            }
        }
    }

    private fun buildExtractionPrompt(promptExtra: String?): String {
        val promptBase = "Analise o conteúdo e extraia a receita em JSON."
        return if (!promptExtra.isNullOrBlank()) {
            """
            $promptBase

            ATENÇÃO - ADAPTAÇÃO SOLICITADA:
            $promptExtra

            Aja como um Chef de Cozinha: modifique os ingredientes, as quantidades e o modo de preparo para acomodar perfeitamente essa mudança antes de gerar o JSON. Ajuste as tags conforme necessário.
            """.trimIndent()
        } else {
            promptBase
        }
    }

    private fun toUiState(result: RecipeParseResult): RecipeUiState = when (result) {
        is RecipeParseResult.Success -> RecipeUiState.Success(result.receita)
        is RecipeParseResult.Failure -> RecipeUiState.Error(result.message)
    }

    private val _isSavingToNotion = mutableStateOf(false)
    val isSavingToNotion: State<Boolean> = _isSavingToNotion

    private val _receitaSelecionada = MutableStateFlow<Receita?>(null)
    val receitaSelecionada: StateFlow<Receita?> = _receitaSelecionada.asStateFlow()

    // Função que a tela de lista vai chamar ao clicar no card
    fun selecionarReceita(receita: Receita) {
        _receitaSelecionada.value = receita
    }

    // Função para limpar quando sairmos da tela de detalhes
    fun limparReceitaSelecionada() {
        _receitaSelecionada.value = null
    }

    // Estado para guardar a lista de receitas que vem do Notion
    private val _receitasSalvas = MutableStateFlow<List<Receita>>(emptyList())
    val receitasSalvas: StateFlow<List<Receita>> = _receitasSalvas.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Estado para controlar se o filtro de favoritos está ativo
    private val _isFavoriteFilter = MutableStateFlow(false)
    val isFavoriteFilter = _isFavoriteFilter.asStateFlow()

    fun toggleFavoriteFilter() {
        _isFavoriteFilter.value = !_isFavoriteFilter.value
    }

    private val _statusSelecionado = MutableStateFlow<String?>(null)
    val statusSelecionado: StateFlow<String?> = _statusSelecionado.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    fun onTagSelected(tag: String) {
        _selectedTags.update { tagsAtuais ->
            if (tagsAtuais.contains(tag)) {
                tagsAtuais - tag // Se já existe, remove (deseleciona)
            } else {
                tagsAtuais + tag // Se não existe, adiciona
            }
        }
    }

    val todosOsStatus: StateFlow<List<String>> = _receitasSalvas.map { lista ->
        lista.mapNotNull { it.status } // Remove nulos
            .filter { it.isNotBlank() } // Remove strings vazias
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onStatusChanged(novoStatus: String?) {
        _statusSelecionado.value = novoStatus
    }

    // Lista reativa que cruza a busca com as tags, favoritos e status selecionados
    val receitasExibidas: StateFlow<List<Receita>> = combine(
        _receitasSalvas,
        _searchQuery,
        _selectedTags,
        _isFavoriteFilter,
        _statusSelecionado
    ) { receitas, query, tags, isFavoriteOnly, status ->
        RecipeFilter.apply(receitas, query, tags, isFavoriteOnly, status)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Funções para a interface chamar quando o usuário interagir
    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    // Estado para mostrar uma bolinha de carregamento enquanto baixa os dados
    private val _isLoadingReceitas = MutableStateFlow(false)
    val isLoadingReceitas: StateFlow<Boolean> = _isLoadingReceitas.asStateFlow()

    // Função que busca os dados
    fun carregarReceitasDoNotion() {
        viewModelScope.launch {
            _isLoadingReceitas.value = true

            notionRepository.buscarReceitas().collect { listaParcial ->
                _receitasSalvas.value = listaParcial

                _isLoadingReceitas.value = false
            }
        }
    }

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        // Escuta globalmente as trocas de chave do ApiKeyManager
        viewModelScope.launch {
            ApiKeyManager.onKeyChanged.collect { novoNome ->
                _uiEvent.emit("Chave trocada para: $novoNome 🔄")
            }
        }
    }

    // Suspend e retorna o resultado: quem chama decide o que fazer na tela
    // (fechar a edição, navegar, etc.) só depois de saber se realmente salvou.
    suspend fun salvarReceitaNoNotion(receita: Receita): Boolean {
        _isSavingToNotion.value = true
        try {
            val sucesso = notionRepository.saveRecipe(receita, currentLink)
            if (sucesso) {
                _uiEvent.emit("Receita salva no Notion! 🎉")
                // Se é edição de receita existente, reflete o resultado salvo imediatamente
                // (sem isso, a tela de detalhes só veria a edição depois de recarregar tudo).
                if (!receita.id.isNullOrBlank()) {
                    _receitaSelecionada.value = receita
                }
                carregarReceitasDoNotion() // Atualiza a lista automaticamente
            } else {
                _uiEvent.emit("Erro ao salvar. Verifique o Logcat!")
            }
            return sucesso
        } catch (e: Exception) {
            _uiEvent.emit("Falha de conexão: ${e.message}")
            return false
        } finally {
            _isSavingToNotion.value = false
        }
    }

    fun toggleFavorito(receita: Receita) {
        Log.d("BakeItOffDebug", "Clique recebido para receita: ${receita.titulo}")
        viewModelScope.launch {
            val novoEstado = !receita.favorito
            receita.id?.let { id ->
                val sucesso = notionRepository.updateFavorito(pageId = id, favorito = novoEstado)

                if (sucesso) {
                    // 1. Atualiza a lista geral (para a Home, se você voltar)
                    _receitasSalvas.value = _receitasSalvas.value.map {
                        if (it.id == receita.id) it.copy(favorito = novoEstado) else it
                    }.toList()

                    // 2. Atualiza a receita selecionada especificamente
                    // Isso força a tela de detalhes a "re-renderizar" com o novo status de favorito
                    _receitaSelecionada.value = _receitaSelecionada.value?.copy(favorito = novoEstado)
                }
            }
        }
    }

    fun atualizarStatus(receita: Receita, novoStatus: String) {
        viewModelScope.launch {
            receita.id?.let { id ->
                val sucesso = notionRepository.updateStatus(id, novoStatus)

                if (sucesso) {
                    // Atualiza a lista geral
                    _receitasSalvas.value = _receitasSalvas.value.map {
                        if (it.id == receita.id) it.copy(status = novoStatus) else it
                    }.toList()

                    // Atualiza a receita selecionada para refletir na tela imediatamente
                    _receitaSelecionada.value = _receitaSelecionada.value?.copy(status = novoStatus)

                    Log.d("BakeItOffDebug", "Status atualizado para: $novoStatus")
                } else {
                    Log.e("BakeItOffDebug", "Erro ao atualizar status no Notion")
                }
            }
        }
    }

    fun cleanTags() {
        _selectedTags.value = emptySet()
    }

    // Suspend e retorna o resultado: a tela só navega de volta se a exclusão
    // realmente deu certo, em vez de sair da tela antes de saber o resultado.
    suspend fun deletarReceita(receita: Receita): Boolean {
        val id = receita.id ?: return false
        val sucesso = notionRepository.deleteRecipe(id)
        if (sucesso) {
            _uiEvent.emit("Receita deletada com sucesso! 🗑️")
            // Atualiza a lista tirando a receita que acabou de ser apagada
            _receitasSalvas.value = _receitasSalvas.value.filter { it.id != id }
            limparReceitaSelecionada()
        } else {
            _uiEvent.emit("Erro ao deletar receita.")
        }
        return sucesso
    }

    fun criarReceitaPorTexto(descricao: String) {
        viewModelScope.launch {
            // 1. Ativa a tela de carregamento animada
            _uiState.value = RecipeUiState.Extracting

            try {
                // 2. Chama a IA usando o repositório de extração
                val jsonRetornado = extractionRepository.generateFromText(descricao)

                // 3. Manda para o parser com contagem de chaves
                _uiState.value = toUiState(RecipeJsonParser.parse(jsonRetornado))

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = RecipeUiState.Error("Erro ao gerar a receita: ${e.message}")
            }
        }
    }
}
