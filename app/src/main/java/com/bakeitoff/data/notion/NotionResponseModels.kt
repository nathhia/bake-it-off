package com.bakeitoff.data.notion

import com.google.gson.annotations.SerializedName

data class QueryDatabaseRequest(
    val start_cursor: String? = null
)

// 2. ATUALIZE ESTA CLASSE: A resposta principal da busca (agora com paginação!)
data class NotionQueryResponse(
    val results: List<NotionPageResponse>,
    val next_cursor: String? = null,
    val has_more: Boolean = false
)

// Cada página (receita) dentro da lista
data class NotionPageResponse(
    val id: String,
    val properties: NotionPageProperties
)

// As colunas da tabela (use os nomes exatos das suas colunas do Notion)
data class NotionPageProperties(
    @SerializedName("Nome") val nome: NotionPropertyTitle?,
    @SerializedName("Tempo de Preparo") val tempoPreparo: NotionPropertyRichText?,
    @SerializedName("Tags") val tags: NotionPropertyMultiSelect?,

    @SerializedName("Ingredientes") val ingredientes: NotionPropertyRichText?,
    @SerializedName("Preparo") val passos: NotionPropertyRichText?,
    @SerializedName("Favorito") val favorito: NotionPropertyCheckbox? = null,
    @SerializedName("Status") val status: StatusProperty? = null,
    @SerializedName("Link") val link: UrlProperty? = null,
    @SerializedName("Dicas") val dicas: NotionPropertyRichText?,
)

// Estruturas internas para ler o texto que vem do Notion
data class NotionPropertyTitle(val title: List<TextObject>)
data class NotionPropertyRichText(val rich_text: List<TextObject>)
data class NotionPropertyMultiSelect(val multi_select: List<SelectOption>)

data class UrlProperty(
    @SerializedName("url")
    val url: String? = null
)

data class NotionPropertyCheckbox(
    @SerializedName("checkbox") val checkbox: Boolean
)
data class UpdatePageRequest(val properties: UpdateProperties)

data class UpdateProperties(
    @SerializedName("Favorito") val favorito: CheckboxProperty? = null,
    @SerializedName("Status") val status: StatusProperty? = null
)

data class StatusProperty(val status: StatusName)
data class StatusName(val name: String)
data class CheckboxProperty(val checkbox: Boolean)