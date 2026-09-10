package com.bakeitoff

import com.google.gson.annotations.SerializedName

data class Receita(
    val id: String?,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("tempo_preparo") val tempoPreparo: String,
    @SerializedName("ingredientes") val ingredientes: List<Ingrediente>,
    @SerializedName("passos") val passos: List<String>,
    @SerializedName("Favorito") val favorito: Boolean = false,
    @SerializedName(value = "Status") val status: String = "Não feito",
    @SerializedName("Link") val link: String? = null,
    @SerializedName("dicas_video") val dicas_video: List<DicasComentario> = emptyList(),
    @SerializedName("tags") val tags: List<String> // Adicionamos as tags aqui!
)

data class Ingrediente(
    @SerializedName("quantidade") val quantidade: String,
    @SerializedName("unidade") val unidade: String,
    @SerializedName("item") val item: String,
    @SerializedName("secao") val secao: String? = null
)

data class DicasComentario(
    val texto: String,
    val fonte: String, // ex: "Video", "IA", "Comunidade"
    val enriquecida: Boolean // para saber se foi a IA que adicionou
)