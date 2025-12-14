package com.example.guidelensapp.data.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAIApi {
    @Headers("Content-Type: application/json")
    @POST("v1/responses")
    suspend fun generateResponse(@Body request: OpenAIRequest): OpenAIResponse
}

data class OpenAIRequest(
    val model: String,
    val input: String,
    val store: Boolean = true
)

data class OpenAIResponse(
    val choices: List<Choice>? = null,
    // Add other fields as needed based on actual response structure
    // Since this is a custom endpoint, guessing common OpenAI structure or what's needed
    val id: String? = null,
    val created: Long? = 0
)

data class Choice(
    val message: Message?
)

data class Message(
    val role: String?,
    val content: String?
)
