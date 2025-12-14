package com.example.guidelensapp.network

import android.util.Log
import com.example.guidelensapp.data.api.OpenAIClient
import com.example.guidelensapp.data.api.OpenAIRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service using the Custom OpenAI API integration.
 * Replaces GeminiService for medicine analysis.
 */
object OpenAIService {
    private const val TAG = "OpenAIService"

    suspend fun summarizeMedicine(text: String): String {
        Log.d(TAG, "🔍 Requesting AI analysis via OpenAI (v1/responses)...")

        return withContext(Dispatchers.IO) {
            try {
                // Keep the prompt logic similar to GeminiService as requested
                val prompt = """
                    I have scanned a medicine package text: "$text".
                    Identify the medicine name and briefly explain its primary use in one short sentence.
                    Do not include markdown or asterisks. Just plain text.
                    If the text is gibberish or not a medicine, say "Could not identify medicine."
                """.trimIndent()

                // Use the custom endpoint structure provided by USER
                val request = OpenAIRequest(
                    model = "gpt-5-nano",
                    input = prompt,
                    store = true
                )

                val response = OpenAIClient.api.generateResponse(request)
                
                // Extract content based on the response structure
                val result = response.choices?.firstOrNull()?.message?.content?.trim() 
                    ?: "No analysis returned."

                Log.d(TAG, "✅ Success: $result")
                result
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 429) {
                    Log.e(TAG, "❌ Rate Limit Exceeded (429)", e)
                    "System busy (Rate Limit). Please try again later."
                } else {
                    Log.e(TAG, "❌ HTTP Error: ${e.code()}", e)
                    "Service error: ${e.code()}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ AI Request Failed", e)
                "Analysis failed: ${e.message}"
            }
        }
    }
}
