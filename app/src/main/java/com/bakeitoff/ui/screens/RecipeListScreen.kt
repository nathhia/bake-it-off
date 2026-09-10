package com.bakeitoff.ui.screens

import com.bakeitoff.Receita
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bakeitoff.RecipeViewModel

val lightPink = Color(0xFFF5CFE4)
val lightPurple = Color(0xFFD6DCF2)
val CardBackground = Color(0xFFF0F2F7)

@Composable
fun RecipeListScreen(
    viewModel: RecipeViewModel,
    onRecipeClick: (Receita) -> Unit
) {
    // ATENÇÃO AQUI: Agora escutamos a receitasExibidas (já filtrada) e não a receitasSalvas
    val receitas by viewModel.receitasExibidas.collectAsState()
    val isLoading by viewModel.isLoadingReceitas.collectAsState()

    // Pegamos a lista original inteira SÓ para extrair as tags disponíveis
    val listaOriginal by viewModel.receitasSalvas.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()

    val isFavoriteFilter by viewModel.isFavoriteFilter.collectAsState()

    val statusSelecionado by viewModel.statusSelecionado.collectAsState()

    //val todasAsTags by viewModel.todasAsTags.collectAsState() // (já existente)
    val todosOsStatus by viewModel.todosOsStatus.collectAsState() // NOVO

    LaunchedEffect(Unit) {
        if (listaOriginal.isEmpty()) {
            viewModel.carregarReceitasDoNotion()
        }
    }

    // Pega todas as tags, remove as repetidas e põe em ordem alfabética
    val todasAsTags = listaOriginal
        .flatMap { it.tags } // 1. Pega todas as tags de todas as receitas
        .groupingBy { it }   // 2. Agrupa por nome da tag
        .eachCount()         // 3. Conta quantas vezes cada uma aparece
        .entries             // 4. Transforma em uma lista de pares (Tag, Quantidade)
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value } // Primeiro: Ordena pela quantidade (mais frequente primeiro)
                .thenBy { it.key }                                   // Segundo: Se a quantidade for igual, mantém alfabético
        )
        .map { it.key }

    RecipeListContent(
        receitas = receitas, // Passamos a lista que já sofreu o filtro!
        todasAsTags = todasAsTags,
        searchQuery = searchQuery,
        selectedTags = selectedTags,
        isLoading = isLoading,
        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
        onTagSelect = { viewModel.onTagSelected(it) },
        onRecipeClick = onRecipeClick,
        onFavoriteToggle = { viewModel.toggleFavoriteFilter() },
        isFavoriteFilter = isFavoriteFilter,
        statusSelecionado = statusSelecionado, // Passe o estado
        onStatusChanged = { viewModel.onStatusChanged(it)},
        todosOsStatus = todosOsStatus,
        onClearAllTags = { viewModel.cleanTags()}
    )
}

