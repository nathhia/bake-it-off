package com.bakeitoff.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakeitoff.R
import com.bakeitoff.data.model.Recipe
import com.bakeitoff.ui.theme.CardBackground
import com.bakeitoff.ui.theme.LightPink

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit, // Generic callback
    selectedTags: Set<String>
) {
    var tagsExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp).padding(end = 32.dp)
            ) {
                // Title
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Prep time
                Text(
                    text = "⏱️ ${recipe.prepTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tags (showing up to 3 tags to keep the card uncluttered)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    val tagsToShow = if (tagsExpanded) recipe.tags else recipe.tags.take(3)

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
                                containerColor = LightPink,
                                labelColor = Color(0xFF6D4C41)
                            ),
                            border = if (isSelected) BorderStroke(1.dp, Color.White) else null
                        )
                    }

                    // "+X" chip
                    if (recipe.tags.size > 3) {
                        if (!tagsExpanded) {
                            AssistChip(
                                onClick = { tagsExpanded = true }, // Action to expand
                                label = { Text(stringResource(R.string.mais_tags, recipe.tags.size - 3)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFF8F0F0)
                                ),
                                border = null
                            )
                        } else {
                            AssistChip(
                                onClick = { tagsExpanded = false }, // Action to collapse
                                label = { Text(stringResource(R.string.menos)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFF8F0F0)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
            if (recipe.favorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.favorito_desc),
                    tint = Color(0xFFE91E63), // A brighter pink to stand out
                    modifier = Modifier
                        .align(Alignment.TopEnd) // The trick to align it
                        .padding(16.dp)
                )
            }
        }
    }
}
