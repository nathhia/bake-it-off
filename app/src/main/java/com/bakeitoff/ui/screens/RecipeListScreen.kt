package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.Receita
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakeitoff.R
import com.bakeitoff.ui.components.EmptyState
import com.bakeitoff.ui.components.RecipeCard
import com.bakeitoff.ui.components.StatusFilterBar
import com.bakeitoff.ui.components.TagFilterBar
import com.bakeitoff.viewmodel.RecipeViewModel

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
        viewModel.carregarReceitasSeNecessario()
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
                    val filtrandoPorTemplate = stringResource(R.string.filtrando_por)
                    val textoFiltro = remember(searchQuery, selectedTags, filtrandoPorTemplate) {
                        val filtrosAtivos = mutableListOf<String>()

                        if (searchQuery.isNotBlank()) {
                            filtrosAtivos.add("\"$searchQuery\"")
                        }
                        if (selectedTags.isNotEmpty()) {
                            filtrosAtivos.add(selectedTags.joinToString(", "))
                        }

                        String.format(filtrandoPorTemplate, filtrosAtivos.joinToString(" + "))
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
                        Text(stringResource(R.string.limpar))
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
                placeholder = { Text(stringResource(R.string.buscar_receita)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.icone_de_busca)) },
                trailingIcon = {
                    // Botão de "X" para limpar a busca rapidamente
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.limpar_busca))
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
                        EmptyState(
                            emoji = "🧁",
                            message = stringResource(R.string.nenhuma_receita_notion)
                        )
                    }
                    receitas.isEmpty() -> {
                        EmptyState(
                            emoji = "🔍",
                            message = stringResource(R.string.nenhuma_receita_filtros)
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
