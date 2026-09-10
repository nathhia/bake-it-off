package com.bakeitoff.ui.screens

import com.bakeitoff.DicasComentario
import com.bakeitoff.Receita
import com.bakeitoff.Ingrediente
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditDetailScreen(
    receitaOriginal: Receita,
    onSave: (Receita) -> Unit,
    onCancel: () -> Unit
) {
    var titulo by remember { mutableStateOf(receitaOriginal.titulo) }

    val tagsEditaveis = remember { mutableStateListOf<String>().apply { addAll(receitaOriginal.tags) } }
    var novaTag by remember { mutableStateOf("") }


    // 1. Estados para os Ingredientes, Passos e Dicas
    val ingredientesEditaveis = remember { mutableStateListOf<Ingrediente>().apply { addAll(receitaOriginal.ingredientes) } }
    val passosEditaveis = remember { mutableStateListOf<String>().apply { addAll(receitaOriginal.passos) } }

    // NOVO: Estado para as dicas
    val dicasEditaveis = remember { mutableStateListOf<DicasComentario>().apply { addAll(receitaOriginal.dicas_video) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Receita") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 2. Salva todas as edições no objeto final
                        val receitaAtualizada = receitaOriginal.copy(
                            titulo = titulo,
                            tags = tagsEditaveis.toList(),
                            ingredientes = ingredientesEditaveis.toList(),
                            passos = passosEditaveis.toList(),
                            dicas_video = dicasEditaveis.toList()
                        )
                        onSave(receitaAtualizada)
                    }) {
                        Text("Salvar", fontWeight = FontWeight.Bold)
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
            // --- TÍTULO ---
            item {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título da Receita") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // --- TAGS ---
            item {
                Text("Tags", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagsEditaveis.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { tagsEditaveis.remove(tag) },
                                    modifier = Modifier.size(16.dp)
                                ) { Icon(Icons.Default.Close, contentDescription = "Remover Tag") }
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = novaTag,
                    onValueChange = { novaTag = it },
                    label = { Text("Adicionar nova tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val tagLimpa = novaTag.trim()
                            if (tagLimpa.isNotEmpty() && !tagsEditaveis.contains(tagLimpa)) {
                                tagsEditaveis.add(tagLimpa)
                                novaTag = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = "Adicionar Tag") }
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- INGREDIENTES ---
            item {
                Text("Ingredientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // 1. Agrupamos os ingredientes pela seção
            val ingredientesAgrupados = ingredientesEditaveis.groupBy { it.secao ?: "" }

            // 2. Iteramos sobre cada grupo para desenhar na tela
            ingredientesAgrupados.forEach { (secao, listaDaSecao) ->

                // Título da Seção (se não for a seção geral/vazia)
                if (secao.isNotEmpty()) {
                    item {
                        Text(
                            text = secao,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                // Caixinhas dos ingredientes desta seção
                items(listaDaSecao) { ingredienteAtual ->
                    // Buscamos o índice em tempo real para evitar crashes se você deletar algo
                    val index = ingredientesEditaveis.indexOfFirst { it === ingredienteAtual }

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
                                    value = ingredientesEditaveis[index].item,
                                    onValueChange = { novoValor ->
                                        ingredientesEditaveis[index] = ingredientesEditaveis[index].copy(item = novoValor)
                                    },
                                    label = { Text("Ingrediente") },
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(onClick = { ingredientesEditaveis.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Botão para adicionar ingrediente EXCLUSIVO para esta seção
                item {
                    TextButton(
                        onClick = {
                            // Cria um ingrediente vazio já atrelado a esta seção
                            ingredientesEditaveis.add(Ingrediente(quantidade = "", unidade = "", item = "", secao = secao))
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(if (secao.isEmpty()) " Adicionar Ingrediente" else " Adicionar em $secao")
                    }
                }
            }

            // 3. E se eu quiser criar uma seção nova do zero? (ex: "Calda")
            item {
                var novaSecao by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = novaSecao,
                    onValueChange = { novaSecao = it },
                    label = { Text("Criar Nova Seção (ex: Cobertura)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (novaSecao.isNotBlank()) {
                                // Adiciona um item vazio para forçar o grupo a existir
                                ingredientesEditaveis.add(Ingrediente(quantidade = "", unidade = "", item = "", secao = novaSecao.trim()))
                                novaSecao = "" // Limpa o campo
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Criar Seção")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            }

            // --- MODO DE PREPARO ---
            item {
                Text("Modo de Preparo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(passosEditaveis) { index, passo ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = passo,
                        onValueChange = { novoValor ->
                            passosEditaveis[index] = novoValor
                        },
                        label = { Text("Passo ${index + 1}") },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { passosEditaveis.removeAt(index) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remover Passo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                TextButton(onClick = {
                    // Adiciona um passo vazio no final da lista
                    passosEditaveis.add("")
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Adicionar Passo")
                }
            }

            // --- DICAS E COMENTÁRIOS ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Dicas e Comentários", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(dicasEditaveis) { index, dica ->
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
                            Text("Dica ${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                            IconButton(onClick = { dicasEditaveis.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover Dica", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = dica.texto,
                            onValueChange = { novoTexto ->
                                dicasEditaveis[index] = dica.copy(texto = novoTexto)
                            },
                            label = { Text("Texto da Dica/Observação") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Seleção do tipo de Dica usando o campo "fonte"
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val tipos = listOf("Vídeo", "IA", "Pessoal")

                            tipos.forEach { tipo ->
                                InputChip(
                                    // Marca como selecionado se a fonte for igual ao tipo.
                                    // (Fallback: se no banco antigo estiver "Comunidade", mapeia para "Pessoal")
                                    selected = (dica.fonte == tipo || (tipo == "Pessoal" && dica.fonte == "Comunidade")),
                                    onClick = {
                                        dicasEditaveis[index] = dica.copy(
                                            fonte = tipo,
                                            enriquecida = (tipo == "IA") // Atualiza o booleano automaticamente!
                                        )
                                    },
                                    label = { Text(tipo) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                TextButton(onClick = {
                    // Adiciona uma nova dica vazia padronizada como "Pessoal"
                    dicasEditaveis.add(
                        DicasComentario(
                            texto = "",
                            fonte = "Pessoal",
                            enriquecida = false
                        )
                    )
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Adicionar Observação")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}