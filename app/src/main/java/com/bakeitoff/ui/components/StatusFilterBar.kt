package com.bakeitoff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bakeitoff.R
import com.bakeitoff.ui.theme.LightPink

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
                        contentDescription = stringResource(R.string.favoritos_desc),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LightPink,
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
