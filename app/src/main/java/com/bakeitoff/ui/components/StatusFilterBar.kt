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

@OptIn(ExperimentalMaterial3Api::class) // In case your project still requires this for FilterChip
@Composable
fun StatusFilterBar(
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
    options: List<String>, // This list comes from the ViewModel
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

        // Only render the dynamic list of statuses
        items(options) { option ->
            // Extra guard in case "Todos" comes back from Notion by mistake
            if (option != "Todos") {
                val isSelected = (selectedStatus == option)

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        // If the clicked chip is already the selected one, send null to clear the filter.
                        // Otherwise, select the new option normally.
                        if (isSelected) {
                            onStatusSelect(null)
                        } else {
                            onStatusSelect(option)
                        }
                    },
                    label = { Text(option) }
                )
            }
        }
    }
}
