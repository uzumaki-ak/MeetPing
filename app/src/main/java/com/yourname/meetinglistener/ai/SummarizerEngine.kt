package com.yourname.meetinglistener.ai

import android.util.Log
import com.yourname.meetinglistener.models.*

/**
 * SummarizerEngine.kt
 *
 * PURPOSE:
 * Implements hierarchical summarization strategy to prevent token overflow
 * Manages the compression of meeting content at different time scales
 *
 * STRATEGY:
 * - Raw transcripts → Micro summaries (every 5-10 minutes)
 * - Micro summaries → Section summaries (every 30 minutes)
 * - Keeps only recent raw transcripts, older content is compressed
 *
 * This ensures meetings can run for hours without context overflow
 */
class SummarizerEngine(private val llmManager: LLMManager) {

    private val TAG = "SummarizerEngine"

    companion object {
        // Time thresholds for summarization (in milliseconds)
        private const val MICRO_SUMMARY_INTERVAL = 5 * 60 * 1000L  // 5 minutes
        private const val SECTION_SUMMARY_INTERVAL = 30 * 60 * 1000L // 30 minutes
        private const val MAX_RECENT_TRANSCRIPTS = 20 // Keep last 20 chunks (~2-3 min)
    }

    /**
     * Process new transcript chunk and manage context
     * Triggers summarization if needed
     *
     * @param chunk New transcript chunk to add
     * @param context Current meeting context
     */
    suspend fun processTranscriptChunk(
        chunk: TranscriptChunk,
        context: MeetingContext
    ) {
        // Add chunk to recent transcripts
        context.recentTranscripts.add(chunk)

        Log.d(TAG, "Added transcript chunk. Total recent: ${context.recentTranscripts.size}")

        // Check if we need to create a micro summary
        if (shouldCreateMicroSummary(context)) {
            createMicroSummary(context)
        }

        // Check if we need to create a section summary
        if (shouldCreateSectionSummary(context)) {
            createSectionSummary(context)
        }

        // Trim old transcripts to prevent memory bloat
        trimOldTranscripts(context)
    }

    /**
     * Check if it's time to create a micro summary
     * Criteria: Has been 5+ minutes since last micro summary
     */
    private fun shouldCreateMicroSummary(context: MeetingContext): Boolean {
        if (context.recentTranscripts.size < 5) {
            return false // Need at least some content
        }

        val lastMicroTime = context.microSummaries.lastOrNull()?.endTime ?: context.startTime
        val currentTime = System.currentTimeMillis()

        return (currentTime - lastMicroTime) >= MICRO_SUMMARY_INTERVAL
    }

