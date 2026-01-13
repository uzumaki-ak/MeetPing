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
    /**
     * Answer user's question with automatic provider fallback
     * WORKS WITH ANY LANGUAGE - Hindi, English, mixed
     */
    suspend fun answerQuestion(question: String, context: String): LLMResponse {
        val providers = getProviderFallbackOrder()

        if (providers.isEmpty()) {
            return LLMResponse(
                content = "No API keys configured. Please add an API key in settings.",
                provider = "none",
                success = false,
                errorMessage = "No API keys available"
            )
        }

        // Enhanced prompt for multilingual support
        val enhancedContext = """
MEETING CONTEXT:
$context

USER'S QUESTION (answer in the SAME language as the question):
$question

INSTRUCTIONS:
- Answer in the SAME language as the question
- If question is in Hindi, answer in Hindi
- If question is in English, answer in English
- Be concise (2-4 sentences)
- If information not available, say "I don't have that information"

ANSWER:
    """.trimIndent()

        for (provider in providers) {
            try {
                Log.d(TAG, "Attempting with provider: $provider")

                val response = when (provider) {
                    ApiKeyManager.PROVIDER_CLAUDE ->
                        claudeClient?.answerQuestion(question, enhancedContext)
                    ApiKeyManager.PROVIDER_GEMINI ->
                        geminiClient?.answerQuestion(question, enhancedContext)
                    ApiKeyManager.PROVIDER_EURON ->
                        euronClient?.answerQuestion(question, enhancedContext)
                    else -> null
                }

                if (response != null && response.success) {
                    Log.d(TAG, "Success with $provider")
                    return response
                }

                Log.w(TAG, "Provider $provider failed: ${response?.errorMessage}")

            } catch (e: Exception) {
                Log.e(TAG, "Exception with $provider: ${e.message}", e)
            }
        }

        return LLMResponse(
            content = "Unable to answer. All LLM providers failed. Check API keys and internet.",
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