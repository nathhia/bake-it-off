package com.bakeitoff

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bakeitoff.ui.screens.RecipeDetailScreen
import com.bakeitoff.ui.screens.RecipeListScreen
import com.bakeitoff.ui.screens.RecipeScreen

@Composable
fun BakeItOffApp(viewModel: RecipeViewModel) {
    // Esse é o "motor" que controla as viagens entre as telas
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        // Rota 1: Tela Inicial (Extração)
        composable("home") {
            // Supondo que a sua tela inicial se chame RecipeScreen ou InitialScreen
            RecipeScreen(
                viewModel = viewModel,
                // Passamos uma função que diz ao botão como ir para a lista
                onNavigateToList = {
                    navController.navigate("lista_receitas")
                }
            )
        }

        // Rota 2: Tela do Caderno do Notion
        composable("lista_receitas") {
            RecipeListScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onRecipeClick = { receitaSelecionada ->
                    // 1. Avisamos ao ViewModel qual receita foi clicada
                    viewModel.selecionarReceita(receitaSelecionada)
                    // 2. Navegamos para a tela de detalhes
                    navController.navigate("detalhes_receita")
                }
            )
        }

        // Rota 3: A Nova Tela de Detalhes!
        composable("detalhes_receita") {
            RecipeDetailScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}