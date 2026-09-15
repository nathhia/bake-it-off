package com.bakeitoff.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakeitoff.R
import com.bakeitoff.data.model.RecipeTip

/**
 * Used both in the preview of a just-extracted recipe (source will only ever be
 * "IA" or "Video") and on the detail screen of a saved recipe, where the user
 * may have already added their own notes ("Pessoal").
 */
@Composable
fun RecipeTipsSection(tips: List<RecipeTip>) {
    if (tips.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.dicas_e_comentarios),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        tips.forEach { tip ->
            // Fallback: older Notion databases saved "Comunidade" before that option
            // became "Pessoal" on the edit screen — we treat both as the same source.
            val (backgroundColor, label) = when (tip.source) {
                "IA" -> Pair(Color(0xFFF3E5F5), stringResource(R.string.dica_extra_ia))
                "Pessoal", "Comunidade" -> Pair(Color(0xFFE3F2FD), stringResource(R.string.minha_observacao))
                else -> Pair(CardDefaults.cardColors().containerColor, stringResource(R.string.dica_do_video))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = tip.text,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
