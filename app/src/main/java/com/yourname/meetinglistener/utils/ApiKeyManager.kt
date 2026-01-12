package com.yourname.meetinglistener.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yourname.meetinglistener.BuildConfig

/**
 * ApiKeyManager.kt
 *
 * PURPOSE:
 * Manages API keys for all LLM services (Claude, Gemini, Euron).
 * Provides fallback mechanism: User-provided keys → BuildConfig keys.
 * Uses encrypted storage for security.
 *
 * FEATURES:
 * - Encrypted SharedPreferences for user-entered keys
 * - Fallback to BuildConfig keys from local.properties
 * - Easy key validation and update methods
 * - Supports multiple LLM providers
 *
 * USAGE:
 * val apiKeyManager = ApiKeyManager(context)
 * val claudeKey = apiKeyManager.getClaudeKey()
 * apiKeyManager.setClaudeKey("user_custom_key")
 */
class ApiKeyManager(context: Context) {

    // Encrypted SharedPreferences instance for secure storage
    private val encryptedPrefs: SharedPreferences

    // Preference keys for different API services
    companion object {
        private const val PREFS_NAME = "api_keys_encrypted"
        private const val KEY_CLAUDE = "claude_api_key"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_EURON = "euron_api_key"
        private const val KEY_ACTIVE_LLM = "active_llm_provider"

        // LLM Provider constants
        const val PROVIDER_CLAUDE = "claude"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_EURON = "euron"
    }

    init {
        // Create or retrieve master key for encryption
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize encrypted SharedPreferences
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ========== CLAUDE API KEY METHODS ==========

    /**
     * Get Claude API key with fallback mechanism
     * 1. Check user-provided key from UI
     * 2. Fallback to BuildConfig key from local.properties
     * @return API key or empty string if none available
     */
    fun getClaudeKey(): String {
        val userKey = encryptedPrefs.getString(KEY_CLAUDE, null)
        return if (!userKey.isNullOrBlank()) {
            userKey // User-provided key takes priority
        } else {
            BuildConfig.CLAUDE_API_KEY // Fallback to build config
        }
    }

    /**
     * Save user-provided Claude API key
     * @param key The API key to save
     */
    fun setClaudeKey(key: String) {
        encryptedPrefs.edit().putString(KEY_CLAUDE, key).apply()
    }

    /**
     * Check if Claude API key is available
     * @return true if either user key or BuildConfig key exists
     */
    fun hasClaudeKey(): Boolean {
        return getClaudeKey().isNotBlank()
    }

    // ========== GEMINI API KEY METHODS ==========

    /**
     * Get Gemini API key with fallback mechanism
     */
    fun getGeminiKey(): String {
        val userKey = encryptedPrefs.getString(KEY_GEMINI, null)
        return if (!userKey.isNullOrBlank()) {
            userKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }
    }

    /**
     * Save user-provided Gemini API key
     */
    fun setGeminiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_GEMINI, key).apply()
    }

    /**
     * Check if Gemini API key is available
     */
    fun hasGeminiKey(): Boolean {
        return getGeminiKey().isNotBlank()
    }

    // ========== EURON API KEY METHODS ==========

    /**
     * Get Euron API key with fallback mechanism
     */
    fun getEuronKey(): String {
        val userKey = encryptedPrefs.getString(KEY_EURON, null)
        return if (!userKey.isNullOrBlank()) {
            userKey
        } else {
            BuildConfig.EURON_API_KEY
        }
    }

    /**
     * Save user-provided Euron API key
     */
    fun setEuronKey(key: String) {
        encryptedPrefs.edit().putString(KEY_EURON, key).apply()
    }

    /**
     * Check if Euron API key is available
     */
    fun hasEuronKey(): Boolean {
        return getEuronKey().isNotBlank()
    }

    // ========== ACTIVE LLM PROVIDER MANAGEMENT ==========

    /**
     * Get currently active LLM provider
     * Defaults to first available provider
     * @return Provider constant (PROVIDER_CLAUDE, PROVIDER_GEMINI, or PROVIDER_EURON)
     */
    fun getActiveLLM(): String {
        val saved = encryptedPrefs.getString(KEY_ACTIVE_LLM, null)

        // If saved provider exists and has key, use it
        if (!saved.isNullOrBlank()) {
            when (saved) {
                PROVIDER_CLAUDE -> if (hasClaudeKey()) return PROVIDER_CLAUDE
                PROVIDER_GEMINI -> if (hasGeminiKey()) return PROVIDER_GEMINI
                PROVIDER_EURON -> if (hasEuronKey()) return PROVIDER_EURON
            }
        }

        // Otherwise find first available provider
        return when {
            hasClaudeKey() -> PROVIDER_CLAUDE
            hasGeminiKey() -> PROVIDER_GEMINI
            hasEuronKey() -> PROVIDER_EURON
            else -> PROVIDER_CLAUDE // Default even if no key (will fail gracefully)
        }
    }

    /**
     * Set active LLM provider
     * @param provider Provider constant to set as active
     */
    fun setActiveLLM(provider: String) {
        encryptedPrefs.edit().putString(KEY_ACTIVE_LLM, provider).apply()
    }

    /**
     * Get list of available providers (those with valid keys)
     * @return List of provider constants
     */
    fun getAvailableProviders(): List<String> {
        val available = mutableListOf<String>()
        if (hasClaudeKey()) available.add(PROVIDER_CLAUDE)
        if (hasGeminiKey()) available.add(PROVIDER_GEMINI)
        if (hasEuronKey()) available.add(PROVIDER_EURON)
        return available
    }

    /**
     * Clear all stored API keys (useful for logout/reset)
     */
    fun clearAllKeys() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Get human-readable provider name
     * @param provider Provider constant
     * @return Display name for UI
     */
    fun getProviderDisplayName(provider: String): String {
        return when (provider) {
            PROVIDER_CLAUDE -> "Claude (Anthropic)"
            PROVIDER_GEMINI -> "Gemini (Google)"
            PROVIDER_EURON -> "Euron"
            else -> "Unknown"
        }
    }
}