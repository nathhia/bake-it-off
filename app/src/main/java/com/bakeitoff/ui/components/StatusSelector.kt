package com.bakeitoff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bakeitoff.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSelector(statusAtual: String, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val opcoes = listOf("Feito", "Não feito", "Quero fazer")

    Box(modifier = Modifier.fillMaxWidth()) {
        // Usamos um botão como gatilho. Ele é mais fino que o TextField.
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = statusAtual, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.mudar_status))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f) // Menu alinhado com o botão
        ) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao) },
                    onClick = {
                        onStatusChange(opcao)
                        expanded = false
                    }
                )
            }
        }
    }
}
