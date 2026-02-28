package dev.krinry.jarvis.ai

import android.content.Context
import dev.krinry.jarvis.security.SecureKeyStore

/**
 * LlmProvider — Interface for all AI providers.
 * Each provider implements this interface in its own file.
 * Easy to add new providers: just implement this interface.
 */
interface LlmProvider {
    /** Unique identifier: "groq", "openrouter", "gemini" */
    val id: String

    /** Display name for UI */
    val displayName: String

    /** Base URL for chat completions */
    val baseUrl: String

    /** Default primary model */
    val defaultModel: String

    /** Default fallback model */
    val defaultFallbackModel: String

    /** Whether this provider supports STT (Whisper) */
    val supportsSTT: Boolean get() = false

    /** Build extra headers for this provider (e.g., OpenRouter's Referer) */
    fun extraHeaders(): Map<String, String> = emptyMap()

    /** Fetch available models from this provider's API */
    suspend fun fetchModels(apiKey: String): List<ModelInfo>

    /** Get stored API key for this provider */
    fun getApiKey(context: Context): String? {
        return SecureKeyStore.getProviderApiKey(context, id)
    }

    /** Save API key for this provider */
    fun saveApiKey(context: Context, key: String) {
        SecureKeyStore.saveProviderApiKey(context, id, key)
    }
}

/** Model information returned by providers */
data class ModelInfo(
    val id: String,
    val name: String,
    val isFree: Boolean = false,
    val contextLength: Int = 0
)
