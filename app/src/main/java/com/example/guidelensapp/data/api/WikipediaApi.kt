package com.example.guidelensapp.data.api

import retrofit2.http.GET
import retrofit2.http.Path

interface WikipediaApi {
    @GET("page/summary/{title}")
    suspend fun getSummary(@Path("title") title: String): WikipediaResponse

    // Add search endpoint (using MediaWiki ACTION API)
    // https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch={term}
    @GET("https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&utf8=1")
    suspend fun search(@retrofit2.http.Query("srsearch") term: String): WikiSearchResponse
}

data class WikipediaResponse(
    val title: String,
    val extract: String,
    val description: String? = null,
    val thumbnail: WikiImage? = null
)

data class WikiImage(
    val source: String,
    val width: Int,
    val height: Int
)

data class WikiSearchResponse(
    val query: WikiQuery?
)

data class WikiQuery(
    val search: List<WikiSearchResult>?
)

data class WikiSearchResult(
    val title: String,
    val snippet: String?
)
