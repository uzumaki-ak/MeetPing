package com.yourname.meetinglistener.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yourname.meetinglistener.databinding.ActivitySettingsBinding
import com.yourname.meetinglistener.storage.MeetingDatabase
import com.yourname.meetinglistener.utils.ApiKeyManager
import kotlinx.coroutines.launch

/**
 * SettingsActivity.kt (FIXED - COROUTINE ERROR)
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

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnClearData.setOnClickListener {
            clearAllData()
        }
    }

    private fun loadCurrentSettings() {
        // Load API keys (masked)
        val claudeKey = apiKeyManager.getClaudeKey()
        val geminiKey = apiKeyManager.getGeminiKey()
        val euronKey = apiKeyManager.getEuronKey()

        binding.etClaudeKey.setText(if (claudeKey.isNotBlank()) "••••••••" else "")
        binding.etGeminiKey.setText(if (geminiKey.isNotBlank()) "••••••••" else "")
        binding.etEuronKey.setText(if (euronKey.isNotBlank()) "••••••••" else "")

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

        // Load language preference
        val language = prefs.getString("language", "en") ?: "en"
        when (language) {
            "en" -> binding.radioEnglish.isChecked = true
            "hi" -> binding.radioHindi.isChecked = true
        }
    }

    private fun saveSettings() {
        // Save API keys
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
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        if (userName.isNotBlank()) {
            prefs.edit().putString("user_name", userName).apply()
        }

        // Save language preference
        val selectedLanguage = when (binding.radioGroupLanguage.checkedRadioButtonId) {
            binding.radioEnglish.id -> "en"
            binding.radioHindi.id -> "hi"
            else -> "en"
        }
        prefs.edit().putString("language", selectedLanguage).apply()

        Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clearAllData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will delete:\n• All API keys\n• Meeting history\n• All settings\n\nContinue?")
            .setPositiveButton("Clear") { _, _ ->
                // Clear API keys
                apiKeyManager.clearAllKeys()

                // Clear preferences
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().clear().apply()

                // Clear database using lifecycleScope (FIXED)
                lifecycleScope.launch {
                    val db = MeetingDatabase.getDatabase(this@SettingsActivity)
                    db.meetingSummaryDao().deleteAll()
                    db.transcriptChunkDao().deleteAll()
                }

                Toast.makeText(this, "All data cleared ✅", Toast.LENGTH_SHORT).show()
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