package com.bakeitoff.data.notion

import com.google.gson.annotations.SerializedName

// Main payload we send to the API
data class NotionCreatePageRequest(
    val parent: NotionDatabaseParent,
    val properties: RecipeProperties,
    val children: List<NotionBlock>
)

data class NotionDatabaseParent(
    @SerializedName("database_id") val databaseId: String
)

// Maps exactly to the columns in the user's Notion database
data class RecipeProperties(
    @SerializedName("Nome") val name: NotionTitle,
    @SerializedName("Tempo de Preparo") val prepTime: NotionRichText,
    @SerializedName("Ingredientes") val ingredients: NotionRichText,
    @SerializedName("Preparo") val instructions: NotionRichText,
    @SerializedName("Tags") val tags: NotionMultiSelect,
    @SerializedName("Favorito") val favorite: NotionCheckbox,
    @SerializedName("Status") val status: NotionStatus,
    @SerializedName("Dicas") val tips: NotionRichText,
    @SerializedName("Link") val link: NotionUrl? = null
)

// Verbose structures required by the Notion API
data class NotionTitle(val title: List<TextObject>)
data class NotionRichText(@SerializedName("rich_text") val richText: List<TextObject>)
data class NotionMultiSelect(@SerializedName("multi_select") val multiSelect: List<SelectOption>)

data class TextObject(val text: TextContent)
data class TextContent(val content: String)
data class SelectOption(val name: String)
data class NotionUrl(val url: String)
data class NotionCheckbox(val checkbox: Boolean)
data class StatusOption(val name: String)
data class NotionStatus(val status: StatusOption)

data class ArchivePageRequest(
    val archived: Boolean = true
)

data class UpdateFullPageRequest(
    val properties: RecipeProperties
)

open class NotionBlock(
    val `object`: String = "block",
    val type: String
)

class Heading2Block(
    @SerializedName("heading_2") val heading2: NotionRichText
) : NotionBlock(type = "heading_2")

class Heading3Block(
    @SerializedName("heading_3") val heading3: NotionRichText
) : NotionBlock(type = "heading_3")

class BulletedListBlock(
    @SerializedName("bulleted_list_item") val bulletedListItem: NotionRichText
) : NotionBlock(type = "bulleted_list_item")

class NumberedListBlock(
    @SerializedName("numbered_list_item") val numberedListItem: NotionRichText
) : NotionBlock(type = "numbered_list_item")
