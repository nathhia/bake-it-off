package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.DicasComentario
import com.bakeitoff.data.model.Receita
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.bakeitoff.viewmodel.RecipeUiState
import com.bakeitoff.viewmodel.RecipeViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(viewModel: RecipeViewModel, onNavigateToList: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var linkTexto by rememberSaveable { mutableStateOf("") }
    var instrucaoExtra by rememberSaveable { mutableStateOf("") }
    var savedUrisStrings by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { mensagem ->
            Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is RecipeUiState.Success) {
            linkTexto = ""
            instrucaoExtra = ""
            savedUrisStrings = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bake It Off 🧁") })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is RecipeUiState.Uploading -> {
                    // Observamos os estados de compressão apenas quando estamos no Uploading
                    val isCompressing by viewModel.isCompressing.collectAsStateWithLifecycle()
                    val progress by viewModel.compressionProgress.collectAsStateWithLifecycle()

                    if (isCompressing) {
                        // Tela de progresso da compressão do vídeo
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Divide por 100f porque o Compose exige um valor entre 0.0 e 1.0
                            CircularProgressIndicator(progress = { progress / 100f })
                            Spacer(Modifier.height(16.dp))
                            Text("Otimizando vídeo: ${progress.toInt()}%")
                            Text(
                                "Isso economiza seus tokens da IA!",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Quando não está comprimindo (ou já terminou), mostra a mensagem normal de upload
                        LoadingScreen("Enviando mídias para a nuvem...")
                    }
                }
                is RecipeUiState.Extracting -> LoadingScreen("IA extraindo receita...")
                is RecipeUiState.Error -> {
                    ErrorScreen(state.message, viewModel)
                }
                is RecipeUiState.Success -> {
                    ExtractedRecipePreview(state.receita, viewModel)
                }
                else -> InitialScreen(
                    linkTexto = linkTexto,
                    onLinkChange = { linkTexto = it },
                    instrucaoExtra = instrucaoExtra,
                    onInstrucaoChange = { instrucaoExtra = it },
                    savedUrisStrings = savedUrisStrings,
                    onUrisChange = { savedUrisStrings = it },
                    onMediaSelected = { uris, link, promptExtra ->
                        viewModel.processMediaUris(uris, context.applicationContext, link, promptExtra)
                    },
                    onTextOnlySubmit = { texto ->
                        viewModel.criarReceitaPorTexto(texto)
                    },
                    onNavigateToList = onNavigateToList,
                    onLogoLongPress = { viewModel.toggleApiKey() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractedRecipePreview(receita: Receita, viewModel: RecipeViewModel) {
    val isSaving by viewModel.isSavingToNotion
    val coroutineScope = rememberCoroutineScope()

    var tagsEditaveis = remember { mutableStateListOf<String>().apply { addAll(receita.tags) } }
    var novaTag by remember { mutableStateOf("") }

    BackHandler {
        viewModel.resetToInitial() // Volta para o estado inicial (InitialScreen)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Título e Tempo
        item {
            Text(text = receita.titulo, style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text(text = receita.tempoPreparo, color = Color.Gray)
            }
        }

        // 2. Tags (Chips)
        item {
            Text("Tags", style = MaterialTheme.typography.titleLarge)

            // Renderiza as tags atuais com um botão de "X" para remover
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tagsEditaveis.forEach { tag ->
                    key(tag) {
                        InputChip(
                            selected = true,
                            onClick = { }, // Não faz nada ao clicar no corpo
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        // Remove a tag da lista
                                        tagsEditaveis.remove(tag)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remover Tag")
                                }
                            }
                        )
                    }
                }
            }

            // Campo de texto para adicionar uma nova tag
            OutlinedTextField(
                value = novaTag,
                onValueChange = { novaTag = it },
                label = { Text("Adicionar nova tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val tagLimpa = novaTag.trim()
                        // Só adiciona se não for vazio e se a tag já não existir
                        if (tagLimpa.isNotEmpty() && !tagsEditaveis.contains(tagLimpa)) {
                            tagsEditaveis.add(tagLimpa)
                            novaTag = "" // Limpa o campo após adicionar
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar Tag")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Ingredientes
        item {
            Text(
                text = "Ingredientes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp) // Um respiro entre o título e o Card
            )

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    // 1. Agrupa os ingredientes pela seção
                    val ingredientesAgrupados = receita.ingredientes.groupBy { it.secao }

                    ingredientesAgrupados.forEach { (secao, lista) ->
                        if (!secao.isNullOrEmpty()) {
                            Text(
                                text = secao,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, // Dá um destaque na cor base do seu app
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        // 3. Desenha os itens
                        lista.forEach { ing ->
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 4.dp) // Primeiro aplica o espaçamento vertical
                                    .padding(start = if (secao.isNullOrEmpty()) 0.dp else 8.dp) // Depois aplica o recuo lateral
                            ) {
                                Text("• ", fontWeight = FontWeight.Bold)

                                val textoIngrediente = if (ing.quantidade.isNullOrBlank() && ing.unidade.isNullOrBlank()) {
                                    ing.item
                                } else {
                                    // Cast pra nullable de propósito: o Gson ignora o tipo não-nulo do Kotlin
                                    // e pode deixar isso null quando a IA não especifica quantidade/unidade.
                                    val q = (ing.quantidade as String?)?.trim() ?: ""
                                    val u = (ing.unidade as String?)?.trim() ?: ""
                                    val ligacao = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "
                                    "$q $u$ligacao${ing.item}".replace(Regex("\\s+"), " ").trim()
                                }

                                Text(textoIngrediente)
                            }
                        }
                    }
                }
            }
        }

        // 4. Modo de Preparo
        item {
            Text("Passo a Passo", style = MaterialTheme.typography.titleLarge)
        }

        itemsIndexed(receita.passos) { index, passo ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${index + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(passo)
            }
        }

        item {
            DicasSection(dicas = receita.dicas_video)
        }

        // 5. Botão Salvar no Notion
        item {
            Button(
                onClick = {
                    // Cast pra nullable de propósito: a IA nunca inclui "Status" no JSON extraído,
                    // e o Gson ignora o valor padrão do Kotlin, deixando status null nesse ponto.
                    val statusAtual = (receita.status as String?) ?: "Não feito"
                    val receitaAtualizada = receita.copy(tags = tagsEditaveis, status = statusAtual)
                    coroutineScope.launch {
                        viewModel.salvarReceitaNoNotion(receitaAtualizada)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp),
                enabled = !isSaving // Desabilita o botão enquanto salva
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Salvar no Notion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
fun ErrorScreen(message: String, viewModel: RecipeViewModel) {
    BackHandler(enabled = true) {
        viewModel.resetToInitial()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ops! Algo deu errado:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error // Usa a cor de erro padrão do Material 3
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, textAlign = TextAlign.Center)
    }
}

@Composable
fun InitialScreen(
    linkTexto: String,
    onLinkChange: (String) -> Unit,
    instrucaoExtra: String,
    onInstrucaoChange: (String) -> Unit,
    savedUrisStrings: List<String>,
    onUrisChange: (List<String>) -> Unit,
    onMediaSelected: (List<Uri>, String?, String?) -> Unit,
    onTextOnlySubmit: (String) -> Unit,
    onNavigateToList: () -> Unit,
    onLogoLongPress: () -> Unit
) {
    val selectedUris = savedUrisStrings.map { Uri.parse(it) }

    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onUrisChange(uris.map { it.toString() })
        }
    }

    val isMediaAttached = selectedUris.isNotEmpty() || linkTexto.isNotBlank()

    // Usamos o LazyColumn para abraçar a usabilidade correta de listas/formulários fluídos
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {

        // 1. Cabeçalho Visual (Componentizado)
        item {
            HeaderSection(onLogoLongPress = onLogoLongPress)
        }

        // 2. Seção de Anexo de Mídia (Componentizado)
        item {
            MediaSelectorSection(
                hasAttachedUris = selectedUris.isNotEmpty(),
                attachedUrisCount = selectedUris.size,
                onAttachClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onClearMedia = { onUrisChange(emptyList()) }
            )
        }

        // 3. Campo de Link Opcional
        item {
            OutlinedTextField(
                value = linkTexto,
                onValueChange = { onLinkChange(it) },
                label = { Text("Link do TikTok / Reels (Opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Campo de Entrada de Texto Dinâmico
        item {
            // Inteligência de UI: os textos mudam com base na presença de mídia
            val dynamicLabel = if (isMediaAttached) "Adaptações na Receita (Opcional)" else "O que você quer comer?"
            val dynamicPlaceholder = if (isMediaAttached) "Ex: Trocar frango por grão de bico" else "Ex: Uma torta de maçã clássica usando massa de mascarpone"

            OutlinedTextField(
                value = instrucaoExtra,
                onValueChange = { onInstrucaoChange(it) },
                label = { Text(dynamicLabel) },
                placeholder = { Text(dynamicPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )
        }

        // 5. Botão de Envio Unificado (Submit)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true // Trava o botão na hora
                        if (isMediaAttached) {
                            onMediaSelected(selectedUris, linkTexto, instrucaoExtra)
                        } else if (instrucaoExtra.isNotBlank()) {
                            onTextOnlySubmit(instrucaoExtra)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                // O botão apaga se não tiver texto OU se já estiver processando
                enabled = !isSubmitting && (isMediaAttached || instrucaoExtra.isNotBlank())
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        text = if (isMediaAttached) "Extrair da Mídia ✨" else "Criar Receita com IA ✨",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. Botão de Navegação para o Caderno
        item {
            OutlinedButton(
                onClick = onNavigateToList,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Ver Meu Caderno de Receitas")
            }
        }
    }
}

// ==========================================
// COMPONENTES AUXILIARES EXTRAÍDOS
// ==========================================

@Composable
fun HeaderSection(onLogoLongPress: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Reduzimos o círculo de 120.dp para 80.dp
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🧁",
                    fontSize = 56.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLogoLongPress() })
                    }
                )
            }
        }

        // Reduzimos o espaço de 24.dp para 12.dp
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "O que vamos preparar?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Insira um vídeo, link ou apenas descreva o prato desejado para a inteligência artificial estruturar sua receita.",
            style = MaterialTheme.typography.bodyMedium, // Mudamos de bodyLarge para bodyMedium para ficar mais delicado
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MediaSelectorSection(
    hasAttachedUris: Boolean,
    attachedUrisCount: Int,
    onAttachClick: () -> Unit,
    onClearMedia: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onAttachClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasAttachedUris) "Substituir Mídias" else "Anexar da Galeria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (hasAttachedUris) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "$attachedUrisCount mídia(s) selecionada(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onClearMedia, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar mídias",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DicasSection(dicas: List<DicasComentario>) {
    if (dicas.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Dicas e Comentários",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        dicas.forEach { dica ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (dica.enriquecida) Color(0xFFF3E5F5) else CardBackground
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = dica.texto, style = MaterialTheme.typography.bodyMedium)

                    Text(
                        text = if (dica.enriquecida) "IA · Dica extra" else "Vídeo · Dica original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}