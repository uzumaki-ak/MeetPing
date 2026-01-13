package com.yourname.meetinglistener.ai
import com.yourname.meetinglistener.models.SummaryRequest
import com.yourname.meetinglistener.models.SummaryType
import android.util.Log
import com.yourname.meetinglistener.models.*
import com.yourname.meetinglistener.storage.entities.MeetingSummaryEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * MoMGenerator.kt
 *
 * PURPOSE:
 * Generates comprehensive Minutes of Meeting (MoM)
 * Extracts decisions, action items, and key points
 *
 * FEATURES:
 * - Professional MoM format
 * - Automatic decision extraction
 * - Action item identification
 * - Attendee tracking (when mentioned)
 */
class MoMGenerator(private val llmManager: LLMManager) {

    private val TAG = "MoMGenerator"

    /**
     * Generate complete MoM from meeting context
     */
    suspend fun generateMoM(context: MeetingContext): MeetingSummaryEntity {
        Log.d(TAG, "Generating MoM for meeting ${context.meetingId}")

        // Extract decisions
        val decisions = extractDecisions(context)

        // Extract action items
        val actionItems = extractActionItems(context)

        // Generate overall summary
        val summary = generateOverallSummary(context)

        // Create entity
        return MeetingSummaryEntity(
            meetingId = context.meetingId,
            startTime = context.startTime,
            endTime = System.currentTimeMillis(),
            durationMinutes = context.durationMinutes,
            userName = context.userName,
            finalSummary = summary,
            decisions = formatDecisions(decisions),
            actionItems = formatActionItems(actionItems),
            transcriptCount = context.recentTranscripts.size +
                    (context.microSummaries.size * 10) // Estimate
        )
    }

    /**
     * Extract decisions from meeting
     */
    private suspend fun extractDecisions(context: MeetingContext): List<Decision> {
        val prompt = buildString {
            appendLine("Extract all DECISIONS made in this meeting.")
            appendLine()
            appendLine("MEETING CONTEXT:")
            appendLine(context.getCondensedContext())
            appendLine()
            appendLine("Return ONLY a JSON array of decisions:")
            appendLine("[{\"decision\": \"description\", \"topic\": \"optional topic\"}]")
            appendLine()
            appendLine("If no decisions, return: []")
        }

        try {
            val response = llmManager.generateSummary(
                SummaryRequest(prompt, SummaryType.DECISION)
            )

            return parseDecisions(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting decisions: ${e.message}")
            return context.decisions // Fallback to tracked decisions
        }
    }

    /**
     * Extract action items from meeting
     */
    private suspend fun extractActionItems(context: MeetingContext): List<ActionItem> {
        val prompt = buildString {
            appendLine("Extract all ACTION ITEMS and TASKS from this meeting.")
            appendLine()
            appendLine("MEETING CONTEXT:")
            appendLine(context.getCondensedContext())
            appendLine()
            appendLine("Return ONLY a JSON array:")
            appendLine("[{\"task\": \"what to do\", \"assignee\": \"who\", \"deadline\": \"when or null\"}]")
            appendLine()
            appendLine("If no action items, return: []")
        }

        try {
            val response = llmManager.generateSummary(
                SummaryRequest(prompt, SummaryType.ACTION_ITEM)
            )

            return parseActionItems(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting action items: ${e.message}")
            return context.actionItems // Fallback
        }
    }

    /**
     * Generate overall meeting summary
     */
    private suspend fun generateOverallSummary(context: MeetingContext): String {
        val prompt = buildString {
            appendLine("Create a professional meeting summary (3-5 sentences).")
            appendLine()
            appendLine("MEETING CONTEXT:")
            appendLine(context.getCondensedContext())
            appendLine()
            appendLine("Focus on:")
            appendLine("- Main topics discussed")
            appendLine("- Key outcomes")
            appendLine("- Overall purpose/goal")
            appendLine()
            appendLine("Write in professional, concise language.")
        }

        return try {
            llmManager.generateSummary(
                SummaryRequest(prompt, SummaryType.FINAL)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating summary: ${e.message}")
            "Meeting summary generation failed"
        }
    }

    /**
     * Parse decisions from JSON
     */
    private fun parseDecisions(json: String): List<Decision> {
        return try {
            val cleaned = json.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val array = JSONArray(cleaned)
            val decisions = mutableListOf<Decision>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                decisions.add(
                    Decision(
                        description = obj.getString("decision"),
                        timestamp = System.currentTimeMillis(),
                        relatedTopic = obj.optString("topic", null)
                    )
                )
            }

            decisions
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing decisions JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse action items from JSON
     */
    private fun parseActionItems(json: String): List<ActionItem> {
        return try {
            val cleaned = json.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val array = JSONArray(cleaned)
            val items = mutableListOf<ActionItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    ActionItem(
                        task = obj.getString("task"),
                        assignee = obj.getString("assignee"),
                        deadline = obj.optString("deadline", null),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            items
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing action items JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Format decisions for storage
     */
    private fun formatDecisions(decisions: List<Decision>): String {
        if (decisions.isEmpty()) return "No decisions made"

        return decisions.joinToString("\n") { decision ->
            "• ${decision.description}" +
                    if (decision.relatedTopic != null) " (${decision.relatedTopic})" else ""
        }
    }

    /**
     * Format action items for storage
     */
    private fun formatActionItems(items: List<ActionItem>): String {
        if (items.isEmpty()) return "No action items"

        return items.joinToString("\n") { item ->
            "• ${item.task} - Assigned to: ${item.assignee}" +
                    if (item.deadline != null) " - Due: ${item.deadline}" else ""
        }
    }
}