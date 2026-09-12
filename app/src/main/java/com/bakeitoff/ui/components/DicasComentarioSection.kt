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
import com.bakeitoff.data.model.DicasComentario

/**
 * Usado tanto na pré-visualização de uma receita recém-extraída (fonte só será
 * "IA" ou "Video") quanto na tela de detalhes de uma receita salva, onde o
 * usuário já pode ter adicionado observações próprias ("Pessoal").
 */
@Composable
fun DicasComentarioSection(dicas: List<DicasComentario>) {
    if (dicas.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.dicas_e_comentarios),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        dicas.forEach { dica ->
            // Fallback: bancos antigos do Notion salvaram "Comunidade" antes de essa opção
            // virar "Pessoal" na tela de edição — tratamos os dois como a mesma origem.
            val (corFundo, rotulo) = when (dica.fonte) {
                "IA" -> Pair(Color(0xFFF3E5F5), stringResource(R.string.dica_extra_ia))
                "Pessoal", "Comunidade" -> Pair(Color(0xFFE3F2FD), stringResource(R.string.minha_observacao))
                else -> Pair(CardDefaults.cardColors().containerColor, stringResource(R.string.dica_do_video))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = corFundo)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = dica.texto,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = rotulo,
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
