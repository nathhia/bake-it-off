package com.bakeitoff

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bakeitoff.ui.screens.RecipeDetailScreen
import com.bakeitoff.ui.screens.RecipeListScreen
import com.bakeitoff.ui.screens.RecipeScreen
import com.bakeitoff.viewmodel.RecipeViewModel

@Composable
fun BakeItOffApp(viewModel: RecipeViewModel) {
    // This is the "engine" that controls navigation between screens
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        // Route 1: Home screen (extraction)
        composable("home") {
            // Assuming the home screen is called RecipeScreen or InitialScreen
            RecipeScreen(
                viewModel = viewModel,
                // Pass a function that tells the button how to go to the list
                onNavigateToList = {
                    navController.navigate("recipe_list") {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Route 2: Notion notebook screen
        composable("recipe_list") {
            RecipeListScreen(
                viewModel = viewModel,
                onRecipeClick = { clickedRecipe ->
                    // 1. Tell the ViewModel which recipe was clicked
                    viewModel.selectRecipe(clickedRecipe)
                    // 2. Navigate to the detail screen
                    navController.navigate("recipe_details")
                }
            )
        }

        // Route 3: The new detail screen!
        composable("recipe_details") {
            RecipeDetailScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
