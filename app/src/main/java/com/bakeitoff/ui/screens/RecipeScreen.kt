package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.Recipe
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakeitoff.R
import com.bakeitoff.ui.components.ErrorScreen
import com.bakeitoff.ui.components.HeaderSection
import com.bakeitoff.ui.components.LoadingScreen
import com.bakeitoff.ui.components.MediaSelectorSection
import com.bakeitoff.ui.components.RecipeTipsSection
import com.bakeitoff.viewmodel.RecipeUiState
import com.bakeitoff.viewmodel.RecipeViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(viewModel: RecipeViewModel, onNavigateToList: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var linkText by rememberSaveable { mutableStateOf("") }
    var extraInstruction by rememberSaveable { mutableStateOf("") }
    var savedUrisStrings by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is RecipeUiState.Success) {
            linkText = ""
            extraInstruction = ""
            savedUrisStrings = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_title_home)) })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is RecipeUiState.Uploading -> {
                    // Without this, there was no way to back out while the media was uploading.
                    BackHandler { viewModel.cancelProcessing() }

                    // Only observe the compression states while we're in the Uploading state
                    val isCompressing by viewModel.isCompressing.collectAsStateWithLifecycle()
                    val progress by viewModel.compressionProgress.collectAsStateWithLifecycle()

                    if (isCompressing) {
                        // Video compression progress screen
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Divide by 100f because Compose expects a value between 0.0 and 1.0
                            CircularProgressIndicator(progress = { progress / 100f })
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.otimizando_video, progress.toInt()))
                            Text(
                                stringResource(R.string.economiza_tokens),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // When not compressing (or already done), show the regular upload message
                        LoadingScreen(stringResource(R.string.enviando_midias))
                    }
                }
                is RecipeUiState.Extracting -> {
                    BackHandler { viewModel.cancelProcessing() }
                    LoadingScreen(stringResource(R.string.ia_extraindo_receita))
                }
                is RecipeUiState.Error -> {
                    ErrorScreen(state.message, onDismiss = { viewModel.resetToInitial() })
                }
                is RecipeUiState.Success -> {
                    ExtractedRecipePreview(state.recipe, viewModel)
                }
                else -> InitialScreen(
                    linkText = linkText,
                    onLinkChange = { linkText = it },
                    extraInstruction = extraInstruction,
                    onInstructionChange = { extraInstruction = it },
                    savedUrisStrings = savedUrisStrings,
                    onUrisChange = { savedUrisStrings = it },
                    onMediaSelected = { uris, link, extraPrompt ->
                        viewModel.processMediaUris(uris, link, extraPrompt)
                    },
                    onTextOnlySubmit = { text ->
                        viewModel.createRecipeFromText(text)
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
fun ExtractedRecipePreview(recipe: Recipe, viewModel: RecipeViewModel) {
    val isSaving by viewModel.isSavingToNotion
    val coroutineScope = rememberCoroutineScope()

    var editableTags = remember { mutableStateListOf<String>().apply { addAll(recipe.tags) } }
    var newTag by remember { mutableStateOf("") }

    BackHandler {
        viewModel.resetToInitial() // Back to the initial state (InitialScreen)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Title and time
        item {
            Text(text = recipe.title, style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text(text = recipe.prepTime, color = Color.Gray)
            }
        }

        // 2. Tags (chips)
        item {
            Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleLarge)

            // Renders the current tags with an "X" button to remove them
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                editableTags.forEach { tag ->
                    key(tag) {
                        InputChip(
                            selected = true,
                            onClick = { }, // Does nothing when the body is clicked
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        // Removes the tag from the list
                                        editableTags.remove(tag)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remover_tag))
                                }
                            }
                        )
                    }
                }
            }

            // Text field to add a new tag
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text(stringResource(R.string.adicionar_nova_tag)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val cleanTag = newTag.trim()
                        // Only add it if it's not empty and the tag doesn't already exist
                        if (cleanTag.isNotEmpty() && !editableTags.contains(cleanTag)) {
                            editableTags.add(cleanTag)
                            newTag = "" // Clears the field after adding
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.adicionar_tag))
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Ingredients
        item {
            Text(
                text = stringResource(R.string.ingredientes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp) // A little breathing room between the title and the card
            )

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    // 1. Group the ingredients by section
                    val groupedIngredients = recipe.ingredients.groupBy { it.section }

                    groupedIngredients.forEach { (section, list) ->
                        if (!section.isNullOrEmpty()) {
                            Text(
                                text = section,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, // Highlights it with the app's base color
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        // 3. Draw the items
                        list.forEach { ing ->
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 4.dp) // First apply the vertical spacing
                                    .padding(start = if (section.isNullOrEmpty()) 0.dp else 8.dp) // Then apply the side indent
                            ) {
                                Text("• ", fontWeight = FontWeight.Bold)

                                val ingredientText = if (ing.quantity.isNullOrBlank() && ing.unit.isNullOrBlank()) {
                                    ing.item
                                } else {
                                    // Deliberately cast to nullable: Gson ignores Kotlin's non-null type
                                    // and can leave this null when the AI doesn't specify quantity/unit.
                                    val q = (ing.quantity as String?)?.trim() ?: ""
                                    val u = (ing.unit as String?)?.trim() ?: ""
                                    val connector = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "
                                    "$q $u$connector${ing.item}".replace(Regex("\\s+"), " ").trim()
                                }

                                Text(ingredientText)
                            }
                        }
                    }
                }
            }
        }

        // 4. Instructions
        item {
            Text(stringResource(R.string.passo_a_passo), style = MaterialTheme.typography.titleLarge)
        }

        itemsIndexed(recipe.steps) { index, step ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${index + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(step)
            }
        }

        item {
            RecipeTipsSection(tips = recipe.videoTips)
        }

        // 5. Save to Notion button
        item {
            Button(
                onClick = {
                    // Deliberately cast to nullable: the AI never includes "Status" in the
                    // extracted JSON, and Gson ignores Kotlin's default value, leaving status null here.
                    val currentStatus = (recipe.status as String?) ?: "Não feito"
                    val updatedRecipe = recipe.copy(tags = editableTags, status = currentStatus)
                    coroutineScope.launch {
                        viewModel.saveRecipeToNotion(updatedRecipe)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp),
                enabled = !isSaving // Disables the button while saving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.salvar_no_notion),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InitialScreen(
    linkText: String,
    onLinkChange: (String) -> Unit,
    extraInstruction: String,
    onInstructionChange: (String) -> Unit,
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

    val isMediaAttached = selectedUris.isNotEmpty() || linkText.isNotBlank()

    // Use a LazyColumn to get correct usability for fluid lists/forms
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {

        // 1. Visual header (componentized)
        item {
            HeaderSection(onLogoLongPress = onLogoLongPress)
        }

        // 2. Media attachment section (componentized)
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

        // 3. Optional link field
        item {
            OutlinedTextField(
                value = linkText,
                onValueChange = { onLinkChange(it) },
                label = { Text(stringResource(R.string.link_tiktok_reels)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Dynamic text input field
        item {
            // UI smarts: the copy changes based on whether media is attached
            val dynamicLabel = if (isMediaAttached) stringResource(R.string.adaptacoes_receita) else stringResource(R.string.o_que_quer_comer)
            val dynamicPlaceholder = if (isMediaAttached) stringResource(R.string.exemplo_adaptacao) else stringResource(R.string.exemplo_receita_texto)

            OutlinedTextField(
                value = extraInstruction,
                onValueChange = { onInstructionChange(it) },
                label = { Text(dynamicLabel) },
                placeholder = { Text(dynamicPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )
        }

        // 5. Unified submit button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true // Locks the button immediately
                        if (isMediaAttached) {
                            onMediaSelected(selectedUris, linkText, extraInstruction)
                        } else if (extraInstruction.isNotBlank()) {
                            onTextOnlySubmit(extraInstruction)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                // The button is disabled if there's no text OR it's already processing
                enabled = !isSubmitting && (isMediaAttached || extraInstruction.isNotBlank())
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        text = if (isMediaAttached) stringResource(R.string.extrair_da_midia) else stringResource(R.string.criar_receita_com_ia),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. Navigation button to the notebook
        item {
            OutlinedButton(
                onClick = onNavigateToList,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(stringResource(R.string.ver_meu_caderno))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InitialScreenPreview() {
    MaterialTheme {
        InitialScreen(
            linkText = "",
            onLinkChange = {},
            extraInstruction = "",
            onInstructionChange = {},
            savedUrisStrings = emptyList(),
            onUrisChange = {},
            onMediaSelected = { _, _, _ -> },
            onTextOnlySubmit = {},
            onNavigateToList = {},
            onLogoLongPress = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingScreenPreview() {
    MaterialTheme {
        LoadingScreen("IA extraindo receita...")
    }
}
