package com.yourname.meetinglistener.ai

import android.util.Log
import com.yourname.meetinglistener.models.*
import com.yourname.meetinglistener.storage.entities.MeetingSummaryEntity

/**
 * MoMGenerator.kt (FIXED - ACTUALLY WORKS NOW)
 *
 * FIXES:
 * - Simplified decision/action extraction
 * - Better parsing
 * - Fallback to manual extraction
 */
class MoMGenerator(private val llmManager: LLMManager) {

    private val TAG = "MoMGenerator"

    suspend fun generateMoM(context: MeetingContext): MeetingSummaryEntity {
        Log.d(TAG, "Generating MoM for meeting ${context.meetingId}")

        // Use simple extraction from existing context first
        val decisions = extractDecisionsSimple(context)
        val actionItems = extractActionItemsSimple(context)

        // Generate summary
        val summary = generateSimpleSummary(context)

        Log.d(TAG, "MoM generated: ${decisions.size} decisions, ${actionItems.size} action items")

        return MeetingSummaryEntity(
            meetingId = context.meetingId,
            startTime = context.startTime,
            endTime = System.currentTimeMillis(),
            durationMinutes = context.durationMinutes,
            userName = context.userName,
            finalSummary = summary,
            decisions = formatDecisions(decisions),
            actionItems = formatActionItems(actionItems),
            transcriptCount = context.recentTranscripts.size
        )
    }

    /**
     * Simple decision extraction using keywords
     */
    private fun extractDecisionsSimple(context: MeetingContext): List<Decision> {
        val decisions = mutableListOf<Decision>()

        // First, use tracked decisions
        decisions.addAll(context.decisions)

        // Then scan transcripts for decision keywords
        val decisionKeywords = listOf(
            "decided", "decide", "decision", "agreed", "agree",
            "will do", "going to", "confirmed", "finalized"
        )

        context.recentTranscripts.forEach { chunk ->
            decisionKeywords.forEach { keyword ->
                if (chunk.text.contains(keyword, ignoreCase = true)) {
                    val decision = Decision(
                        description = chunk.text,
                        timestamp = chunk.timestampMillis
                    )
                    decisions.add(decision)
                }
            }
        }

        return decisions.distinctBy { it.description }
    }

    /**
     * Simple action item extraction using keywords
     */
    private fun extractActionItemsSimple(context: MeetingContext): List<ActionItem> {
        val actionItems = mutableListOf<ActionItem>()

        // Use tracked action items
        actionItems.addAll(context.actionItems)

        // Scan for action keywords
        val actionKeywords = listOf(
            "will", "should", "need to", "have to", "must",
            "task", "assign", "responsible", "deadline", "by"
        )

        context.recentTranscripts.forEach { chunk ->
            actionKeywords.forEach { keyword ->
                if (chunk.text.contains(keyword, ignoreCase = true)) {
                    // Try to extract assignee (name after "assigned to" or similar)
                    val assignee = extractAssignee(chunk.text, context.userName)

                    val actionItem = ActionItem(
                        task = chunk.text,
                        assignee = assignee,
                        timestamp = chunk.timestampMillis
                    )
                    actionItems.add(actionItem)
                }
            }
        }

        return actionItems.distinctBy { it.task }
    }

    /**
     * Extract assignee from text
     */
    private fun extractAssignee(text: String, defaultName: String): String {
        // Look for patterns like "assigned to X", "X will do", etc.
        val patterns = listOf(
            Regex("assigned to ([A-Za-z]+)", RegexOption.IGNORE_CASE),
            Regex("([A-Za-z]+) will", RegexOption.IGNORE_CASE),
            Regex("([A-Za-z]+) should", RegexOption.IGNORE_CASE)
        )

        patterns.forEach { pattern ->
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        return defaultName // Default to user
    }

    /**
     * Generate simple summary
     */
    private suspend fun generateSimpleSummary(context: MeetingContext): String {
        // If meeting is very short, just use transcripts
        if (context.durationMinutes < 2) {
            return "Brief meeting covering: ${context.recentTranscripts.take(3).joinToString(", ") { it.text.take(50) }}"
        }

        // Try to get AI summary
        return try {
            val contextText = context.getCondensedContext()
            val prompt = """
Summarize this meeting in 3-4 sentences. Be concise and focus on main points.

$contextText

Summary:
            """.trimIndent()

            llmManager.generateSummary(
                SummaryRequest(prompt, SummaryType.FINAL, maxLength = 200)
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed: ${e.message}")
            // Fallback to simple summary
            buildString {
                append("Meeting lasted ${context.durationMinutes} minutes. ")
                if (context.recentTranscripts.isNotEmpty()) {
                    append("Topics: ${context.recentTranscripts.take(5).joinToString(", ") { it.text.take(30) }}.")
                }
            }
        }
    }

    private fun formatDecisions(decisions: List<Decision>): String {
        if (decisions.isEmpty()) return "No decisions made"

        return decisions.take(10).joinToString("\n\n") { decision ->
            "• ${decision.description.take(200)}"
        }
    }

    private fun formatActionItems(items: List<ActionItem>): String {
        if (items.isEmpty()) return "No action items"

        return items.take(10).joinToString("\n\n") { item ->
            "• ${item.task.take(150)} → ${item.assignee}"
        }
    }
}