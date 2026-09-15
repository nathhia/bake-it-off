package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.Ingredient
import com.bakeitoff.data.model.Recipe
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakeitoff.R
import com.bakeitoff.ui.components.RecipeTipsSection
import com.bakeitoff.ui.components.StatusSelector
import com.bakeitoff.viewmodel.RecipeViewModel
import kotlinx.coroutines.launch

// ==========================================
// 1. The "Manager" (stateful)
// ==========================================
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeViewModel,
    onBackClick: () -> Unit
) {
    val recipe by viewModel.selectedRecipe.collectAsState()
    val isSaving by viewModel.isSavingToNotion
    val coroutineScope = rememberCoroutineScope()

    // Only runs when the screen first appears (not on every recipe change, otherwise it would
    // duplicate the onBackClick() already fired by the BackHandler/deletion when clearing the selection).
    // If the recipe is already null here (e.g. process restoration), go back to the previous
    // screen instead of leaving a blank screen with no way out.
    LaunchedEffect(Unit) {
        if (recipe == null) {
            onBackClick()
        }
    }

    var isEditing by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    if (recipe == null) {
        return
    }

    BackHandler {
        if (isEditing) {
            isEditing = false // Just cancel the edit
        } else {
            viewModel.clearSelectedRecipe()
            onBackClick() // Back to the list
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.deletar_receita_titulo)) },
            text = { Text(stringResource(R.string.deletar_receita_mensagem, recipe?.title ?: "")) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        val recipeToDelete = recipe!!
                        coroutineScope.launch {
                            isDeleting = true
                            // Only leaves the screen if the deletion actually succeeded — before this,
                            // the app left immediately, and if the deletion failed, the detail screen
                            // for a recipe already erased from memory was left blank.
                            val success = viewModel.deleteRecipe(recipeToDelete)
                            isDeleting = false
                            showDeleteDialog = false
                            if (success) {
                                onBackClick() // Back to the main list
                            }
                        }
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.deletar), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isDeleting, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }

    if (isEditing) {
        // --- EDIT MODE ---
        RecipeEditDetailScreen(
            originalRecipe = recipe!!,
            onSave = { editedRecipe ->
                // Only leaves edit mode if saving to Notion actually confirms success —
                // before this, the screen closed and assumed success right away, silently
                // losing the edit if the connection dropped in the meantime.
                if (!isSaving) {
                    coroutineScope.launch {
                        val success = viewModel.saveRecipeToNotion(editedRecipe)
                        if (success) {
                            isEditing = false
                        }
                    }
                }
            },
            onCancel = {
                isEditing = false
            }
        )
    } else {
        RecipeDetailContent(
            recipe = recipe!!,
            onBack = {
                viewModel.clearSelectedRecipe()
                onBackClick()
            },
            onFavoriteToggle = {
                // Calls the function we created in the ViewModel
                viewModel.toggleFavorite(recipe!!)
            },
            onEdit = {
                isEditing = true
            },
            onDelete = {
                showDeleteDialog = true
            },
            onStatusChange = { newStatus ->
                viewModel.updateStatus(recipe!!, newStatus)
            },
        )
    }
}

// ==========================================
// 2. The "Painter" (stateless)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailContent(
    recipe: Recipe,
    onFavoriteToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onStatusChange: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isCollapsed = scrollBehavior.state.collapsedFraction > 0.5f
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = recipe.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = if (isCollapsed) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                actions = {
                    // 1. Favorite button (always visible)
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (recipe.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.favoritar),
                            tint = if (recipe.favorite) Color(0xFFE91E63) else Color.Gray
                        )
                    }

                    // 2. Future actions menu (edit/delete)
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.opcoes))
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editar)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.deletar)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Title
            item {
                // Prep time and tags
                Text(
                    text = stringResource(R.string.tempo_formatado, recipe.prepTime),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), // Gives some breathing room if it wraps to the next line
                    modifier = Modifier.fillMaxWidth()
                ) {
                    recipe.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                StatusSelector(
                    currentStatus = recipe.status,
                    onStatusChange = onStatusChange
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Ingredients
            item {
                Text(
                    text = stringResource(R.string.ingredientes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 1. Group the ingredients by section
            val groupedIngredients = recipe.ingredients.groupBy { it.section ?: "" }

            // 2. Draw each group on screen
            groupedIngredients.forEach { (section, list) ->

                // If the group has a name, draw the subtitle
                if (section.isNotEmpty()) {
                    item {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                // Draw the ingredients in this section
                items(list) { ingredient ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = ingredient.item.trim(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }


            // Instructions
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.modo_de_preparo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(recipe.steps) { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Circle with the step number
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            item {
                if (!recipe.link.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.link_da_receita),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            fontSize = 16.sp
                        ),
                        // Centers the text
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recipe.link))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                RecipeTipsSection(tips = recipe.videoTips)
            }
        }
    }
}

// ==========================================
// 3. PREVIEW FOR ANDROID STUDIO
// ==========================================
@Preview(showBackground = true)
@Composable
fun RecipeDetailScreenPreview() {
    val mockRecipe = Recipe(
        title = "Pizza Babalou",
        prepTime = "30 minutos",
        ingredients = listOf(
            Ingredient(
                quantity = "1",
                unit = "disco",
                item = "massa de pizza de fermentação natural"
            ),
            Ingredient(
                quantity = "150",
                unit = "g",
                item = "Mascarpone (substituindo o molho branco tradicional)"
            ),
            Ingredient(
                quantity = "100",
                unit = "g",
                item = "queijo Muçarela ralado"
            ),
            Ingredient(
                quantity = "A gosto",
                unit = "",
                item = "Folhas de manjericão fresco"
            ),
            Ingredient(
                quantity = "1",
                unit = "fio",
                item = "Azeite trufado para finalizar"
            )
        ),
        steps = listOf(
            "Pré-aqueça o forno na temperatura máxima (idealmente acima de 250ºC) com uma pedra de pizza dentro, se tiver.",
            "Abra o disco de massa. Com a ajuda de uma colher, espalhe o mascarpone uniformemente sobre a base, deixando as bordas livres.",
            "Cubra a camada de mascarpone com a muçarela ralada.",
            "Leve ao forno por cerca de 10 a 15 minutos, ou até as bordas estarem douradas e o queijo borbulhando.",
            "Retire do forno, adicione as folhas de manjericão e um fio de azeite trufado. Sirva imediatamente."
        ),
        tags = listOf("Forno", "Jantar", "Fácil", "Vegetariano"),
        favorite = false,
        id = "0",
        status = "Não feito"
    )

    MaterialTheme {
        RecipeDetailContent(
            recipe = mockRecipe,
            onFavoriteToggle = {},
            onEdit = {},
            onDelete = {},
            onBack = {},
            onStatusChange = {}
        )
    }
}
