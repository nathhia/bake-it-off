package com.bakeitoff.ui.screens

import com.bakeitoff.data.model.Ingrediente
import com.bakeitoff.data.model.Receita
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakeitoff.R
import com.bakeitoff.ui.components.DicasComentarioSection
import com.bakeitoff.ui.components.StatusSelector
import com.bakeitoff.viewmodel.RecipeViewModel
import kotlinx.coroutines.launch

// ==========================================
// 1. O "Gerente" (Stateful)
// ==========================================
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeViewModel,
    onBackClick: () -> Unit
) {
    val receita by viewModel.receitaSelecionada.collectAsState()
    val isSaving by viewModel.isSavingToNotion
    val coroutineScope = rememberCoroutineScope()

    // Roda só quando a tela aparece (não a cada mudança de receita, senão duplicaria
    // o onBackClick() já disparado pelo BackHandler/exclusão ao zerar a seleção).
    // Se a receita já chegar nula aqui (ex: restauração de processo), volta para a tela
    // anterior em vez de deixar a tela em branco sem saída.
    LaunchedEffect(Unit) {
        if (receita == null) {
            onBackClick()
        }
    }

    var isEditing by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    if (receita == null) {
        return
    }

    BackHandler {
        if (isEditing) {
            isEditing = false // Apenas cancela a edição
        } else {
            viewModel.limparReceitaSelecionada()
            onBackClick() // Volta para a lista
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.deletar_receita_titulo)) },
            text = { Text(stringResource(R.string.deletar_receita_mensagem, receita?.titulo ?: "")) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        val receitaParaDeletar = receita!!
                        coroutineScope.launch {
                            isDeleting = true
                            // Só sai da tela se a exclusão realmente deu certo — antes disso,
                            // o app saía imediatamente e, se a exclusão falhasse, a tela de
                            // detalhes de uma receita já apagada da memória ficava em branco.
                            val sucesso = viewModel.deletarReceita(receitaParaDeletar)
                            isDeleting = false
                            showDeleteDialog = false
                            if (sucesso) {
                                onBackClick() // Volta para a lista principal
                            }
                        }
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.deletar), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isDeleting, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }

    if (isEditing) {
        // --- MODO EDIÇÃO ---
        RecipeEditDetailScreen(
            receitaOriginal = receita!!,
            onSave = { receitaEditada ->
                // Só sai do modo de edição se o salvamento no Notion realmente confirmar
                // sucesso — antes disso, a tela fechava e assumia sucesso na hora, perdendo
                // a edição silenciosamente se a conexão caísse nesse meio-tempo.
                if (!isSaving) {
                    coroutineScope.launch {
                        val sucesso = viewModel.salvarReceitaNoNotion(receitaEditada)
                        if (sucesso) {
                            isEditing = false
                        }
                    }
                }
            },
            onCancel = {
                isEditing = false
            }
        )
    } else {
        RecipeDetailContent(
            receita = receita!!,
            onBack = { // Corrigido de onBackClick para onBack
                viewModel.limparReceitaSelecionada()
                onBackClick()
            },
            onFavoriteToggle = {
                // Chama a função que criamos no ViewModel
                viewModel.toggleFavorito(receita!!)
            },
            onEdit = {
                isEditing = true
            },
            onDelete = {
                showDeleteDialog = true
            },
            onStatusChange = { novoStatus ->
                viewModel.atualizarStatus(receita!!, novoStatus)
            },
        )
    }
}

// ==========================================
// 2. O "Pintor" (Stateless)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailContent(
    receita: Receita,
    onFavoriteToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onStatusChange: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isCollapsed = scrollBehavior.state.collapsedFraction > 0.5f
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = receita.titulo,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = if (isCollapsed) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                actions = {
                    // 1. Botão de Favoritar (sempre visível)
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (receita.favorito) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.favoritar),
                            tint = if (receita.favorito) Color(0xFFE91E63) else Color.Gray
                        )
                    }

                    // 2. Menu de Ações Futuras (Editar/Deletar)
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.opcoes))
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editar)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.deletar)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Título
            item {
                // Tempo de preparo e Tags
                Text(
                    text = stringResource(R.string.tempo_formatado, receita.tempoPreparo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), // Dá um respiro se for pra linha de baixo
                    modifier = Modifier.fillMaxWidth()
                ) {
                    receita.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                StatusSelector(
                    statusAtual = receita.status,
                    onStatusChange = onStatusChange
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Ingredientes
            item {
                Text(
                    text = stringResource(R.string.ingredientes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 1. Agrupa os ingredientes pela seção
            val ingredientesAgrupados = receita.ingredientes.groupBy { it.secao ?: "" }

            // 2. Desenha cada grupo na tela
            ingredientesAgrupados.forEach { (secao, lista) ->

                // Se o grupo tem um nome, desenha o subtítulo
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

                // Desenha os ingredientes desta seção
                items(lista) { ingrediente ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = ingrediente.item.trim(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }


            // Modo de Preparo
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.modo_de_preparo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(receita.passos) { index, passo ->
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Círculo com o número do passo
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = passo,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            item {
                if (!receita.link.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.link_da_receita),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            fontSize = 16.sp
                        ),
                        // Centralizando o texto
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(receita.link))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                DicasComentarioSection(dicas = receita.dicas_video)
            }
        }
    }
}

// ==========================================
// 3. PREVIEW PARA O ANDROID STUDIO
// ==========================================
@Preview(showBackground = true)
@Composable
fun RecipeDetailScreenPreview() {
    val mockReceita = Receita(
        titulo = "Pizza Babalou",
        tempoPreparo = "30 minutos",
        // dica = "Espalhe bem o mascarpone antes de colocar os outros queijos. A base cremosa faz toda a diferença nesta receita!",
        ingredientes = listOf(
            Ingrediente(
                quantidade = "1",
                unidade = "disco",
                item = "massa de pizza de fermentação natural"
            ),
            Ingrediente(
                quantidade = "150",
                unidade = "g",
                item = "Mascarpone (substituindo o molho branco tradicional)"
            ),
            Ingrediente(
                quantidade = "100",
                unidade = "g",
                item = "queijo Muçarela ralado"
            ),
            Ingrediente(
                quantidade = "A gosto",
                unidade = "",
                item = "Folhas de manjericão fresco"
            ),
            Ingrediente(
                quantidade = "1",
                unidade = "fio",
                item = "Azeite trufado para finalizar"
            )
        ),
        passos = listOf(
            "Pré-aqueça o forno na temperatura máxima (idealmente acima de 250ºC) com uma pedra de pizza dentro, se tiver.",
            "Abra o disco de massa. Com a ajuda de uma colher, espalhe o mascarpone uniformemente sobre a base, deixando as bordas livres.",
            "Cubra a camada de mascarpone com a muçarela ralada.",
            "Leve ao forno por cerca de 10 a 15 minutos, ou até as bordas estarem douradas e o queijo borbulhando.",
            "Retire do forno, adicione as folhas de manjericão e um fio de azeite trufado. Sirva imediatamente."
        ),
        tags = listOf("Forno", "Jantar", "Fácil", "Vegetariano"),
        favorito = false,
        id = "0",
        status = "Não feito"
    )

    MaterialTheme {
        RecipeDetailContent(
            receita = mockReceita,
            onFavoriteToggle = {},
            onEdit = {},
            onDelete = {},
            onBack = {},
            onStatusChange = {}
        )
    }
}