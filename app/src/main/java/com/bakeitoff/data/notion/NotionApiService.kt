package com.bakeitoff.data.notion

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface NotionApiService {

    @Headers(
        "Content-Type: application/json",
        "Notion-Version: 2022-06-28" // Current required API version
    )
    @POST("v1/pages")
    suspend fun addRecipe(
        @Header("Authorization") bearerToken: String,
        @Body request: NotionCreatePageRequest
    ): Response<CreatedPageResponse> // We need the id of the created page

    @Headers(
        "Notion-Version: 2022-06-28",
        "Content-Type: application/json"
    )
    @POST("v1/databases/{database_id}/query")
    suspend fun queryDatabase(
        @Header("Authorization") token: String,
        @Path("database_id") databaseId: String,
        @Body request: QueryDatabaseRequest = QueryDatabaseRequest()
    ): Response<NotionQueryResponse>


    @Headers(
        "Content-Type: application/json"
    )
    @PATCH("v1/pages/{pageId}")
    suspend fun updatePageProperties(
        @Header("Authorization") token: String,
        @Header("Notion-Version") version: String,
        @Path("pageId") pageId: String,
        @Body request: UpdatePageRequest
    ): retrofit2.Response<okhttp3.ResponseBody>

    @PATCH("v1/pages/{page_id}")
    suspend fun updateFullPage(
        @Header("Authorization") token: String,
        @Header("Notion-Version") version: String,
        @Path("page_id") pageId: String,
        @Body request: UpdateFullPageRequest
    ): Response<okhttp3.ResponseBody>

    @PATCH("v1/pages/{page_id}")
    suspend fun archivePage(
        @Header("Authorization") token: String,
        @Header("Notion-Version") version: String,
        @Path("page_id") pageId: String,
        @Body request: ArchivePageRequest
    ): Response<okhttp3.ResponseBody>
}