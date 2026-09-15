package com.bakeitoff.data.notion

import com.google.gson.annotations.SerializedName

data class QueryDatabaseRequest(
    val start_cursor: String? = null
)

// Main search response (paginated)
data class NotionQueryResponse(
    val results: List<NotionPageResponse>,
    val next_cursor: String? = null,
    val has_more: Boolean = false
)

// Each page (recipe) in the list
data class NotionPageResponse(
    val id: String,
    val properties: NotionPageProperties
)

// Response from creating a page (POST /v1/pages) — we only need the new id,
// so we can insert the recipe into the local list without a new fetch.
data class CreatedPageResponse(
    val id: String
)

// The table columns (use the exact names of your Notion columns)
data class NotionPageProperties(
    @SerializedName("Nome") val name: NotionPropertyTitle?,
    @SerializedName("Tempo de Preparo") val prepTime: NotionPropertyRichText?,
    @SerializedName("Tags") val tags: NotionPropertyMultiSelect?,

    @SerializedName("Ingredientes") val ingredients: NotionPropertyRichText?,
    @SerializedName("Preparo") val instructions: NotionPropertyRichText?,
    @SerializedName("Favorito") val favorite: NotionPropertyCheckbox? = null,
    @SerializedName("Status") val status: StatusProperty? = null,
    @SerializedName("Link") val link: UrlProperty? = null,
    @SerializedName("Dicas") val tips: NotionPropertyRichText?,
)

// Internal structures for reading the text that comes back from Notion
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
    @SerializedName("Favorito") val favorite: CheckboxProperty? = null,
    @SerializedName("Status") val status: StatusProperty? = null
)

data class StatusProperty(val status: StatusName)
data class StatusName(val name: String)
data class CheckboxProperty(val checkbox: Boolean)