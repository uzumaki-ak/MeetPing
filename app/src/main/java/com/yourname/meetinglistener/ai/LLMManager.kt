package com.yourname.meetinglistener.ai

import android.content.Context
import android.util.Log
import com.yourname.meetinglistener.models.*
import com.yourname.meetinglistener.utils.ApiKeyManager

/**
 * LLMManager.kt
 *
 * PURPOSE:
 * Central manager for all LLM operations with automatic fallback
 * If one provider fails, automatically tries the next available provider
 *
 * FEATURES:
 * - Multi-provider support (Claude, Gemini, Euron)
 * - Automatic fallback on failure
 * - Provider rotation to balance API usage
 * - Centralized error handling
 *
 * USAGE:
 * val llmManager = LLMManager(context)
 * val response = llmManager.answerQuestion("What was decided?", meetingContext)
 */
class LLMManager(context: Context) {

    private val apiKeyManager = ApiKeyManager(context)
    private val TAG = "LLMManager"

    // Initialize all available LLM clients
    private var claudeClient: ClaudeClient? = null
    private var geminiClient: GeminiClient? = null
    private var euronClient: EuronClient? = null

    init {
        // Initialize clients based on available API keys
        if (apiKeyManager.hasClaudeKey()) {
            claudeClient = ClaudeClient(apiKeyManager.getClaudeKey())
            Log.d(TAG, "Claude client initialized")
        }

        if (apiKeyManager.hasGeminiKey()) {
            geminiClient = GeminiClient(apiKeyManager.getGeminiKey())
            Log.d(TAG, "Gemini client initialized")
        }

        if (apiKeyManager.hasEuronKey()) {
            euronClient = EuronClient(apiKeyManager.getEuronKey())
            Log.d(TAG, "Euron client initialized")
        }
    }

    /**
     * Answer user's question with automatic provider fallback
     * Tries active provider first, then falls back to others if it fails
     *
     * @param question User's question
     * @param context Meeting context (condensed)
     * @return LLMResponse with answer or error
     */
    suspend fun answerQuestion(question: String, context: String): LLMResponse {
        // Get list of providers to try (active provider first)
        val providers = getProviderFallbackOrder()

        if (providers.isEmpty()) {
            return LLMResponse(
                content = "No API keys configured. Please add an API key in settings.",
                provider = "none",
                success = false,
                errorMessage = "No API keys available"
            )
        }

        // Try each provider until one succeeds
        for (provider in providers) {
            try {
                Log.d(TAG, "Attempting to answer with provider: $provider")

                val response = when (provider) {
                    ApiKeyManager.PROVIDER_CLAUDE ->
                        claudeClient?.answerQuestion(question, context)
                    ApiKeyManager.PROVIDER_GEMINI ->
                        geminiClient?.answerQuestion(question, context)
                    ApiKeyManager.PROVIDER_EURON ->
                        euronClient?.answerQuestion(question, context)
                    else -> null
                }

                // If response is successful, return it
                if (response != null && response.success) {
                    Log.d(TAG, "Successfully answered with $provider")
                    return response
                }

                // If response failed, log and try next provider
                Log.w(TAG, "Provider $provider failed: ${response?.errorMessage}")

            } catch (e: Exception) {
                Log.e(TAG, "Exception with provider $provider: ${e.message}", e)
            }
        }

        // All providers failed
        return LLMResponse(
            content = "Unable to answer question. All LLM providers failed. Please check your API keys and internet connection.",
            provider = "fallback_failed",
            success = false,
            errorMessage = "All providers failed"
        )
    }

    /**
     * Generate summary with automatic provider fallback
     *
     * @param request SummaryRequest with content and type
     * @return Summary text or error message
     */
    suspend fun generateSummary(request: SummaryRequest): String {
        val providers = getProviderFallbackOrder()

        if (providers.isEmpty()) {
            return "No API keys configured"
        }

        for (provider in providers) {
            try {
                Log.d(TAG, "Attempting summary with provider: $provider")

                val summary = when (provider) {
                    ApiKeyManager.PROVIDER_CLAUDE ->
                        claudeClient?.generateSummary(request)
                    ApiKeyManager.PROVIDER_GEMINI ->
                        geminiClient?.generateSummary(request)
                    ApiKeyManager.PROVIDER_EURON ->
                        euronClient?.generateSummary(request)
                    else -> null
                }

                // If summary generated successfully, return it
                if (!summary.isNullOrBlank() && !summary.startsWith("Error")) {
                    Log.d(TAG, "Successfully generated summary with $provider")
                    return summary
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception with provider $provider: ${e.message}", e)
            }
        }

        return "Failed to generate summary with all providers"
    }

    /**
     * Get ordered list of providers to try
     * Active provider first, then others
     */
    private fun getProviderFallbackOrder(): List<String> {
        val availableProviders = apiKeyManager.getAvailableProviders()
        val activeProvider = apiKeyManager.getActiveLLM()

        // Put active provider first, then others
        val ordered = mutableListOf<String>()
        if (availableProviders.contains(activeProvider)) {
            ordered.add(activeProvider)
        }

        availableProviders.forEach { provider ->
            if (provider != activeProvider) {
                ordered.add(provider)
            }
        }

        return ordered
    }

    /**
     * Refresh clients when API keys change
     * Call this after user updates API keys in settings
     */
    fun refreshClients() {
        claudeClient = if (apiKeyManager.hasClaudeKey()) {
            ClaudeClient(apiKeyManager.getClaudeKey())
        } else null

        geminiClient = if (apiKeyManager.hasGeminiKey()) {
            GeminiClient(apiKeyManager.getGeminiKey())
        } else null

        euronClient = if (apiKeyManager.hasEuronKey()) {
            EuronClient(apiKeyManager.getEuronKey())
        } else null

        Log.d(TAG, "LLM clients refreshed")
    }

    /**
     * Check if any LLM provider is available
     */
    fun hasAvailableProvider(): Boolean {
        return apiKeyManager.getAvailableProviders().isNotEmpty()
    }

    /**
     * Get current active provider name
     */
    fun getActiveProviderName(): String {
        return apiKeyManager.getProviderDisplayName(apiKeyManager.getActiveLLM())
    }
}