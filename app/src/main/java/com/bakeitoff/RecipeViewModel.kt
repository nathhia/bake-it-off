package com.bakeitoff

import GeminiFileUploader
import GenerativeFile
import KeyRotatedException
import Receita
import RecipeExtractor
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update


import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.bakeitoff.RecipeUiState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.Normalizer

// Representa os estados da tela durante o processo
sealed interface RecipeUiState {
    object Initial : RecipeUiState
    object Idle : RecipeUiState
    object Uploading : RecipeUiState
    object Extracting : RecipeUiState
    data class Success(val receita: Receita) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

class RecipeViewModel(
    private val uploader: GeminiFileUploader,
    private val extractor: RecipeExtractor,
    private val notionRepository: NotionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeUiState>(RecipeUiState.Initial)
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()
    private var currentLink: String? = null

    fun processarReceita(
        context: Context,
        sharedVideoUri: Uri?,
        instrucaoExtra: String?,
        sharedImageUris: List<Uri> = emptyList() // A lista de prints da legenda
    ) {
        viewModelScope.launch {
            _uiState.value = RecipeUiState.Uploading

            // 1. Converte as URIs das imagens em Bitmaps (Roda em background)
            val prints: List<Bitmap> = getBitmapsFromUris(context, sharedImageUris)

            // 2. Faz o upload do vídeo (se existir)
            var fileUriStr: String? = null
            if (sharedVideoUri != null) {
                fileUriStr = uploader.uploadVideo(sharedVideoUri)

                // Se o upload falhar e não tivermos prints, encerra com erro
                if (fileUriStr == null && prints.isEmpty()) {
                    _uiState.value = RecipeUiState.Error("Falha ao processar a mídia.")
                    return@launch
                }
            }

            // 3. Inicia a extração na IA
            _uiState.value = RecipeUiState.Extracting

            val promptBase = "Analise o conteúdo e extraia a receita em JSON."

            val promptFinal = if (!instrucaoExtra.isNullOrBlank()) {
                """
            $promptBase
            
            ATENÇÃO - ADAPTAÇÃO SOLICITADA:
            $instrucaoExtra
            
            Aja como um Chef de Cozinha: modifique os ingredientes, as quantidades e o modo de preparo para acomodar perfeitamente essa mudança antes de gerar o JSON. Ajuste as tags conforme necessário.
            """.trimIndent()
            } else {
                promptBase
            }

            val jsonReceita = extractor.extractFromMultiMedia(
                videoUri = fileUriStr, // A string que validamos
                images = prints,
                prompt = promptFinal
            )

            Log.d("BakeItOffDebug", "JSON Cru retornado pela IA: \n$jsonReceita")

            // 4. Trata o resultado
            if (jsonReceita != null) {
                try {
                    val receitaObj = Gson().fromJson(jsonReceita, Receita::class.java)
                    _uiState.value = RecipeUiState.Success(receitaObj)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("BakeItOffDebug", "Motivo do Gson falhar: ${e.message}")
                    _uiState.value = RecipeUiState.Error("A IA retornou um formato inesperado.")
                }
            } else {
                _uiState.value = RecipeUiState.Error("Não foi possível extrair a receita pela IA.")
            }
        }
    }

    fun processLink(url: String) {
        viewModelScope.launch {
            _uiState.value = RecipeUiState.Extracting

            // Passamos a URL para o método do Extractor
            val jsonReceita = extractor.extractFromUrl(url)

            if (jsonReceita != null) {
                try {
                    // Converte a string JSON para o objeto Kotlin Receita
                    val receitaObj = Gson().fromJson(jsonReceita, Receita::class.java)

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

    private suspend fun getBitmapsFromUris(context: Context, uris: List<Uri>): List<Bitmap> {
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            // Garante que o Bitmap seja copiado para a memória de forma segura
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null // Ignora a imagem se der erro e continua a lista
                }
            }
        }
    }

    fun resetToInitial() {
        currentLink = null
        _uiState.value = RecipeUiState.Initial
    }

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing = _isCompressing.asStateFlow()

    private val _compressionProgress = MutableStateFlow(0f)
    val compressionProgress = _compressionProgress.asStateFlow()

    fun processMediaUris(uris: List<Uri>, context: Context, linkTexto: String?, promptExtra: String?) {
        currentLink = linkTexto

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = RecipeUiState.Uploading
            var uriComprimidaLocal: Uri? = null

            try {
                // ==========================================
                // FASE 1: PREPARAÇÃO LOCAL (Roda Apenas 1 Vez)
                // ==========================================
                val bitmaps = mutableListOf<Bitmap>()

                // Passa por todos os arquivos para comprimir vídeo e decodificar imagens
                for (uri in uris) {
                    val mimeType = context.contentResolver.getType(uri) ?: ""

                    if (mimeType.startsWith("video") && uriComprimidaLocal == null) {
                        // Comprime e salva a Uri local final
                        val uriComprimida = comprimirVideo(context, uri)
                        if (uriComprimida != null) {
                            uriComprimidaLocal = uriComprimida
                        } else {
                            _uiState.value = RecipeUiState.Error("Falha ao comprimir o vídeo. Tente novamente.")
                            return@launch
                        }
                    } else if (mimeType.startsWith("image")) {
                        // 1. Decodifica a imagem pesada original
                        val bitmapOriginal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        val bitmapOtimizado = bitmapOriginal.redimensionarParaIA(1024)
                        bitmaps.add(bitmapOtimizado)
                    }
                }

                val promptBase = "Analise o conteúdo e extraia a receita em JSON."
                val promptFinal = if (!promptExtra.isNullOrBlank()) {
                    """
                $promptBase
                
                ATENÇÃO - ADAPTAÇÃO SOLICITADA:
                $promptExtra
                
                Aja como um Chef de Cozinha: modifique os ingredientes, as quantidades e o modo de preparo para acomodar perfeitamente essa mudança antes de gerar o JSON. Ajuste as tags conforme necessário.
                """.trimIndent()
                } else {
                    promptBase
                }

                // ==========================================
                // FASE 2: PIPELINE DE REDE (Re-upload Silencioso)
                // ==========================================
                var sucesso = false
                var tentativasPipeline = 0
                val maxTentativasPipeline = ApiKeyManager.sizeKeys

                while (!sucesso && tentativasPipeline < maxTentativasPipeline) {
                    try {
                        var geminiVideoUri: String? = null

                        // 1. Faz upload do vídeo leve (se houver)
                        if (uriComprimidaLocal != null) {
                            _uiState.value = RecipeUiState.Uploading // Garante que a UI atualiza
                            geminiVideoUri = uploader.uploadVideo(uriComprimidaLocal)

                            if (geminiVideoUri == null) {
                                _uiState.value = RecipeUiState.Error("Falha ao fazer o upload do vídeo na nuvem.")
                                return@launch
                            }
                        }

                        // 2. Depois de preparar tudo, manda para a IA extrair
                        _uiState.value = RecipeUiState.Extracting

                        Log.d("BakeItOffDebug", "Iniciando checagem de mídia...")

                        var isReady = geminiVideoUri == null
                        var checks = 0
                        val maxChecks = 24 // ~2 minutos (24 x 5s), vídeos maiores podem demorar mais que 50s pra processar

                        geminiVideoUri?.let { uriNuvem ->
                            while (!isReady && checks < maxChecks) {
                                isReady = uploader.isVideoReady(uriNuvem)
                                if (!isReady) {
                                    Log.d("BakeItOffDebug", "Vídeo ainda processando no Google (checagem ${checks + 1}/$maxChecks)...")
                                    delay(5000) // delay do kotlinx.coroutines não trava a thread
                                    checks++
                                }
                            }
                        }


                        if (!isReady) {
                            _uiState.value = RecipeUiState.Error("O vídeo demorou demais para processar. Tente novamente.")
                            return@launch
                        }

                        val jsonReceita = extractor.extractFromMultiMedia(
                            videoUri = geminiVideoUri,
                            images = bitmaps,
                            prompt = promptFinal
                        )

                        // 3. Se chegou aqui sem jogar exceção, deu certo!
                        parseAndSetState(jsonReceita)
                        sucesso = true

                    } catch (e: KeyRotatedException) {
                        tentativasPipeline++
                        Log.w("BakeItOffDebug", "Chave rotacionada capturada na ViewModel! Refazendo Upload Silencioso... (Tentativa $tentativasPipeline de $maxTentativasPipeline)")
                        delay(3000)
                    }
                }

                if (!sucesso && tentativasPipeline >= maxTentativasPipeline) {
                    _uiState.value = RecipeUiState.Error("Todas as chaves de contingência falharam ou estão sem cota.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = RecipeUiState.Error("Erro ao processar mídias: ${e.localizedMessage}")
            } finally {
                uriComprimidaLocal?.let { deletarVideoTemporario(it) }
            }
        }
    }

    // Uma função auxiliar só para não repetir o mesmo código de ler o JSON nos dois blocos
    private fun parseAndSetState(jsonReceita: String?) {
        if (jsonReceita != null) {
            Log.d("BakeItOffDebug", "Tentando fazer parse do JSON...")

            // 1. Encontra onde o JSON começa
            val inicio = jsonReceita.indexOf('{')

            if (inicio != -1) {
                // 2. Algoritmo rastreador de chaves para achar o final EXATO
                var chavesAbertas = 0
                var fim = -1

                for (i in inicio until jsonReceita.length) {
                    if (jsonReceita[i] == '{') {
                        chavesAbertas++
                    } else if (jsonReceita[i] == '}') {
                        chavesAbertas--
                        if (chavesAbertas == 0) {
                            fim = i // Encontramos a chave que fecha o objeto principal!
                            break
                        }
                    }
                }

                // 3. Se encontrou um começo e um fim válidos, corta e envia pro Gson
                if (fim != -1) {
                    val jsonLimpo = jsonReceita.substring(inicio, fim + 1)

                    try {
                        val receitaObj = Gson().fromJson(jsonLimpo, Receita::class.java)
                        _uiState.value = RecipeUiState.Success(receitaObj)
                    } catch (e: Exception) {
                        Log.e("BakeItOffDebug", "Erro ao converter o JSON limpo: ${e.message}")
                        _uiState.value = RecipeUiState.Error("A IA não retornou um JSON válido.")
                    }
                } else {
                    Log.e("BakeItOffDebug", "Não encontrou o fechamento da chave.")
                    _uiState.value = RecipeUiState.Error("A resposta da IA estava incompleta.")
                }
            } else {
                Log.e("BakeItOffDebug", "Texto não possui estrutura JSON.")
                _uiState.value = RecipeUiState.Error("A resposta da IA não estava no formato correto.")
            }
        } else {
            _uiState.value = RecipeUiState.Error("Não foi possível extrair a receita dessa mídia.")
        }
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

    // 2. Estado para a Tag selecionada (pode ser nula se nenhuma estiver selecionada)
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // 1. Estado para controlar se o filtro de favoritos está ativo
    private val _isFavoriteFilter = MutableStateFlow(false)
    val isFavoriteFilter = _isFavoriteFilter.asStateFlow()

    // 2. Função para alternar o filtro (liga/desliga)
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

    private fun String.prepararParaBusca(): String {
        // 1. Remove os acentos
        val normalizada = Normalizer.normalize(this, Normalizer.Form.NFD)
        val semAcento = normalizada.replace("\\p{Mn}+".toRegex(), "")

        // 2. Substitui hifens por espaços em branco
        val semHifen = semAcento.replace("-", " ")

        // 3. Remove espaços duplos que possam ter sobrado e limpa as bordas
        return semHifen.replace("\\s+".toRegex(), " ").trim()
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


    // 3. A MÁGICA: Uma lista reativa que cruza a busca com a tag e a lista original
//    val receitasExibidas: StateFlow<List<Receita>> = combine(
//        _receitasSalvas,
//        _searchQuery,
//        _selectedTag,
//        _isFavoriteFilter,
//        _statusSelecionado
//    ) { receitas, query, tag, isFavoriteOnly, status ->
//
//
//        var listaFiltrada = receitas
//
////        receitas.forEach { receita ->
////            android.util.Log.d("DEBUG_FAVORITO", "Título: ${receita.titulo} | É favorito? ${receita.favorito}")
////        }
//
//        // Filtra pelo texto digitado
//        if (query.isNotBlank()) {
//            listaFiltrada = listaFiltrada.filter {
//                it.titulo.contains(query, ignoreCase = true)
//            }
//        }
//
//        // Filtra pela tag (ex: clica em "Pescetariano" e só mostra essas receitas)
//        if (tag != null) {
//            listaFiltrada = listaFiltrada.filter {
//                it.tags.contains(tag)
//            }
//        }
//        if (isFavoriteOnly) {
//            listaFiltrada = listaFiltrada.filter { it.favorito }
//        }
//
//        listaFiltrada
//    }.stateIn(
//        scope = viewModelScope,
//        started = SharingStarted.WhileSubscribed(5000),
//        initialValue = emptyList()
//    )

    val receitasExibidas: StateFlow<List<Receita>> = combine(
        _receitasSalvas,   // 1
        _searchQuery,      // 2
        _selectedTags,     // 3
        _isFavoriteFilter, // 4
        _statusSelecionado // 5
    ) { receitas, query, tags, isFavoriteOnly, status -> // 5 variáveis aqui

        val queryLimpa = query.prepararParaBusca().lowercase()
        val termosBuscados = if (queryLimpa.isBlank()) emptyList() else queryLimpa.split(" ")

        receitas.filter { receita ->
            val matchBusca = if (termosBuscados.isEmpty()) {
                true
            } else {
                val tituloPronto = receita.titulo.prepararParaBusca().lowercase()
                val tagsProntas = receita.tags.map { it.prepararParaBusca().lowercase() }

                termosBuscados.all { termo ->
                    tituloPronto.contains(termo) || tagsProntas.any { tagPronta -> tagPronta.contains(termo) }
                }
            }

            val matchTags = tags.all { tag -> receita.tags.contains(tag) }
            val matchFav = !isFavoriteOnly || receita.favorito
            val matchStatus = status == null || receita.status == status

            matchBusca && matchTags && matchFav && matchStatus
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 4. Funções para a interface chamar quando o usuário interagir
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

            val lista = notionRepository.buscarReceitas().collect { listaParcial ->
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

    fun salvarReceitaNoNotion(receita: Receita) {
        viewModelScope.launch {
            _isSavingToNotion.value = true
            try {
                val sucesso = notionRepository.saveRecipe(receita, currentLink)
                if (sucesso) {
                    _uiEvent.emit("Receita salva no Notion! 🎉")
                    carregarReceitasDoNotion() // Atualiza a lista automaticamente
                } else {
                    _uiEvent.emit("Erro ao salvar. Verifique o Logcat!")
                }
            } catch (e: Exception) {
                _uiEvent.emit("Falha de conexão: ${e.message}")
            } finally {
                _isSavingToNotion.value = false
            }
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

                    // 2. ADICIONE ISSO: Atualiza a receita selecionada especificamente
                    // Isso força a tela de detalhes a "re-renderizar" com o novo status de favorito
                    _receitaSelecionada.value = _receitaSelecionada.value?.copy(favorito = novoEstado)
                }
            }
        }
    }

    fun atualizarStatus(receita: Receita, novoStatus: String) {
        viewModelScope.launch {
            receita.id?.let { id ->
                // Chama o repositório que criamos anteriormente
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

    fun cleanTags (){
        _selectedTags.value = emptySet()
    }
    private suspend fun comprimirVideo(context: Context, videoUri: Uri): Uri? = suspendCancellableCoroutine { continuation ->
        _isCompressing.value = true
        _compressionProgress.value = 0f

        VideoCompressor.start(
            context = context,
            uris = listOf(videoUri),
            isStreamable = false,
            sharedStorageConfiguration = SharedStorageConfiguration(
                saveAt = SaveLocation.movies,
                subFolderName = "BakeItOff_Temp"
            ),
            configureWith = Configuration(
                quality = VideoQuality.LOW,
                disableAudio = false,
                keepOriginalResolution = false,
                videoWidth = 480.0,
                videoHeight = 854.0,
                videoNames = listOf("bakeitoff_${System.currentTimeMillis()}"),
                isMinBitrateCheckEnabled = false
            ),
            listener = object : CompressionListener {
                override fun onProgress(index: Int, percent: Float) {
                    _compressionProgress.value = percent
                }

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    _isCompressing.value = false
                    if (path != null) {
                        // Retorna a Uri do novo arquivo leve
                        continuation.resume(Uri.fromFile(File(path)))
                    } else {
                        continuation.resume(null)
                    }
                }

                override fun onFailure(index: Int, failureMessage: String) {
                    _isCompressing.value = false
                    Log.e("BakeItOffDebug", "Falha na compressão: $failureMessage")
                    continuation.resume(null)
                }

                override fun onStart(index: Int) {}

                override fun onCancelled(index: Int) {
                    _isCompressing.value = false
                    continuation.resume(null)
                }
            }
        )
    }
    fun deletarReceita(receita: Receita) {
        viewModelScope.launch {
            receita.id?.let { id ->
                val sucesso = notionRepository.deleteRecipe(id)
                if (sucesso) {
                    _uiEvent.emit("Receita deletada com sucesso! 🗑️")
                    // Atualiza a lista tirando a receita que acabou de ser apagada
                    _receitasSalvas.value = _receitasSalvas.value.filter { it.id != id }
                    // Limpa a seleção para forçar a tela a voltar
                    limparReceitaSelecionada()
                } else {
                    _uiEvent.emit("Erro ao deletar receita.")
                }
            }
        }
    }

    fun criarReceitaPorTexto(descricao: String) {
        viewModelScope.launch {
            // 1. Ativa a tela de carregamento animada
            _uiState.value = RecipeUiState.Extracting

            try {
                // 2. Chama a IA usando o extractor que JÁ EXISTE no seu ViewModel
                val jsonRetornado = extractor.generateFromText(descricao)

                // 3. Manda para o seu parser mágico com contagem de chaves!
                parseAndSetState(jsonRetornado)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = RecipeUiState.Error("Erro ao gerar a receita: ${e.message}")
            }
        }
    }

    private fun deletarVideoTemporario(uri: Uri) {
        try {
            val file = java.io.File(uri.path ?: return)
            if (file.exists()) {
                val deletado = file.delete()
                if (deletado) {
                    Log.d("BakeItOffDebug", "Vídeo temporário deletado com sucesso: ${file.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro ao deletar arquivo temporário: ${e.message}")
        }
    }

    private fun Bitmap.redimensionarParaIA(maxDimension: Int = 1024): Bitmap {
        val width = this.width
        val height = this.height

        // Se a imagem já for menor ou igual ao limite, preservamos a original
        if (width <= maxDimension && height <= maxDimension) {
            return this
        }

        val ratio: Float = width.toFloat() / height.toFloat()
        val finalWidth: Int
        val finalHeight: Int

        // Calcula a proporção garantindo que o lado maior tenha 1024px
        if (ratio > 1) {
            // Imagem na horizontal (Paisagem)
            finalWidth = maxDimension
            finalHeight = (maxDimension / ratio).toInt()
        } else {
            // Imagem na vertical (Retrato - comum em prints) ou Quadrada
            finalHeight = maxDimension
            finalWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(this, finalWidth, finalHeight, true)
    }
}