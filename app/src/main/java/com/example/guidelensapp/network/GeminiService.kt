package com.example.guidelensapp.network

import android.util.Log
import com.example.guidelensapp.data.api.GeminiClient
import com.example.guidelensapp.utils.TextFilterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Service using the Local Server API integration with fallback support.
 * Handles server health checks and intelligent fallback to Google Search.
 * Server uses Google Gemini 2.5 Flash for medicine identification.
 * 
 * ALL errors (HTTP 500, quota, network, timeout, etc.) automatically
 * trigger Google Search fallback for maximum reliability.
 */
object GeminiService {
    private const val TAG = "GeminiService"
    private const val HEALTH_CHECK_TIMEOUT = 5000L // 5 seconds
    private const val API_REQUEST_TIMEOUT = 15000L // 15 seconds
    
    /**
     * Result wrapper to indicate source of response
     */
    data class MedicineResult(
        val text: String,
        val source: Source,
        val isError: Boolean = false
    )
    
    enum class Source {
        SERVER,      // Response from local server (Gemini)
        GOOGLE,      // Fallback to Google Search
        ERROR        // Error occurred
    }
    
    /**
     * Check if the local server is available and responding
     */
    suspend fun isServerAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(HEALTH_CHECK_TIMEOUT) {
                    val response = GeminiClient.api.healthCheck()
                    val isHealthy = response.status == "ok"
                    Log.d(TAG, "🏥 Server health check: ${if (isHealthy) "✅ OK" else "❌ Failed"}")
                    isHealthy
                }
            } catch (e: Exception) {
                Log.w(TAG, "🏥 Server health check failed: ${e.message}")
                false
            }
        }
    }

    /**
     * Summarize medicine text with automatic fallback.
     * First attempts local server (Gemini), falls back to Google Search if unavailable.
     * 
     * @param text Raw scanned text from OCR
     * @return MedicineResult containing response and source
     */
    suspend fun summarizeMedicine(text: String): MedicineResult {
        Log.d(TAG, "🔍 Requesting medicine analysis for: $text")

        return withContext(Dispatchers.IO) {
            // Filter text to get relevant keywords
            val filteredText = TextFilterUtil.filterRelevantWords(text)
            Log.d(TAG, "📝 Filtered text: $filteredText")
            
            // Check server availability first
            val serverAvailable = isServerAvailable()
            
            if (serverAvailable) {
                // Try server API (Gemini)
                try {
                    Log.d(TAG, "🌐 Attempting server API at ${com.example.guidelensapp.Config.SERVER_BASE_URL}")
                    
                    val request = com.example.guidelensapp.data.api.LocalRequest(
                        text = filteredText
                    )

                    val response = withTimeout(API_REQUEST_TIMEOUT) {
                        GeminiClient.api.generateResponse(request)
                    }
                    
                    if (response.error != null) {
                        // ANY server error → fallback to Google
                        Log.e(TAG, "❌ Server Error: ${response.error}")
                        Log.d(TAG, "💡 Falling back to Google Search")
                        return@withContext MedicineResult(
                            text = "Server error. Opening Google Search instead.",
                            source = Source.GOOGLE,
                            isError = false
                        )
                    } else {
                        val result = response.result ?: "No analysis returned."
                        Log.d(TAG, "✅ Server Success: $result")
                        return@withContext MedicineResult(
                            text = result,
                            source = Source.SERVER,
                            isError = false
                        )
                    }
                } catch (e: retrofit2.HttpException) {
                    // ANY HTTP error (500, 429, 404, etc.) → fallback to Google
                    Log.e(TAG, "❌ HTTP Error ${e.code()}, falling back to Google", e)
                    return@withContext MedicineResult(
                        text = "Server error (${e.code()}). Opening Google Search instead.",
                        source = Source.GOOGLE,
                        isError = false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Server Request Failed", e)
                    // Network error - fallback to Google
                    return@withContext MedicineResult(
                        text = "Server connection failed. Check Google Search instead.",
                        source = Source.GOOGLE,
                        isError = false
                    )
                }
            } else {
                // Server not available - use Google fallback
                Log.d(TAG, "🔄 Server offline, using Google Search fallback")
                return@withContext MedicineResult(
                    text = "Server offline. Opening Google Search for: $filteredText",
                    source = Source.GOOGLE,
                    isError = false
                )
            }
        }
    }
}
