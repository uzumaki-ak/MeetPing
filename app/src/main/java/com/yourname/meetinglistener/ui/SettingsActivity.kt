package com.yourname.meetinglistener.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.meetinglistener.databinding.ActivitySettingsBinding
import com.yourname.meetinglistener.utils.ApiKeyManager

/**
 * SettingsActivity.kt
 *
 * PURPOSE:
 * Allows user to configure app settings
 * Manage API keys for different LLM providers
 * Set user name and preferences
 *
 * FEATURES:
 * - API key input for Claude, Gemini, Euron
 * - Active provider selection
 * - User name configuration
 * - Clear all data option
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var apiKeyManager: ApiKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiKeyManager = ApiKeyManager(this)

        setupUI()
        loadCurrentSettings()
    }

    /**
     * Setup UI components
     */
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Clear data button
        binding.btnClearData.setOnClickListener {
            clearAllData()
        }

        // Provider radio buttons
        binding.radioGroupProvider.setOnCheckedChangeListener { _, checkedId ->
            // Handle provider selection change if needed
        }
    }

    /**
     * Load current settings into UI
     */
    private fun loadCurrentSettings() {
        // Load API keys (show masked)
        val claudeKey = apiKeyManager.getClaudeKey()
        val geminiKey = apiKeyManager.getGeminiKey()
        val euronKey = apiKeyManager.getEuronKey()

        binding.etClaudeKey.setText(if (claudeKey.isNotBlank()) "••••••••" else "")
        binding.etGeminiKey.setText(if (geminiKey.isNotBlank()) "••••••••" else "")
        binding.etEuronKey.setText(if (euronKey.isNotBlank()) "••••••••" else "")

        // Set hint to show if key is from BuildConfig or user-provided
        binding.etClaudeKey.hint = if (claudeKey.isNotBlank()) "Key configured" else "Enter Claude API key"
        binding.etGeminiKey.hint = if (geminiKey.isNotBlank()) "Key configured" else "Enter Gemini API key"
        binding.etEuronKey.hint = if (euronKey.isNotBlank()) "Key configured" else "Enter Euron API key"

        // Load active provider
        val activeProvider = apiKeyManager.getActiveLLM()
        when (activeProvider) {
            ApiKeyManager.PROVIDER_CLAUDE -> binding.radioClaude.isChecked = true
            ApiKeyManager.PROVIDER_GEMINI -> binding.radioGemini.isChecked = true
            ApiKeyManager.PROVIDER_EURON -> binding.radioEuron.isChecked = true
        }

        // Load user name
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "")
        binding.etUserName.setText(userName)
    }

    /**
     * Save settings
     */
    private fun saveSettings() {
        // Save API keys (only if not masked placeholder)
        val claudeKey = binding.etClaudeKey.text.toString()
        if (claudeKey.isNotBlank() && !claudeKey.contains("•")) {
            apiKeyManager.setClaudeKey(claudeKey)
        }

        val geminiKey = binding.etGeminiKey.text.toString()
        if (geminiKey.isNotBlank() && !geminiKey.contains("•")) {
            apiKeyManager.setGeminiKey(geminiKey)
        }

        val euronKey = binding.etEuronKey.text.toString()
        if (euronKey.isNotBlank() && !euronKey.contains("•")) {
            apiKeyManager.setEuronKey(euronKey)
        }

        // Save active provider
        val selectedProvider = when (binding.radioGroupProvider.checkedRadioButtonId) {
            binding.radioClaude.id -> ApiKeyManager.PROVIDER_CLAUDE
            binding.radioGemini.id -> ApiKeyManager.PROVIDER_GEMINI
            binding.radioEuron.id -> ApiKeyManager.PROVIDER_EURON
            else -> ApiKeyManager.PROVIDER_CLAUDE
        }
        apiKeyManager.setActiveLLM(selectedProvider)

        // Save user name
        val userName = binding.etUserName.text.toString().trim()
        if (userName.isNotBlank()) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("user_name", userName).apply()
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * Clear all data
     */
    private fun clearAllData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will delete all API keys and settings. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                apiKeyManager.clearAllKeys()

                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().clear().apply()

                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}