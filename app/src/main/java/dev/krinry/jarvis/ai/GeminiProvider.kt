package dev.krinry.jarvis.ai

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Provider — Google's AI models via generativelanguage API.
 * Uses OpenAI-compatible endpoint for chat completions.
 * API: generativelanguage.googleapis.com
 */
class GeminiProvider : LlmProvider {
    override val id = "gemini"
    override val displayName = "Gemini"
    override val baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
    override val defaultModel = "gemini-2.0-flash"
    override val defaultFallbackModel = "gemini-1.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchModels(apiKey: String): List<ModelInfo> {
        return try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get().build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val data = JSONObject(body).optJSONArray("models") ?: return emptyList()

            val models = mutableListOf<ModelInfo>()
            for (i in 0 until data.length()) {
                val model = data.getJSONObject(i)
                val fullName = model.optString("name", "")
                val modelId = fullName.removePrefix("models/")
                val displayName = model.optString("displayName", modelId)
                val supportedMethods = model.optJSONArray("supportedGenerationMethods")
                val supportsChat = (0 until (supportedMethods?.length() ?: 0)).any {
                    supportedMethods?.optString(it)?.contains("generateContent") == true
                }

                if (modelId.isNotEmpty() && supportsChat && !modelId.contains("embedding")) {
                    models.add(ModelInfo(
                        id = modelId,
                        name = displayName,
                        isFree = true, // Gemini API has free tier
                        contextLength = model.optInt("inputTokenLimit", 0)
                    ))
                }
            }
            models.sortedBy { it.name }
        } catch (e: Exception) {
            Log.e("GeminiProvider", "Fetch models failed", e)
            emptyList()
        }
    }
}
