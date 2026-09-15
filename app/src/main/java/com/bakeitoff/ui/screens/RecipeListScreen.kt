package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.Recipe
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    onRecipeClick: (Recipe) -> Unit
) {
    // NOTE: we listen to displayedRecipes (already filtered), not savedRecipes
    val recipes by viewModel.displayedRecipes.collectAsState()
    val isLoading by viewModel.isLoadingRecipes.collectAsState()

    // Grab the whole original list JUST to extract the available tags
    val originalList by viewModel.savedRecipes.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()

    val isFavoriteFilter by viewModel.isFavoriteFilter.collectAsState()

    val selectedStatus by viewModel.selectedStatus.collectAsState()

    val allStatuses by viewModel.allStatuses.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecipesIfNeeded()
    }

    // Grabs all tags, removes duplicates and sorts them
    val allTags = originalList
        .flatMap { it.tags } // 1. Grab all tags from every recipe
        .groupingBy { it }   // 2. Group by tag name
        .eachCount()         // 3. Count how many times each one appears
        .entries             // 4. Turn it into a list of (tag, count) pairs
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value } // First: sort by count (most frequent first)
                .thenBy { it.key }                                   // Then: if the count ties, keep alphabetical order
        )
        .map { it.key }

    RecipeListContent(
        recipes = recipes, // Pass the already-filtered list!
        allTags = allTags,
        searchQuery = searchQuery,
        selectedTags = selectedTags,
        isLoading = isLoading,
        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
        onTagSelect = { viewModel.onTagSelected(it) },
        onRecipeClick = onRecipeClick,
        onFavoriteToggle = { viewModel.toggleFavoriteFilter() },
        isFavoriteFilter = isFavoriteFilter,
        selectedStatus = selectedStatus,
        onStatusChanged = { viewModel.onStatusChanged(it)},
        allStatuses = allStatuses,
        onClearAllTags = { viewModel.cleanTags()}
    )
}

// ==========================================
// 2. The "Painter" (stateless)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListContent(
    recipes: List<Recipe>,
    allTags: List<String>,
    searchQuery: String,
    selectedTags: Set<String>,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onTagSelect: (String) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavoriteFilter: Boolean,
    selectedStatus: String?,
    onStatusChanged: (String?) -> Unit,
    allStatuses: List<String>,
    onClearAllTags: () -> Unit
) {
    Scaffold { paddingValues ->
        Column( // Changed the main Box to a Column to stack the search bar and the list
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- ACTIVE FILTER INDICATOR ---
            if (selectedTags.isNotEmpty() || searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Dynamically concatenates the text depending on what's active
                    val filteringByTemplate = stringResource(R.string.filtrando_por)
                    val filterText = remember(searchQuery, selectedTags, filteringByTemplate) {
                        val activeFilters = mutableListOf<String>()

                        if (searchQuery.isNotBlank()) {
                            activeFilters.add("\"$searchQuery\"")
                        }
                        if (selectedTags.isNotEmpty()) {
                            activeFilters.add(selectedTags.joinToString(", "))
                        }

                        String.format(filteringByTemplate, activeFilters.joinToString(" + "))
                    }

                    Text(
                        text = filterText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), // Ensures a long text doesn't break the button's layout
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // "Clear all" button
                    TextButton(onClick = {
                        onSearchQueryChange("") // Clears the search field
                        onClearAllTags()        // Callback to clear the tag Set in the ViewModel
                    }) {
                        Text(stringResource(R.string.limpar))
                    }
                }
            }

            // --- SEARCH BAR ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.buscar_receita)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.icone_de_busca)) },
                trailingIcon = {
                    // "X" button to quickly clear the search
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.limpar_busca))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge // Keeps it round and modern
            )

            // --- TAG FILTER (scrolls horizontally) ---
            if (allTags.isNotEmpty()) {
                TagFilterBar(
                    allTags = allTags,
                    selectedTags = selectedTags,
                    onTagSelect = onTagSelect,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            StatusFilterBar(
                selectedStatus = selectedStatus,
                onStatusSelect = { newStatus -> onStatusChanged(newStatus) },
                options = allStatuses,
                isFavoriteFilter = isFavoriteFilter,
                onFavoriteToggle = onFavoriteToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- THE RECIPE LIST OR LOADING STATE ---
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    recipes.isEmpty() && searchQuery.isBlank() && selectedTags.isEmpty() -> {
                        EmptyState(
                            emoji = "🧁",
                            message = stringResource(R.string.nenhuma_receita_notion)
                        )
                    }
                    recipes.isEmpty() -> {
                        EmptyState(
                            emoji = "🔍",
                            message = stringResource(R.string.nenhuma_receita_filtros)
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()

                        // Force the list back to the top every time this screen is entered.
                        // Without this, if a new recipe just got prepended to the front (e.g.
                        // right after saving one), Compose's key-based scroll anchoring keeps
                        // whatever was already on screen exactly where it was — which means the
                        // brand new recipe lands above the visible area instead of front and
                        // center, and looks like it "isn't there" until scrolled up to manually.
                        LaunchedEffect(Unit) {
                            listState.scrollToItem(0)
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recipes, key = { it.id ?: it.title }) { recipe ->
                                RecipeCard(
                                    recipe = recipe,
                                    onClick = { onRecipeClick(recipe) },
                                    onTagClick = onTagSelect, // Passes the callback down
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
    val mockRecipes = listOf(
        Recipe(
            title = "Moqueca de Banana da Terra com Peixe Branco",
            prepTime = "45 minutos",
            ingredients = emptyList(),
            steps = emptyList(),
            tags = listOf("Pescetariano", "Panela", "Prato Principal"),
            favorite = false,
            id = "0",
            status = "Não feito"
        ),
        Recipe(
            title = "Dadinhos de Tapioca",
            prepTime = "1 hora (inclui geladeira)",
            ingredients = emptyList(),
            steps = emptyList(),
            tags = listOf("Aperitivo", "Comida de Tabuleiro", "Airfryer"),
            favorite = true,
            id = "0",
            status = "Não feito"
        ),
        Recipe(
            title = "Pizza Babalou",
            prepTime = "30 minutos",
            ingredients = emptyList(),
            steps = emptyList(),
            tags = listOf("Forno", "Jantar", "Fácil", "Vegetariano"),
            favorite = false,
            id = "0",
            status = "Não feito"
        )
    )

    // 1. Static list of tags so the Preview can render the carousel
    val mockTags = listOf("Airfryer", "Aperitivo", "Fácil", "Forno", "Jantar", "Panela", "Pescetariano", "Prato Principal", "Vegetariano")

    MaterialTheme {
        RecipeListContent(
            recipes = mockRecipes,
            isLoading = false,

            // 2. Fill in the new static parameters
            allTags = mockTags,
            searchQuery = "", // Leave empty to see the placeholder, or write "Pizza" to see typed text
            selectedTags = emptySet(), // Leave empty, or add "Forno" to see the colored selected button

            // 3. Empty functions, since clicks in the Preview don't need to do anything
            onSearchQueryChange = {},
            onTagSelect = {},

            onRecipeClick = {},
            onFavoriteToggle = {},
            isFavoriteFilter = false,
            selectedStatus = null,
            onStatusChanged = {},
            allStatuses = listOf("Feito", "Não feito", "Quero fazer"),
            onClearAllTags = {}
        )
    }
}
