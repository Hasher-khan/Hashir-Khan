package com.example.data.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiWeatherClient {
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getAiDiagnostics(
        cityName: String,
        temp: Double,
        humidity: Double,
        windSpeed: Double,
        weatherCode: Int,
        condition: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Configure your Gemini API Key in the Secrets panel to get detailed AI weather insights!"
        }

        val systemPrompt = """
            You are a charming local weather radio host style AI Advisor for a beautiful Weather App.
            Write a super concise (max 2 short sentences), witty, and creative diagnostic forecast + dress or activity recommendation based on the current weather.
            Do not list the raw numbers again, just give high-quality context-aware creative, fun recommendations!
        """.trimIndent()

        val userPrompt = """
            City: $cityName
            Temperature: ${temp}°C
            Humidity: $humidity%
            Wind Speed: $windSpeed m/s
            WMO Weather code: $weatherCode
            General Condition: $condition
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error: Failed to obtain AI insights (${response.code})."
                }
                val bodyString = response.body?.string() ?: return@withContext "Error: Empty response from AI service."
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text")

                text?.trim() ?: "No custom insights available for today's weather."
            }
        } catch (e: Exception) {
            "Weather expert is offline. Please check your network connection."
        }
    }
}
