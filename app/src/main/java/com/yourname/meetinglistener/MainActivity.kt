package com.yourname.meetinglistener

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yourname.meetinglistener.ai.LLMManager
import com.yourname.meetinglistener.databinding.ActivityMainBinding
import com.yourname.meetinglistener.services.AudioCaptureService
import com.yourname.meetinglistener.storage.ContextManager
import com.yourname.meetinglistener.ui.BubbleOverlayService
import com.yourname.meetinglistener.ui.SettingsActivity
import com.yourname.meetinglistener.utils.PermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity.kt
 *
 * PURPOSE:
 * Main entry point and control center for the app
 * Manages meeting start/stop, permissions, and user interactions
 *
 * FEATURES:
 * - Start/Stop meeting controls
 * - Real-time meeting statistics display
 * - Question asking interface
 * - Settings access
 * - Permission handling
 *
 * UI COMPONENTS:
 * - Start/Stop meeting button
 * - Meeting status display
 * - Question input field
 * - Meeting stats (duration, decisions, action items)
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    // View binding
    private lateinit var binding: ActivityMainBinding

    // Helpers and managers
    private lateinit var permissionsHelper: PermissionsHelper
    private lateinit var contextManager: ContextManager
    private lateinit var llmManager: LLMManager

    // SharedPreferences for user name
    private lateinit var prefs: SharedPreferences

    // Meeting state
    private var isMeetingActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        permissionsHelper = PermissionsHelper(this)
        contextManager = ContextManager.getInstance()
        llmManager = LLMManager(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Setup UI
        setupUI()
        checkPermissions()

        // Update UI periodically
        startUIUpdateLoop()
    }

    /**
     * Setup UI components and click listeners
     */
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        // Start/Stop meeting button
        binding.btnStartStop.setOnClickListener {
            if (isMeetingActive) {
                stopMeeting()
            } else {
                startMeeting()
            }
        }

        // Ask question button
        binding.btnAskQuestion.setOnClickListener {
            showQuestionDialog()
        }

        // Initially disable question button
        binding.btnAskQuestion.isEnabled = false

        updateUI()
    }

    /**
     * Check and request necessary permissions
     */
    private fun checkPermissions() {
        if (!permissionsHelper.hasAllPermissions()) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app needs microphone and notification permissions to work.")
                .setPositiveButton("Grant") { _, _ ->
                    permissionsHelper.requestPermissions()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Check overlay permission for bubble
        if (!permissionsHelper.hasOverlayPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage("Allow app to display floating bubble over other apps?")
                .setPositiveButton("Allow") { _, _ ->
                    permissionsHelper.requestOverlayPermission()
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    /**
     * Start meeting session
     */
    private fun startMeeting() {
        // Check permissions first
        if (!permissionsHelper.hasAudioPermission()) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            permissionsHelper.requestPermissions()
            return
        }

        // Check if LLM is configured
        if (!llmManager.hasAvailableProvider()) {
            AlertDialog.Builder(this)
                .setTitle("API Key Required")
                .setMessage("Please add at least one LLM API key in Settings first.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Get user's name
        var userName = prefs.getString("user_name", null)

        if (userName.isNullOrBlank()) {
            // Ask for user's name
            val input = android.widget.EditText(this)
            input.hint = "Enter your name"

            AlertDialog.Builder(this)
                .setTitle("What's your name?")
                .setMessage("Your name will be used to detect when you're mentioned in the meeting.")
                .setView(input)
                .setPositiveButton("Start") { _, _ ->
                    userName = input.text.toString().trim()
                    if (userName.isNotBlank()) {
                        prefs.edit().putString("user_name", userName).apply()
                        startMeetingWithName(userName)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            startMeetingWithName(userName)
        }
    }

    /**
     * Actually start the meeting with user name
     */
    private fun startMeetingWithName(userName: String) {
        // Start audio capture service
        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_USER_NAME, userName)
        }
        startService(serviceIntent)

        // Start bubble overlay if permission granted
        if (permissionsHelper.hasOverlayPermission()) {
            val bubbleIntent = Intent(this, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_SHOW
            }
            startService(bubbleIntent)
        }

        isMeetingActive = true
        updateUI()

        Toast.makeText(this, "Meeting started. Listening...", Toast.LENGTH_SHORT).show()
    }

    /**
     * Stop meeting session
     */
    private fun stopMeeting() {
        // Stop audio capture service
        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        startService(serviceIntent)

        // Hide bubble
        val bubbleIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE
        }
        startService(bubbleIntent)

        isMeetingActive = false
        updateUI()

        // Show final summary dialog
        showFinalSummaryDialog()

        Toast.makeText(this, "Meeting stopped", Toast.LENGTH_SHORT).show()
    }

    /**
     * Show question dialog
     */
    private fun showQuestionDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Ask a question about the meeting..."
        input.setSingleLine(false)
        input.minLines = 3

        AlertDialog.Builder(this)
            .setTitle("Ask a Question")
            .setView(input)
            .setPositiveButton("Ask") { _, _ ->
                val question = input.text.toString().trim()
                if (question.isNotBlank()) {
                    answerQuestion(question)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Answer user's question using LLM
     */
    private fun answerQuestion(question: String) {
        // Show progress
        binding.tvAnswerLabel.text = "Thinking..."
        binding.tvAnswer.text = "Processing your question..."

        lifecycleScope.launch {
            try {
                // Get context from ContextManager
                val context = contextManager.getCondensedContext()

                // Ask LLM
                val response = llmManager.answerQuestion(question, context)

                if (response.success) {
                    binding.tvAnswerLabel.text = "Answer (${response.provider}):"
                    binding.tvAnswer.text = response.content
                } else {
                    binding.tvAnswerLabel.text = "Error:"
                    binding.tvAnswer.text = response.errorMessage ?: "Failed to get answer"
                }

            } catch (e: Exception) {
                binding.tvAnswerLabel.text = "Error:"
                binding.tvAnswer.text = "Exception: ${e.message}"
            }
        }
    }

    /**
     * Show final summary dialog when meeting ends
     */
    private fun showFinalSummaryDialog() {
        lifecycleScope.launch {
            // Wait a moment for final processing
            delay(1000)

            // Get final summary from context
            val stats = contextManager.getMeetingStats()

            val summaryText = buildString {
                appendLine("Meeting Duration: ${stats.durationMinutes} minutes")
                appendLine("Decisions Made: ${stats.decisions}")
                appendLine("Action Items: ${stats.actionItems}")
                appendLine("\nFull summary available in meeting history.")
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Meeting Ended")
                .setMessage(summaryText)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Update UI based on current state
     */
    private fun updateUI() {
        if (isMeetingActive) {
            binding.btnStartStop.text = "Stop Meeting"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            binding.btnAskQuestion.isEnabled = true
            binding.tvStatus.text = "Meeting Active - Listening..."
        } else {
            binding.btnStartStop.text = "Start Meeting"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            binding.btnAskQuestion.isEnabled = false
            binding.tvStatus.text = "No Active Meeting"
        }

        // Update stats
        val stats = contextManager.getMeetingStats()
        binding.tvDuration.text = "Duration: ${stats.durationMinutes} min"
        binding.tvDecisions.text = "Decisions: ${stats.decisions}"
        binding.tvActionItems.text = "Action Items: ${stats.actionItems}"
    }

    /**
     * Start UI update loop
     * Updates stats every 5 seconds while meeting is active
     */
    private fun startUIUpdateLoop() {
        lifecycleScope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                if (isMeetingActive) {
                    updateUI()
                }
            }
        }
    }

    /**
     * Create options menu
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Handle menu item clicks
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_history -> {
                // TODO: Open meeting history
                Toast.makeText(this, "Meeting history coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionsHelper.PERMISSION_REQUEST_CODE) {
            if (permissionsHelper.hasAllPermissions()) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}