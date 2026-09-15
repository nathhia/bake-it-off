package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.RecipeTip
import com.bakeitoff.data.model.Recipe
import com.bakeitoff.data.model.Ingredient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bakeitoff.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditDetailScreen(
    originalRecipe: Recipe,
    onSave: (Recipe) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(originalRecipe.title) }

    val editableTags = remember { mutableStateListOf<String>().apply { addAll(originalRecipe.tags) } }
    var newTag by remember { mutableStateOf("") }


    // 1. State for ingredients, steps and tips
    val editableIngredients = remember { mutableStateListOf<Ingredient>().apply { addAll(originalRecipe.ingredients) } }
    val editableSteps = remember { mutableStateListOf<String>().apply { addAll(originalRecipe.steps) } }

    // State for the tips
    val editableTips = remember { mutableStateListOf<RecipeTip>().apply { addAll(originalRecipe.videoTips) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editar_receita_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancelar))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 2. Save every edit onto the final object
                        val updatedRecipe = originalRecipe.copy(
                            title = title,
                            tags = editableTags.toList(),
                            ingredients = editableIngredients.toList(),
                            steps = editableSteps.toList(),
                            videoTips = editableTips.toList()
                        )
                        onSave(updatedRecipe)
                    }) {
                        Text(stringResource(R.string.salvar), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- TITLE ---
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.titulo_da_receita)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // --- TAGS ---
            item {
                Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    editableTags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { editableTags.remove(tag) },
                                    modifier = Modifier.size(16.dp)
                                ) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remover_tag)) }
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text(stringResource(R.string.adicionar_nova_tag)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cleanTag = newTag.trim()
                            if (cleanTag.isNotEmpty() && !editableTags.contains(cleanTag)) {
                                editableTags.add(cleanTag)
                                newTag = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.adicionar_tag)) }
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- INGREDIENTS ---
            item {
                Text(stringResource(R.string.ingredientes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // 1. Group the ingredients by section
            val groupedIngredients = editableIngredients.groupBy { it.section ?: "" }

            // 2. Iterate over each group to draw it on screen
            groupedIngredients.forEach { (section, sectionList) ->

                // Section title (unless it's the general/empty section)
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

                // Cards for the ingredients in this section
                items(sectionList) { currentIngredient ->
                    // Look up the index live to avoid crashes if something gets deleted
                    val index = editableIngredients.indexOfFirst { it === currentIngredient }

                    if (index != -1) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editableIngredients[index].item,
                                    onValueChange = { newValue ->
                                        editableIngredients[index] = editableIngredients[index].copy(item = newValue)
                                    },
                                    label = { Text(stringResource(R.string.ingrediente_label)) },
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(onClick = { editableIngredients.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remover_desc), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Button to add an ingredient EXCLUSIVE to this section
                item {
                    TextButton(
                        onClick = {
                            // Creates an empty ingredient already tied to this section
                            editableIngredients.add(Ingredient(quantity = "", unit = "", item = "", section = section))
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" " + (if (section.isEmpty()) stringResource(R.string.adicionar_ingrediente) else stringResource(R.string.adicionar_ingrediente_em, section)))
                    }
                }
            }

            // 3. What if I want to create a brand new section? (e.g. "Calda")
            item {
                var newSection by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = newSection,
                    onValueChange = { newSection = it },
                    label = { Text(stringResource(R.string.criar_nova_secao)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newSection.isNotBlank()) {
                                // Adds an empty item to force the group to exist
                                editableIngredients.add(Ingredient(quantity = "", unit = "", item = "", section = newSection.trim()))
                                newSection = "" // Clears the field
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.criar_secao_desc))
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            }

            // --- INSTRUCTIONS ---
            item {
                Text(stringResource(R.string.modo_de_preparo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(editableSteps) { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { newValue ->
                            editableSteps[index] = newValue
                        },
                        label = { Text(stringResource(R.string.passo_indexado, index + 1)) },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { editableSteps.removeAt(index) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remover_passo_desc), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                TextButton(onClick = {
                    // Adds an empty step at the end of the list
                    editableSteps.add("")
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" " + stringResource(R.string.adicionar_passo))
                }
            }

            // --- TIPS AND COMMENTS ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.dicas_e_comentarios), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(editableTips) { index, tip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.dica_indexada, index + 1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                            IconButton(onClick = { editableTips.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remover_dica_desc), tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = tip.text,
                            onValueChange = { newText ->
                                editableTips[index] = tip.copy(text = newText)
                            },
                            label = { Text(stringResource(R.string.texto_da_dica)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Selects the tip type using the "source" field
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val types = listOf("Vídeo", "IA", "Pessoal")

                            types.forEach { type ->
                                InputChip(
                                    // Marks it selected if the source matches the type.
                                    // (Fallback: if an old database has "Comunidade", map it to "Pessoal")
                                    selected = (tip.source == type || (type == "Pessoal" && tip.source == "Comunidade")),
                                    onClick = {
                                        editableTips[index] = tip.copy(
                                            source = type,
                                            enriched = (type == "IA") // Updates the boolean automatically!
                                        )
                                    },
                                    label = { Text(type) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                TextButton(onClick = {
                    // Adds a new empty tip, defaulted to "Pessoal"
                    editableTips.add(
                        RecipeTip(
                            text = "",
                            source = "Pessoal",
                            enriched = false
                        )
                    )
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" " + stringResource(R.string.adicionar_observacao))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeEditDetailScreenPreview() {
    val mockRecipe = Recipe(
        id = "preview-id",
        title = "Pizza Babalou",
        prepTime = "30 minutos",
        tags = listOf("Forno", "Jantar", "Vegetariano"),
        ingredients = listOf(
            Ingredient(quantity = "1", unit = "disco", item = "massa de pizza", section = "Massa"),
            Ingredient(quantity = "150", unit = "g", item = "Mascarpone", section = "Cobertura")
        ),
        steps = listOf(
            "Abra a massa numa forma untada.",
            "Espalhe o mascarpone e finalize com o queijo ralado."
        ),
        videoTips = listOf(
            RecipeTip(text = "Fica melhor com forno bem quente.", source = "Pessoal", enriched = false)
        )
    )

    MaterialTheme {
        RecipeEditDetailScreen(
            originalRecipe = mockRecipe,
            onSave = {},
            onCancel = {}
        )
    }
}
