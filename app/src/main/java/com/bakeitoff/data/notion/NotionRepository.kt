package com.bakeitoff.data.notion

import android.util.Log
import com.bakeitoff.data.model.RecipeTip
import com.bakeitoff.data.model.Ingredient
import com.bakeitoff.data.model.Recipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class NotionRepository(private val integrationToken: String, private val databaseId: String) {

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Time to connect
        // 60s read timeout: with many recipes (paginated 100 at a time, each with
        // long ingredients/steps/tips text), a single page can legitimately take
        // longer than 30s on a slower connection — the timeout firing mid-pagination
        // used to silently cut off the list before any error was shown.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)   // Time to send the data
        .build()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.notion.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
        .create(NotionApiService::class.java)

    private fun sliceForNotion(text: String): List<TextObject> {
        if (text.isEmpty()) return listOf(TextObject(TextContent("")))

        // Chunk at 1999 to stay under the API's 2000-char limit
        return text.chunked(1999).map { chunk ->
            TextObject(TextContent(chunk))
        }
    }

    // Takes the Recipe class as-is, already nicely shaped by Gemini.
    // Returns the id of the Notion page (new or existing) on success, or
    // null on failure — the caller uses that id to update the local list right
    // away, without depending on a new fetch (Notion's query API can take a few
    // seconds to "see" a page that was just created).
    suspend fun saveRecipe(recipe: Recipe, originLink: String?): String? {
        try {

            // ==========================================
            // PART 1: Preparing text for the columns (properties)
            // ==========================================
            val ingredientsString = recipe.ingredients
                .groupBy { it.section }
                .entries.joinToString("\n\n") { (section, ingredientList) ->
                    val sectionTitle = if (!section.isNullOrEmpty()) "**$section:**\n" else ""
                    val items = ingredientList.joinToString("\n") { ing ->

                        // Rebuilds the phrase if the AI split it, or uses it as-is if it came from Notion
                        val ingredientText = if (ing.quantity.isNullOrBlank() && ing.unit.isNullOrBlank()) {
                            ing.item
                        } else {
                            // Deliberately cast to nullable: Gson ignores Kotlin's non-null type
                            // and can leave this null when the AI doesn't specify quantity/unit.
                            val q = (ing.quantity as String?)?.trim() ?: ""
                            val u = (ing.unit as String?)?.trim() ?: ""
                            val connector = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "

                            "$q $u$connector${ing.item}".replace(Regex("\\s+"), " ").trim()
                        }

                        "• $ingredientText"
                    }
                    sectionTitle + items
                }

            val stepsString = recipe.steps.mapIndexed { index, step ->
                "${index + 1}. $step"
            }.joinToString("\n")

            val tipsString = recipe.videoTips.joinToString("\n") { tip ->
                val prefix = when (tip.source) {
                    "IA" -> "[IA]"
                    "Pessoal" -> "[Pessoal]"
                    else -> "[Vídeo]"
                }
                "$prefix ${tip.text}"
            }

            val finalLink = originLink?.takeIf { it.isNotBlank() } ?: recipe.link

            val properties = RecipeProperties(
                name = NotionTitle(listOf(TextObject(TextContent(recipe.title)))),
                prepTime = NotionRichText(listOf(TextObject(TextContent(recipe.prepTime)))),
                ingredients = NotionRichText(sliceForNotion(ingredientsString)),
                instructions = NotionRichText(sliceForNotion(stepsString)),
                tags = NotionMultiSelect(recipe.tags.map { SelectOption(it) }),
                favorite = NotionCheckbox(recipe.favorite), // Preserves current state
                // Deliberately cast to nullable: the AI never includes "Status" in the
                // extracted JSON, and Gson ignores Kotlin's default value, leaving status null here.
                status = NotionStatus(StatusOption((recipe.status as String?) ?: "Não feito")), // Preserves current state
                link = if (!finalLink.isNullOrBlank()) NotionUrl(finalLink) else null,
                tips = NotionRichText(sliceForNotion(tipsString))
            )

            // ==========================================
            // PART 2: Check whether this is a CREATE or an UPDATE
            // ==========================================

            if (recipe.id.isNullOrBlank()) {
                val pageBlocks = mutableListOf<NotionBlock>()

                pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Ingredientes"))))))

                val groupedIngredients = recipe.ingredients.groupBy { it.section }
                groupedIngredients.forEach { (section, list) ->
                    if (!section.isNullOrEmpty()) {
                        pageBlocks.add(
                            Heading3Block(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(
                                                section
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                    list.forEach { ing ->
                        val ingredientText = if (ing.quantity.isNullOrBlank() && ing.unit.isNullOrBlank()) {
                            ing.item
                        } else {
                            // Deliberately cast to nullable: Gson ignores Kotlin's non-null type
                            // and can leave this null when the AI doesn't specify quantity/unit.
                            val q = (ing.quantity as String?)?.trim() ?: ""
                            val u = (ing.unit as String?)?.trim() ?: ""
                            val connector = if (u.isNotEmpty() || q.any { it.isLetter() }) " de " else " "
                            "$q $u$connector${ing.item}".replace(Regex("\\s+"), " ").trim()
                        }

                        pageBlocks.add(
                            BulletedListBlock(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(ingredientText)
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                if (recipe.videoTips.isNotEmpty()) {
                    pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Dicas e Comentários"))))))

                    recipe.videoTips.forEach { tip ->
                        val icon = when (tip.source) {
                            "IA" -> "💡 "
                            "Pessoal" -> "📝 "
                            else -> "📹 "
                        }
                        pageBlocks.add(
                            BulletedListBlock(
                                NotionRichText(
                                    listOf(
                                        TextObject(
                                            TextContent(icon + tip.text)
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                pageBlocks.add(Heading2Block(NotionRichText(listOf(TextObject(TextContent("Modo de Preparo"))))))

                recipe.steps.forEach { step ->
                    pageBlocks.add(
                        NumberedListBlock(
                            NotionRichText(
                                listOf(
                                    TextObject(
                                        TextContent(
                                            step
                                        )
                                    )
                                )
                            )
                        )
                    )
                }

                // ==========================================
                // PART 3: Fire the final request
                // ==========================================
                val request = NotionCreatePageRequest(
                    parent = NotionDatabaseParent(databaseId),
                    properties = properties,
                    children = pageBlocks
                )

                val response = api.addRecipe("Bearer $integrationToken", request)

                val created = response.body()
                if (response.isSuccessful && created != null) {
                    Log.d(
                        "BakeItOffDebug",
                        "Sucesso Híbrido! Colunas preenchidas e página desenhada."
                    )
                    return created.id
                } else {
                    Log.e("BakeItOffDebug", "Erro do Notion: ${response.errorBody()?.string()}")
                    return null
                }
            } else {
                // ➔ This is an EXISTING recipe (PATCH)
                val request = UpdateFullPageRequest(properties = properties)

                val response = api.updateFullPage(
                    token = "Bearer $integrationToken",
                    version = "2022-06-28",
                    pageId = recipe.id,
                    request = request
                )

                return if (response.isSuccessful) {
                    Log.d("BakeItOffDebug", "Receita ATUALIZADA com sucesso no Notion!")
                    recipe.id
                } else {
                    Log.e("BakeItOffDebug", "Erro ao atualizar: ${response.errorBody()?.string()}")
                    null
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Lets the exception propagate instead of swallowing it and emitting an empty
    // list — that way whoever collects this Flow (the ViewModel) knows the fetch
    // failed and can warn the user, instead of silently showing "no recipes" as if
    // the list were genuinely empty.
    suspend fun fetchRecipes(): Flow<List<Recipe>> = flow {
        val allRecipes = mutableListOf<Recipe>()
        var currentCursor: String? = null
        var hasMorePages = true

        while (hasMorePages) {
            val requestBody = QueryDatabaseRequest(start_cursor = currentCursor)
            val response = api.queryDatabase("Bearer $integrationToken", databaseId, requestBody)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                hasMorePages = body.has_more
                currentCursor = body.next_cursor

                val pageRecipes = body.results.map { page -> NotionRecipeMapper.toRecipe(page) }

                allRecipes.addAll(pageRecipes)
                emit(allRecipes.toList())

            } else {
                val error = response.errorBody()?.string()
                Log.e("BakeItOffDebug", "Erro ao buscar do Notion: $error")
                throw IOException("Notion respondeu ${response.code()} ao buscar receitas")
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun updateFavorite(pageId: String, favorite: Boolean): Boolean {
        return try {
            // Builds the structure the Notion API requires
            val request = UpdatePageRequest(
                properties = UpdateProperties(
                    favorite = CheckboxProperty(checkbox = favorite)
                )
            )

            val response = api.updatePageProperties(
                token = "Bearer $integrationToken",
                version = "2022-06-28",
                pageId = pageId,
                request = request
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Favorito atualizado com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao atualizar favorito: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão: ${e.message}")
            false
        }
    }

    suspend fun updateStatus(pageId: String, newStatus: String): Boolean {
        return try {
            // Builds the structure the Notion API requires
            val request = UpdatePageRequest(
                properties = UpdateProperties(
                    status = StatusProperty(StatusName(newStatus))
                )
            )

            val response = api.updatePageProperties(
                token = "Bearer $integrationToken",
                version = "2022-06-28",
                pageId = pageId,
                request = request
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Status atualizado com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao atualizar status: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão: ${e.message}")
            false
        }
    }
    suspend fun deleteRecipe(pageId: String): Boolean {
        return try {
            val response = api.archivePage(
                token = "Bearer $integrationToken",
                version = "2022-06-28",
                pageId = pageId,
                request = ArchivePageRequest() // Sends archived = true by default
            )

            if (response.isSuccessful) {
                Log.d("BakeItOffDebug", "Receita arquivada/deletada com sucesso no Notion!")
                true
            } else {
                Log.e("BakeItOffDebug", "Erro ao deletar: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("BakeItOffDebug", "Erro de conexão ao deletar: ${e.message}")
            false
        }
    }
}