// ==========================================
// 2. O "Pintor" (Stateless)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListContent(
    receitas: List<Receita>,
    todasAsTags: List<String>,
    searchQuery: String,
    selectedTags: Set<String>,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onTagSelect: (String) -> Unit,
    onRecipeClick: (Receita) -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavoriteFilter: Boolean,
    statusSelecionado: String?,
    onStatusChanged: (String?) -> Unit,
    todosOsStatus: List<String>,
    onClearAllTags: () -> Unit
) {
    Scaffold { paddingValues ->
        Column( // Mudamos o Box principal para Column para empilhar a busca e a lista
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- INDICADOR DE FILTRO ATIVO ---
            if (selectedTags.isNotEmpty() || searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Concatena dinamicamente o texto dependendo do que está ativo
                    val textoFiltro = remember(searchQuery, selectedTags) {
                        val filtrosAtivos = mutableListOf<String>()

                        if (searchQuery.isNotBlank()) {
                            filtrosAtivos.add("\"$searchQuery\"")
                        }
                        if (selectedTags.isNotEmpty()) {
                            filtrosAtivos.add(selectedTags.joinToString(", "))
                        }

                        "Filtrando por: ${filtrosAtivos.joinToString(" + ")}"
                    }

                    Text(
                        text = textoFiltro,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), // Garante que o texto longo não quebre o layout do botão
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Botão de "Limpar tudo"
                    TextButton(onClick = {
                        onSearchQueryChange("") // Limpa o campo de busca
                        onClearAllTags()        // Callback para limpar o Set de tags no ViewModel
                    }) {
                        Text("Limpar")
                    }
                }
            }

            // --- BARRA DE PESQUISA ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar receita...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ícone de Busca") },
                trailingIcon = {
                    // Botão de "X" para limpar a busca rapidamente
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge // Deixa redondinho e moderno
            )

            // --- FILTRO DE TAGS (Rola na horizontal) ---
            if (todasAsTags.isNotEmpty()) {
                TagFilterBar(
                    todasAsTags = todasAsTags,
                    selectedTags = selectedTags,
                    onTagSelect = onTagSelect,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            StatusFilterBar(
                statusSelecionado = statusSelecionado,
                onStatusSelect = { novoStatus -> onStatusChanged(novoStatus) },
                opcoes = todosOsStatus,
                isFavoriteFilter = isFavoriteFilter,
                onFavoriteToggle = onFavoriteToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- A LISTA DE RECEITAS OU LOADING ---
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    receitas.isEmpty() && searchQuery.isBlank() && selectedTags.isEmpty() -> {
                        Text(
                            text = "Nenhuma receita no seu Notion.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    receitas.isEmpty() -> {
                        Text(
                            text = "Nenhuma receita bate com esses filtros.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(receitas, key = { it.id ?: it.titulo }) { receita ->
                                RecipeCard(
                                    receita = receita,
                                    onClick = { onRecipeClick(receita) },
                                    onTagClick = onTagSelect, // Passa o callback para baixo
                                    selectedTags = selectedTags,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeCard(
    receita: Receita,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit, // Callback genérico
    selectedTags: Set<String>
) {
    val LightPurple = CardBackground
    var tagsExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = LightPurple
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp).padding(end = 32.dp)
            ) {
                // Título
                Text(
                    text = receita.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tempo de Preparo
                Text(
                    text = "⏱️ ${receita.tempoPreparo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tags (Exibindo até 3 tags para não poluir o card)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    val tagsToShow = if (tagsExpanded) receita.tags else receita.tags.take(3)

                    tagsToShow.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)

                        AssistChip(
                            onClick = { onTagClick(tag) },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = lightPink,
                                labelColor = Color(0xFF6D4C41)
                            ),
                            border = if (isSelected) BorderStroke(1.dp, Color.White) else null
                        )
                    }

                    // Chip do "+X"
                    if (receita.tags.size > 3) {
                        if (!tagsExpanded) {
                            AssistChip(
                                onClick = { tagsExpanded = true }, // Ação para expandir
                                label = { Text("+${receita.tags.size - 3}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFF8F0F0)
                                ),
                                border = null
                            )
                        } else {
                            AssistChip(
                                onClick = { tagsExpanded = false }, // Ação para recolher
                                label = { Text("Menos") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFF8F0F0)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
            if (receita.favorito) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorito",
                    tint = Color(0xFFE91E63), // Um rosa mais vivo para destacar
                    modifier = Modifier
                        .align(Alignment.TopEnd) // O "pulo do gato" para alinhar
                        .padding(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeListScreenPreview() {
    val mockReceitas = listOf(
        Receita(
            titulo = "Moqueca de Banana da Terra com Peixe Branco",
            tempoPreparo = "45 minutos",
            ingredientes = emptyList(),
            passos = emptyList(),
            tags = listOf("Pescetariano", "Panela", "Prato Principal"),
            favorito = false,
            id = "0",
            status = "Não feito"
        ),
        Receita(
            titulo = "Dadinhos de Tapioca",
            tempoPreparo = "1 hora (inclui geladeira)",
            ingredientes = emptyList(),
            passos = emptyList(),
            tags = listOf("Aperitivo", "Comida de Tabuleiro", "Airfryer"),
            favorito = true,
            id = "0",
            status = "Não feito"
        ),
        Receita(
            titulo = "Pizza Babalou",
            tempoPreparo = "30 minutos",
            ingredientes = emptyList(),
            passos = emptyList(),
            tags = listOf("Forno", "Jantar", "Fácil", "Vegetariano"),
            favorito = false,
            id = "0",
            status = "Não feito"
        )
    )

    // 1. Criamos uma lista estática com as tags para o Preview renderizar o carrossel
    val mockTags = listOf("Airfryer", "Aperitivo", "Fácil", "Forno", "Jantar", "Panela", "Pescetariano", "Prato Principal", "Vegetariano")

    MaterialTheme {
        RecipeListContent(
            receitas = mockReceitas,
            isLoading = false,

            // 2. Preenchemos os novos parâmetros estáticos
            todasAsTags = mockTags,
            searchQuery = "", // Deixe vazio para ver o placeholder, ou escreva "Pizza" para ver o texto digitado
            selectedTags = emptySet(), // Deixe null, ou coloque "Forno" para ver o botão colorido indicando seleção

            // 3. Funções vazias, pois no Preview os cliques não precisam fazer nada
            onSearchQueryChange = {},
            onTagSelect = {},

            onRecipeClick = {},
            onFavoriteToggle = {},
            isFavoriteFilter = false,
            statusSelecionado = null, // Passe o estado
            onStatusChanged = {},
            todosOsStatus = listOf("Feito", "Não feito", "Quero fazer"),
            onClearAllTags = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Caso seu projeto ainda exija para o FilterChip
@Composable
fun StatusFilterBar(
    statusSelecionado: String?,
    onStatusSelect: (String?) -> Unit,
    opcoes: List<String>, // Esta lista vem do seu ViewModel
    isFavoriteFilter: Boolean,
    onFavoriteToggle: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = isFavoriteFilter,
                onClick = onFavoriteToggle,
                label = {
                    Icon(
                        imageVector = if (isFavoriteFilter) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoritos",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = lightPink,
                    containerColor = Color.White
                )
            )
        }

        // Renderizamos apenas a lista dinâmica de status
        items(opcoes) { opcao ->
            // Proteção extra caso "Todos" venha escrito do Notion por engano
            if (opcao != "Todos") {
                val isSelected = (statusSelecionado == opcao)

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        // Se o chip clicado já for o selecionado, manda null para limpar o filtro.
                        // Caso contrário, seleciona a nova opção normalmente.
                        if (isSelected) {
                            onStatusSelect(null)
                        } else {
                            onStatusSelect(opcao)
                        }
                    },
                    label = { Text(opcao) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterBar(
    todasAsTags: List<String>,
    selectedTags: Set<String>,
    onTagSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(todasAsTags) { tag ->
            FilterChip(
                selected = selectedTags.contains(tag), // Verifica no Set
                onClick = { onTagSelect(tag) },
                label = { Text(tag) }
            )
        }
    }
}