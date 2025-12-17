package com.example.guidelensapp.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface GeminiApi {
    @Headers("Content-Type: application/json")
    @POST("identify")
    suspend fun generateResponse(@Body request: LocalRequest): LocalResponse
    
    @GET("health")
    suspend fun healthCheck(): HealthResponse
}

data class LocalRequest(
    val text: String
)

data class LocalResponse(
    val result: String?,
    val error: String?,
    val filtered_text: String? = null
)

data class HealthResponse(
    val status: String,
    val server: String,
    val api: String? = null
)
