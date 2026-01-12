package com.yourname.meetinglistener.models

/**
 * MeetingContext.kt
 *
 * PURPOSE:
 * Represents the live context/memory of an ongoing meeting.
 * Stores hierarchical summaries to maintain context without token overflow.
 *
 * STRUCTURE:
 * - Raw transcript chunks (recent only)
 * - Micro summaries (last 5-10 minutes)
 * - Section summaries (compressed older content)
 * - Key decisions and action items
 *
 * This enables answering questions without sending entire meeting history.
 */
data class MeetingContext(
    val meetingId: String,              // Unique ID for this meeting session
    val startTime: Long,                // Unix timestamp when meeting started
    val userName: String,               // User's name for detection

    // Recent transcript chunks (last 2-3 minutes of raw text)
    val recentTranscripts: MutableList<TranscriptChunk> = mutableListOf(),

    // Micro summaries (5-10 minute segments)
    val microSummaries: MutableList<MicroSummary> = mutableListOf(),

    // Section summaries (compressed older content)
    val sectionSummaries: MutableList<String> = mutableListOf(),

    // Key decisions made during meeting
    val decisions: MutableList<Decision> = mutableListOf(),

    // Action items and tasks assigned
    val actionItems: MutableList<ActionItem> = mutableListOf(),

    // Current topic being discussed
    var currentTopic: String? = null,

    // Total duration in minutes
    var durationMinutes: Int = 0
) {
    /**
     * Get condensed context for LLM query
     * Returns only relevant information to stay within token limits
     */
    fun getCondensedContext(): String {
        val sb = StringBuilder()

        // Add meeting metadata
        sb.appendLine("Meeting Duration: $durationMinutes minutes")
        if (currentTopic != null) {
            sb.appendLine("Current Topic: $currentTopic")
        }
        sb.appendLine()

        // Add recent transcripts (last 2-3 minutes)
        if (recentTranscripts.isNotEmpty()) {
            sb.appendLine("=== Recent Conversation ===")
            recentTranscripts.takeLast(10).forEach { chunk ->
                sb.appendLine("[${chunk.timestamp}] ${chunk.text}")
            }
            sb.appendLine()
        }

        // Add micro summaries (last 10-15 minutes compressed)
        if (microSummaries.isNotEmpty()) {
            sb.appendLine("=== Previous Discussion Summaries ===")
            microSummaries.takeLast(3).forEach { summary ->
                sb.appendLine("• ${summary.summary}")
            }
            sb.appendLine()
        }

        // Add section summaries (older content, highly compressed)
        if (sectionSummaries.isNotEmpty()) {
            sb.appendLine("=== Earlier Meeting Highlights ===")
            sectionSummaries.forEach { summary ->
                sb.appendLine("• $summary")
            }
            sb.appendLine()
        }

        // Add decisions made
        if (decisions.isNotEmpty()) {
            sb.appendLine("=== Decisions Made ===")
            decisions.forEach { decision ->
                sb.appendLine("• ${decision.description}")
            }
            sb.appendLine()
        }

        // Add action items
        if (actionItems.isNotEmpty()) {
            sb.appendLine("=== Action Items ===")
            actionItems.forEach { item ->
                sb.appendLine("• ${item.task} (Assigned to: ${item.assignee})")
            }
        }

        return sb.toString()
    }

    /**
     * Check if user's name was mentioned in recent context
     */
    fun wasUserMentioned(): Boolean {
        return recentTranscripts.any {
            it.text.contains(userName, ignoreCase = true)
        }
    }
}

/**
 * TranscriptChunk
 *
 * Represents a small segment of transcribed speech (5-15 seconds)
 */
data class TranscriptChunk(
    val text: String,                   // Transcribed text
    val timestamp: String,              // Readable timestamp (HH:MM:SS)
    val timestampMillis: Long,          // Unix timestamp
    val speakerInfo: String? = null     // Optional speaker identification
)

/**
 * MicroSummary
 *
 * A compressed summary of 5-10 minutes of conversation
 */
data class MicroSummary(
    val summary: String,                // Summary text
    val startTime: Long,                // When this segment started
    val endTime: Long,                  // When this segment ended
    val topics: List<String> = listOf() // Topics discussed in this segment
)

/**
 * Decision
 *
 * Represents a decision made during the meeting
 */
data class Decision(
    val description: String,            // What was decided
    val timestamp: Long,                // When decision was made
    val relatedTopic: String? = null    // Related topic if any
)

/**
 * ActionItem
 *
 * Represents a task or action item assigned during meeting
 */
data class ActionItem(
    val task: String,                   // What needs to be done
    val assignee: String,               // Who is responsible
    val deadline: String? = null,       // Optional deadline
    val timestamp: Long                 // When it was mentioned
)

// ==========================================
// QuestionAnswer.kt
// ==========================================

/**
 * QuestionAnswer
 *
 * PURPOSE:
 * Stores a user's question and the AI's answer for history/reference
 */
data class QuestionAnswer(
    val id: String,                     // Unique ID
    val meetingId: String,              // Associated meeting session
    val question: String,               // User's question
    val answer: String,                 // AI-generated answer
    val timestamp: Long,                // When question was asked
    val provider: String,               // Which LLM answered (claude/gemini/euron)
    val contextUsed: String             // Snapshot of context used for answering
)

// ==========================================
// ApiConfig.kt
// ==========================================

/**
 * ApiConfig
 *
 * PURPOSE:
 * Configuration data class for LLM API settings
 */
data class ApiConfig(
    val apiKey: String,                 // API key for authentication
    val model: String,                  // Model name (e.g., "claude-3-5-sonnet-20241022")
    val maxTokens: Int = 1000,          // Maximum response tokens
    val temperature: Double = 0.7       // Response randomness (0.0-1.0)
)

// ==========================================
// LLMResponse.kt
// ==========================================

/**
 * LLMResponse
 *
 * PURPOSE:
 * Standardized response wrapper for all LLM providers
 * Makes it easy to switch between Claude, Gemini, Euron
 */
data class LLMResponse(
    val content: String,                // The actual response text
    val provider: String,               // Which provider generated this
    val success: Boolean,               // Whether request succeeded
    val errorMessage: String? = null,   // Error details if failed
    val tokensUsed: Int? = null,        // Optional token usage info
    val latencyMs: Long? = null         // Optional latency tracking
)

// ==========================================
// SummaryRequest.kt
// ==========================================

/**
 * SummaryRequest
 *
 * PURPOSE:
 * Input for generating different types of summaries
 */
data class SummaryRequest(
    val content: String,                // Content to summarize
    val summaryType: SummaryType,       // What kind of summary
    val maxLength: Int = 150            // Maximum summary length in words
)

enum class SummaryType {
    MICRO,          // 5-10 minute segment summary
    SECTION,        // Older content compression
    FINAL,          // End-of-meeting summary
    DECISION,       // Extract decisions only
    ACTION_ITEM     // Extract action items only
}