package com.example.guidelensapp.data.api

import com.example.guidelensapp.BuildConfig
import com.example.guidelensapp.Config
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val BASE_URL = Config.SERVER_BASE_URL

    private val client by lazy {
        OkHttpClient.Builder()
            // No Authorization header needed for local server
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }
}
