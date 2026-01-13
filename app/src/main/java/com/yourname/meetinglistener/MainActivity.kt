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
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourname.meetinglistener.ai.LLMManager
import com.yourname.meetinglistener.databinding.ActivityMainBinding
import com.yourname.meetinglistener.services.AudioCaptureService
import com.yourname.meetinglistener.storage.ContextManager
import com.yourname.meetinglistener.ui.BubbleOverlayService
import com.yourname.meetinglistener.ui.MeetingHistoryActivity
import com.yourname.meetinglistener.ui.SettingsActivity
import com.yourname.meetinglistener.ui.TranscriptAdapter
import com.yourname.meetinglistener.utils.PermissionsHelper
import kotlinx.coroutines.delay
import android.util.Log
import kotlinx.coroutines.launch

/**
 * MainActivity.kt (COMPLETE VERSION)
 *
 * NEW FEATURES:
 * - Real-time transcript display
 * - Proper duration tracking
 * - Meeting history access
 * - Better UI feedback
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionsHelper: PermissionsHelper
    private lateinit var contextManager: ContextManager
    private lateinit var llmManager: LLMManager
    private lateinit var prefs: SharedPreferences

    // Transcript adapter for RecyclerView
    private lateinit var transcriptAdapter: TranscriptAdapter

    private var isMeetingActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionsHelper = PermissionsHelper(this)
        contextManager = ContextManager.getInstance()
        llmManager = LLMManager(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setupUI()
        checkPermissions()
        startUIUpdateLoop()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        // Setup transcript RecyclerView
        transcriptAdapter = TranscriptAdapter()
        binding.rvTranscripts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = transcriptAdapter
        }

        binding.btnStartStop.setOnClickListener {
            if (isMeetingActive) {
                stopMeeting()
            } else {
                startMeeting()
            }
        }

        binding.btnAskQuestion.setOnClickListener {
            showQuestionDialog()
        }

        binding.btnClearTranscripts.setOnClickListener {
            transcriptAdapter.clear()
        }

        binding.btnAskQuestion.isEnabled = false
        updateUI()
    }

    private fun checkPermissions() {
        if (!permissionsHelper.hasAllPermissions()) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Microphone and notification permissions needed")
                .setPositiveButton("Grant") { _, _ ->
                    permissionsHelper.requestPermissions()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        if (!permissionsHelper.hasOverlayPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage("Allow floating bubble over other apps?")
                .setPositiveButton("Allow") { _, _ ->
                    permissionsHelper.requestOverlayPermission()
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    private fun startMeeting() {
        if (!permissionsHelper.hasAudioPermission()) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            permissionsHelper.requestPermissions()
            return
        }

        if (!llmManager.hasAvailableProvider()) {
            AlertDialog.Builder(this)
                .setTitle("API Key Required")
                .setMessage("Add at least one LLM API key in Settings")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        var userName = prefs.getString("user_name", null)

        if (userName.isNullOrBlank()) {
            val input = android.widget.EditText(this)
            input.hint = "Enter your name"

            AlertDialog.Builder(this)
                .setTitle("What's your name?")
                .setMessage("Used to detect when you're mentioned")
                .setView(input)
                .setPositiveButton("Start") { _, _ ->
                    userName = input.text.toString().trim()
                    if (!userName.isNullOrBlank()) {
                        prefs.edit().putString("user_name", userName).apply()
                        startMeetingWithName(userName!!)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            startMeetingWithName(userName)
        }
    }

    private fun startMeetingWithName(userName: String) {
        // Get language preference
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val language = prefs.getString("language", "en") ?: "en"

        // Start audio capture service
        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_USER_NAME, userName)
            putExtra(AudioCaptureService.EXTRA_LANGUAGE, language) // NEW
        }
        startService(serviceIntent)

        // Start bubble overlay
        if (permissionsHelper.hasOverlayPermission()) {
            val bubbleIntent = Intent(this, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_SHOW
            }
            startService(bubbleIntent)
        }

        isMeetingActive = true
        transcriptAdapter.clear()
        updateUI()

        val langName = if (language == "hi") "Hindi" else "English"
        Toast.makeText(this, "Meeting started in $langName! Speak clearly 🎤", Toast.LENGTH_SHORT).show()
    }

    private fun stopMeeting() {
        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        startService(serviceIntent)

        val bubbleIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = BubbleOverlayService.ACTION_HIDE
        }
        startService(bubbleIntent)

        isMeetingActive = false
        updateUI()

        showFinalSummaryDialog()

        Toast.makeText(this, "Meeting stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showQuestionDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Ask about the meeting..."
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

    private fun answerQuestion(question: String) {
        binding.tvAnswerLabel.text = "Thinking..."
        binding.tvAnswer.text = "Processing..."

        lifecycleScope.launch {
            try {
                val context = contextManager.getCondensedContext()
                val response = llmManager.answerQuestion(question, context)

                if (response.success) {
                    binding.tvAnswerLabel.text = "Answer (${response.provider}):"
                    binding.tvAnswer.text = response.content
                } else {
                    binding.tvAnswerLabel.text = "Error:"
                    binding.tvAnswer.text = response.errorMessage ?: "Failed"
                }

            } catch (e: Exception) {
                binding.tvAnswerLabel.text = "Error:"
                binding.tvAnswer.text = "Exception: ${e.message}"
            }
        }
    }

    private fun showFinalSummaryDialog() {
        lifecycleScope.launch {
            // Wait for MoM generation
            delay(3000) // Give service time to save

            // Load from database
            try {
                val database = com.yourname.meetinglistener.storage.MeetingDatabase.getDatabase(this@MainActivity)
                val allMeetings = database.meetingSummaryDao().getAllSummaries()

                if (allMeetings.isEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Meeting Too Short")
                        .setMessage("No transcripts were captured. The meeting may have been too short or there were microphone issues.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                // Get most recent meeting
                val latestMeeting = allMeetings.first()

                val summaryText = buildString {
                    appendLine("⏱️ Duration: ${latestMeeting.durationMinutes} minutes")
                    appendLine("📝 Transcripts: ${latestMeeting.transcriptCount} segments")
                    appendLine()
                    appendLine("📋 Summary:")
                    appendLine(latestMeeting.finalSummary.take(200))
                    if (latestMeeting.finalSummary.length > 200) {
                        appendLine("...")
                    }
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("✅ Meeting Saved!")
                    .setMessage(summaryText)
                    .setPositiveButton("View Details") { _, _ ->
                        startActivity(Intent(this@MainActivity, com.yourname.meetinglistener.ui.MeetingHistoryActivity::class.java))
                    }
                    .setNegativeButton("Close", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading meeting: ${e.message}")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Meeting Ended")
                    .setMessage("Meeting was saved. Check Meeting History.")
                    .setPositiveButton("View History") { _, _ ->
                        startActivity(Intent(this@MainActivity, com.yourname.meetinglistener.ui.MeetingHistoryActivity::class.java))
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun updateUI() {
        if (isMeetingActive) {
            binding.btnStartStop.text = "Stop Meeting"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            binding.btnAskQuestion.isEnabled = true
            binding.tvStatus.text = "🔴 Meeting Active"
        } else {
            binding.btnStartStop.text = "Start Meeting"
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            binding.btnAskQuestion.isEnabled = false
            binding.tvStatus.text = "⚪ No Active Meeting"
        }

        val stats = contextManager.getMeetingStats()
        binding.tvDuration.text = "⏱️ ${stats.durationMinutes} min"
        binding.tvTranscripts.text = "📝 ${stats.transcriptChunks} segments"
        binding.tvDecisions.text = "✅ ${stats.decisions} decisions"
        binding.tvActionItems.text = "📋 ${stats.actionItems} actions"

        // Update transcript list
        if (isMeetingActive) {
            val meeting = contextManager.getActiveMeeting()
            meeting?.let {
                transcriptAdapter.updateTranscripts(it.recentTranscripts)
            }
        }
    }

    private fun startUIUpdateLoop() {
        lifecycleScope.launch {
            while (true) {
                delay(2000) // Update every 2 seconds
                if (isMeetingActive) {
                    // Force duration recalculation
                    contextManager.updateDuration()
                    updateUI()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, MeetingHistoryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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