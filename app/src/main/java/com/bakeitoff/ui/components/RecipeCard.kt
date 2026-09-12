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
import com.bakeitoff.data.model.Receita
import com.bakeitoff.ui.theme.CardBackground
import com.bakeitoff.ui.theme.LightPink

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeCard(
    receita: Receita,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit, // Callback genérico
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
                                containerColor = LightPink,
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
                                label = { Text(stringResource(R.string.mais_tags, receita.tags.size - 3)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFFF8F0F0)
                                ),
                                border = null
                            )
                        } else {
                            AssistChip(
                                onClick = { tagsExpanded = false }, // Ação para recolher
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
            if (receita.favorito) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.favorito_desc),
                    tint = Color(0xFFE91E63), // Um rosa mais vivo para destacar
                    modifier = Modifier
                        .align(Alignment.TopEnd) // O "pulo do gato" para alinhar
                        .padding(16.dp)
                )
            }
        }
    }
}
