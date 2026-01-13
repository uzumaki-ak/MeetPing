package com.yourname.meetinglistener.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourname.meetinglistener.ai.LLMManager
import com.yourname.meetinglistener.databinding.ActivityMeetingHistoryBinding
import com.yourname.meetinglistener.storage.MeetingDatabase
import com.yourname.meetinglistener.storage.entities.MeetingSummaryEntity
import kotlinx.coroutines.launch

/**
 * MeetingHistoryActivity.kt
 *
 * PURPOSE:
 * Shows list of past meetings
 * Allows viewing details and asking questions about past meetings
 */
class MeetingHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingHistoryBinding
    private lateinit var database: MeetingDatabase
    private lateinit var adapter: MeetingHistoryAdapter
    private lateinit var llmManager: LLMManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMeetingHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Meeting History"

        database = MeetingDatabase.getDatabase(this)
        llmManager = LLMManager(this)

        setupRecyclerView()
        loadMeetings()
    }

    private fun setupRecyclerView() {
        adapter = MeetingHistoryAdapter(
            onItemClick = { meeting -> showMeetingDetails(meeting) },
            onQueryClick = { meeting -> showQueryDialog(meeting) },
            onDeleteClick = { meeting -> deleteMeeting(meeting) }
        )

        binding.rvMeetings.layoutManager = LinearLayoutManager(this)
        binding.rvMeetings.adapter = adapter
    }

    private fun loadMeetings() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            val meetings = database.meetingSummaryDao().getAllSummaries()

            binding.progressBar.visibility = View.GONE

            if (meetings.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvMeetings.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvMeetings.visibility = View.VISIBLE
                adapter.submitList(meetings)
            }
        }
    }

    private fun showMeetingDetails(meeting: MeetingSummaryEntity) {
        val details = buildString {
            appendLine("📅 Date: ${formatDate(meeting.startTime)}")
            appendLine("⏱️ Duration: ${meeting.durationMinutes} minutes")
            appendLine("👤 Participant: ${meeting.userName}")
            appendLine()
            appendLine("📝 SUMMARY:")
            appendLine(meeting.finalSummary)
            appendLine()
            appendLine("✅ DECISIONS:")
            appendLine(meeting.decisions)
            appendLine()
            appendLine("📋 ACTION ITEMS:")
            appendLine(meeting.actionItems)
        }

        AlertDialog.Builder(this)
            .setTitle("Meeting Details")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .setNeutralButton("Ask Question") { _, _ ->
                showQueryDialog(meeting)
            }
            .show()
    }

    private fun showQueryDialog(meeting: MeetingSummaryEntity) {
        val input = android.widget.EditText(this)
        input.hint = "Ask about this meeting..."
        input.minLines = 2

        val dialog = AlertDialog.Builder(this)
            .setTitle("Query Past Meeting")
            .setMessage("Ask anything about the meeting from ${formatDate(meeting.startTime)}")
            .setView(input)
            .setPositiveButton("Ask") { _, _ ->
                val question = input.text.toString().trim()
                if (question.isNotBlank()) {
                    queryMeeting(meeting, question)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun queryMeeting(meeting: MeetingSummaryEntity, question: String) {
        lifecycleScope.launch {
            // Build context from meeting
            val context = buildString {
                appendLine("MEETING CONTEXT:")
                appendLine("Date: ${formatDate(meeting.startTime)}")
                appendLine("Duration: ${meeting.durationMinutes} minutes")
                appendLine()
                appendLine("SUMMARY:")
                appendLine(meeting.finalSummary)
                appendLine()
                appendLine("DECISIONS:")
                appendLine(meeting.decisions)
                appendLine()
                appendLine("ACTION ITEMS:")
                appendLine(meeting.actionItems)
            }

            // Show loading
            val loadingDialog = AlertDialog.Builder(this@MeetingHistoryActivity)
                .setTitle("Thinking...")
                .setMessage("Processing your question...")
                .setCancelable(false)
                .create()
            loadingDialog.show()

            try {
                val response = llmManager.answerQuestion(question, context)

                loadingDialog.dismiss()

                AlertDialog.Builder(this@MeetingHistoryActivity)
                    .setTitle("Answer (${response.provider})")
                    .setMessage(if (response.success) response.content else "Error: ${response.errorMessage}")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                loadingDialog.dismiss()

                AlertDialog.Builder(this@MeetingHistoryActivity)
                    .setTitle("Error")
                    .setMessage("Failed to get answer: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun deleteMeeting(meeting: MeetingSummaryEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Meeting?")
            .setMessage("This cannot be undone")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    database.meetingSummaryDao().deleteSummary(meeting.meetingId)
                    database.transcriptChunkDao().deleteChunksForMeeting(meeting.meetingId)
                    loadMeetings()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}