package com.bakeitoff.data.notion

import com.bakeitoff.data.model.RecipeTip
import com.bakeitoff.data.model.Ingredient
import com.bakeitoff.data.model.Recipe

/**
 * Converts a Notion page (NotionPageResponse) into a Recipe.
 * Extracted from NotionRepository.fetchRecipes so it can be tested without
 * a real network call.
 */
object NotionRecipeMapper {

    fun toRecipe(page: NotionPageResponse): Recipe {
        val props = page.properties

        val titleStr = props.name?.title?.firstOrNull()?.text?.content ?: "Sem Título"
        val prepTimeStr = props.prepTime?.rich_text?.firstOrNull()?.text?.content ?: "--"
        val tagsList = props.tags?.multi_select?.map { it.name } ?: emptyList()
        val tipsStr = props.tips?.rich_text?.joinToString("") { it.text.content } ?: ""
        val link = props.link?.url

        val ingredientsStr = props.ingredients?.rich_text?.joinToString("") { it.text.content } ?: ""
        val stepsStr = props.instructions?.rich_text?.joinToString("") { it.text.content } ?: ""

        return Recipe(
            id = page.id,
            title = titleStr,
            prepTime = prepTimeStr,
            tags = tagsList,
            ingredients = parseIngredients(ingredientsStr),
            steps = parseSteps(stepsStr),
            favorite = props.favorite?.checkbox ?: false,
            status = props.status?.status?.name ?: "Não feito",
            link = link,
            videoTips = parseTips(tipsStr)
        )
    }

    /**
     * The text comes as "• item" lines with optional "**Section:**" headers
     * (format written by NotionRepository.saveRecipe/sliceForNotion).
     */
    fun parseIngredients(ingredientsStr: String): List<Ingredient> {
        var currentSection = ""
        val ingredientList = mutableListOf<Ingredient>()

        // Using a Sequence for memory-efficient processing
        ingredientsStr.split("\n")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cleanText = line.removePrefix("•").removePrefix("-").trim()

                if (cleanText.startsWith("**") && cleanText.endsWith("**")) {
                    currentSection = cleanText.replace("**", "").replace(":", "").trim()
                } else {
                    ingredientList.add(
                        Ingredient(
                            quantity = "",
                            unit = "",
                            item = cleanText,
                            section = currentSection
                        )
                    )
                }
            }

        return ingredientList
    }

    /** The text comes as "1. step", "2. step" etc. lines. */
    fun parseSteps(stepsStr: String): List<String> =
        stepsStr
            .split("\n")
            .asSequence() // Using a Sequence here too
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^[0-9]+[.)-]\\s*"), "").trim()
            }
            .toList() // Convert back to a list at the end of the pipeline

    /** The text comes as "[IA] tip", "[Vídeo] tip" or "[Pessoal] tip" lines. */
    fun parseTips(tipsStr: String): List<RecipeTip> =
        tipsStr
            .split("\n")
            .asSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val cleanText = line.removePrefix("[IA] ").removePrefix("[Vídeo] ").removePrefix("[Pessoal] ").trim()
                val source = when {
                    line.startsWith("[IA]") -> "IA"
                    line.startsWith("[Pessoal]") -> "Pessoal"
                    else -> "Vídeo"
                }

                RecipeTip(text = cleanText, source = source, enriched = (source == "IA"))
            }
            .toList()
}
