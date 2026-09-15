package com.bakeitoff.data.model

import com.google.gson.annotations.SerializedName

data class Recipe(
    val id: String?,
    @SerializedName("titulo") val title: String,
    @SerializedName("tempo_preparo") val prepTime: String,
    @SerializedName("ingredientes") val ingredients: List<Ingredient>,
    @SerializedName("passos") val steps: List<String>,
    @SerializedName("Favorito") val favorite: Boolean = false,
    @SerializedName(value = "Status") val status: String = "Não feito",
    @SerializedName("Link") val link: String? = null,
    @SerializedName("dicas_video") val videoTips: List<RecipeTip> = emptyList(),
    @SerializedName("tags") val tags: List<String> // Also carries the recipe's tags!
)

data class Ingredient(
    @SerializedName("quantidade") val quantity: String,
    @SerializedName("unidade") val unit: String,
    @SerializedName("item") val item: String,
    @SerializedName("secao") val section: String? = null
)

data class RecipeTip(
    @SerializedName("texto") val text: String,
    @SerializedName("fonte") val source: String, // e.g.: "Video", "IA", "Comunidade"
    @SerializedName("enriquecida") val enriched: Boolean // whether the AI added/expanded this tip
)