    /**
     * Create a micro summary from recent transcripts
     * Compresses last 5-10 minutes into 2-3 sentences
     */
    private suspend fun createMicroSummary(context: MeetingContext) {
        try {
            Log.d(TAG, "Creating micro summary...")

            // Get recent transcripts to summarize
            val transcriptsToSummarize = context.recentTranscripts
                .joinToString("\n") { "[${it.timestamp}] ${it.text}" }

            // Generate summary using LLM
            val summary = llmManager.generateSummary(
                SummaryRequest(
                    content = transcriptsToSummarize,
                    summaryType = SummaryType.MICRO,
                    maxLength = 100
                )
            )

            if (summary.isNotBlank() && !summary.startsWith("Error")) {
                val microSummary = MicroSummary(
                    summary = summary,
                    startTime = context.recentTranscripts.first().timestampMillis,
                    endTime = System.currentTimeMillis(),
                    topics = extractTopics(summary)
                )

                context.microSummaries.add(microSummary)
                Log.d(TAG, "Micro summary created: $summary")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating micro summary: ${e.message}", e)
        }
    }

    /**
     * Check if it's time to create a section summary
     * Criteria: Has been 30+ minutes since last section summary
     */
    private fun shouldCreateSectionSummary(context: MeetingContext): Boolean {
        if (context.microSummaries.size < 3) {
            return false // Need at least 3 micro summaries
        }

        val lastSectionTime = context.sectionSummaries.lastOrNull()
            ?.let { context.startTime + (context.durationMinutes * 60 * 1000) }
            ?: context.startTime

        val currentTime = System.currentTimeMillis()

        return (currentTime - lastSectionTime) >= SECTION_SUMMARY_INTERVAL
    }

    /**
     * Create a section summary from multiple micro summaries
     * Compresses 30 minutes into 1 sentence
     */
    private suspend fun createSectionSummary(context: MeetingContext) {
        try {
            Log.d(TAG, "Creating section summary...")

            // Take oldest 3 micro summaries to compress
            val microSummariesToCompress = context.microSummaries.take(3)
            val contentToCompress = microSummariesToCompress.joinToString("\n") { it.summary }

            // Generate compressed summary
            val sectionSummary = llmManager.generateSummary(
                SummaryRequest(
                    content = contentToCompress,
                    summaryType = SummaryType.SECTION,
                    maxLength = 50
                )
            )

            if (sectionSummary.isNotBlank() && !sectionSummary.startsWith("Error")) {
                context.sectionSummaries.add(sectionSummary)

                // Remove compressed micro summaries
                repeat(3) {
                    if (context.microSummaries.isNotEmpty()) {
                        context.microSummaries.removeAt(0)
                    }
                }

                Log.d(TAG, "Section summary created: $sectionSummary")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating section summary: ${e.message}", e)
        }
    }

    /**
     * Trim old transcript chunks to prevent memory overflow
     * Keeps only recent chunks, older ones are already summarized
     */
    private fun trimOldTranscripts(context: MeetingContext) {
        if (context.recentTranscripts.size > MAX_RECENT_TRANSCRIPTS) {
            val toRemove = context.recentTranscripts.size - MAX_RECENT_TRANSCRIPTS
            repeat(toRemove) {
                context.recentTranscripts.removeAt(0)
            }
            Log.d(TAG, "Trimmed $toRemove old transcript chunks")
        }
    }

    /**
     * Generate final meeting summary at the end
     * Combines all context into comprehensive summary
     */
    suspend fun generateFinalSummary(context: MeetingContext): String {
        try {
            Log.d(TAG, "Generating final meeting summary...")

            val fullContext = context.getCondensedContext()

            return llmManager.generateSummary(
                SummaryRequest(
                    content = fullContext,
                    summaryType = SummaryType.FINAL,
                    maxLength = 300
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error generating final summary: ${e.message}", e)
            return "Error generating final summary"
        }
    }

    /**
     * Extract key topics from summary text
     * Simple implementation using keyword detection
     */
    private fun extractTopics(summary: String): List<String> {
        // Simple topic extraction - can be improved with NLP
        val topics = mutableListOf<String>()

        val keywords = listOf(
            "deadline", "decision", "task", "project",
            "feature", "issue", "bug", "release", "meeting"
        )

        keywords.forEach { keyword ->
            if (summary.contains(keyword, ignoreCase = true)) {
                topics.add(keyword)
            }
        }

        return topics.distinct()
    }

    /**
     * Extract decisions from meeting context
     */
    suspend fun extractDecisions(context: MeetingContext): List<Decision> {
        try {
            val fullContext = context.getCondensedContext()

            val decisionsText = llmManager.generateSummary(
                SummaryRequest(
                    content = fullContext,
                    summaryType = SummaryType.DECISION
                )
            )

            // Parse decisions from text (simplified)
            val decisions = mutableListOf<Decision>()
            decisionsText.lines().forEach { line ->
                if (line.trim().isNotBlank()) {
                    decisions.add(
                        Decision(
                            description = line.trim().removePrefix("- ").removePrefix("• "),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            return decisions

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting decisions: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Extract action items from meeting context
     */
    suspend fun extractActionItems(context: MeetingContext): List<ActionItem> {
        try {
            val fullContext = context.getCondensedContext()

            val actionItemsText = llmManager.generateSummary(
                SummaryRequest(
                    content = fullContext,
                    summaryType = SummaryType.ACTION_ITEM
                )
            )

            // Parse action items from text (simplified)
            val actionItems = mutableListOf<ActionItem>()
            actionItemsText.lines().forEach { line ->
                if (line.contains("-") && line.contains("assigned", ignoreCase = true)) {
                    val parts = line.split("-")
                    if (parts.size >= 2) {
                        actionItems.add(
                            ActionItem(
                                task = parts[0].trim(),
                                assignee = parts[1].trim(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            return actionItems

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting action items: ${e.message}", e)
            return emptyList()
        }
    }
}