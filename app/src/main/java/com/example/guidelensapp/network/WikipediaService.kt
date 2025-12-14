package com.example.guidelensapp.network

import android.util.Log
import com.example.guidelensapp.data.api.WikipediaApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WikipediaService {
    private const val BASE_URL = "https://en.wikipedia.org/api/rest_v1/"
    private const val TAG = "WikipediaService"

    private val api: WikipediaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApi::class.java)
    }

    suspend fun getSummary(term: String): String? {
        return try {
            Log.d(TAG, "🔍 Searching Wikipedia for: $term")
            
            // Step 1: Search for the term using MediaWiki Search API (fuzzy search)
            val searchResponse = api.search(term)
            val firstHit = searchResponse.query?.search?.firstOrNull()
            
            if (firstHit != null) {
                Log.d(TAG, "✅ Found existing article: ${firstHit.title}")
                
                // Step 2: Get the summary of the Best Match title
                val summaryResponse = api.getSummary(firstHit.title)
                Log.d(TAG, "✅ Retrieved summary for: ${summaryResponse.title}")
                
                 // Return the extract (summary)
                summaryResponse.extract.ifBlank { firstHit.snippet?.replace(Regex("<[^>]*>"), "") } // Fallback to snippet if extract is empty
            } else {
                Log.w(TAG, "❌ No articles found for: $term")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wikipedia search failed", e)
            null
        }
    }
}
