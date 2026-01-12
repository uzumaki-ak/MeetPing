package com.yourname.meetinglistener.storage

import android.content.Context
import android.util.Log
import com.yourname.meetinglistener.models.*
import java.util.*

/**
 * ContextManager.kt
 *
 * PURPOSE:
 * Manages the in-memory meeting context during active meetings
 * Provides thread-safe access to meeting state
 * Coordinates between audio capture, transcription, and AI processing
 *
 * FEATURES:
 * - Singleton pattern for global access
 * - Thread-safe operations
 * - Active meeting state management
 * - Context retrieval for question answering
 */
class ContextManager private constructor() {

    private val TAG = "ContextManager"

    // Current active meeting context (null when no meeting is active)
    private var activeMeetingContext: MeetingContext? = null

    // Lock for thread-safe operations
    private val lock = Any()

    companion object {
        @Volatile
        private var instance: ContextManager? = null

        /**
         * Get singleton instance of ContextManager
         */
        fun getInstance(): ContextManager {
            return instance ?: synchronized(this) {
                instance ?: ContextManager().also { instance = it }
            }
        }
    }

    /**
     * Start a new meeting session
     * Creates new MeetingContext and marks meeting as active
     *
     * @param userName User's name for mention detection
     * @return The newly created MeetingContext
     */
    fun startMeeting(userName: String): MeetingContext {
        synchronized(lock) {
            // End any existing meeting first
            endMeeting()

            val meetingId = UUID.randomUUID().toString()
            val newContext = MeetingContext(
                meetingId = meetingId,
                startTime = System.currentTimeMillis(),
                userName = userName
            )

            activeMeetingContext = newContext
            Log.d(TAG, "Started new meeting: $meetingId")

            return newContext
        }
    }

    /**
     * Get current active meeting context
     * @return Active MeetingContext or null if no meeting active
     */
    fun getActiveMeeting(): MeetingContext? {
        synchronized(lock) {
            return activeMeetingContext
        }
    }

    /**
     * Check if a meeting is currently active
     */
    fun isMeetingActive(): Boolean {
        synchronized(lock) {
            return activeMeetingContext != null
        }
    }

    /**
     * End the current meeting
     * Clears active context
     */
    fun endMeeting() {
        synchronized(lock) {
            if (activeMeetingContext != null) {
                Log.d(TAG, "Ending meeting: ${activeMeetingContext?.meetingId}")
                activeMeetingContext = null
            }
        }
    }

    /**
     * Add a transcript chunk to active meeting
     * Thread-safe operation
     *
     * @param chunk TranscriptChunk to add
     */
    fun addTranscriptChunk(chunk: TranscriptChunk) {
        synchronized(lock) {
            activeMeetingContext?.let { context ->
                context.recentTranscripts.add(chunk)

                // Update meeting duration
                val durationMs = System.currentTimeMillis() - context.startTime
                context.durationMinutes = (durationMs / 60000).toInt()

                Log.d(TAG, "Added transcript chunk: ${chunk.text.take(50)}...")
            } ?: run {
                Log.w(TAG, "Attempted to add chunk but no active meeting")
            }
        }
    }

    /**
     * Add a micro summary to active meeting
     */
    fun addMicroSummary(summary: MicroSummary) {
        synchronized(lock) {
            activeMeetingContext?.microSummaries?.add(summary)
            Log.d(TAG, "Added micro summary")
        }
    }

    /**
     * Add a section summary to active meeting
     */
    fun addSectionSummary(summary: String) {
        synchronized(lock) {
            activeMeetingContext?.sectionSummaries?.add(summary)
            Log.d(TAG, "Added section summary")
        }
    }

    /**
     * Add a decision to active meeting
     */
    fun addDecision(decision: Decision) {
        synchronized(lock) {
            activeMeetingContext?.decisions?.add(decision)
            Log.d(TAG, "Added decision: ${decision.description}")
        }
    }

    /**
     * Add an action item to active meeting
     */
    fun addActionItem(actionItem: ActionItem) {
        synchronized(lock) {
            activeMeetingContext?.actionItems?.add(actionItem)
            Log.d(TAG, "Added action item: ${actionItem.task}")
        }
    }

    /**
     * Update current topic being discussed
     */
    fun updateCurrentTopic(topic: String) {
        synchronized(lock) {
            activeMeetingContext?.currentTopic = topic
            Log.d(TAG, "Updated current topic: $topic")
        }
    }

    /**
     * Get condensed context for LLM query
     * Thread-safe retrieval
     */
    fun getCondensedContext(): String {
        synchronized(lock) {
            return activeMeetingContext?.getCondensedContext()
                ?: "No active meeting"
        }
    }

    /**
     * Check if user was mentioned in recent context
     */
    fun wasUserMentioned(): Boolean {
        synchronized(lock) {
            return activeMeetingContext?.wasUserMentioned() ?: false
        }
    }

    /**
     * Get meeting duration in minutes
     */
    fun getMeetingDuration(): Int {
        synchronized(lock) {
            return activeMeetingContext?.durationMinutes ?: 0
        }
    }

    /**
     * Get meeting statistics for UI display
     */
    fun getMeetingStats(): MeetingStats {
        synchronized(lock) {
            val context = activeMeetingContext
            return MeetingStats(
                isActive = context != null,
                durationMinutes = context?.durationMinutes ?: 0,
                transcriptChunks = context?.recentTranscripts?.size ?: 0,
                microSummaries = context?.microSummaries?.size ?: 0,
                sectionSummaries = context?.sectionSummaries?.size ?: 0,
                decisions = context?.decisions?.size ?: 0,
                actionItems = context?.actionItems?.size ?: 0
            )
        }
    }
}

/**
 * MeetingStats
 * Data class for meeting statistics display
 */
data class MeetingStats(
    val isActive: Boolean,
    val durationMinutes: Int,
    val transcriptChunks: Int,
    val microSummaries: Int,
    val sectionSummaries: Int,
    val decisions: Int,
    val actionItems: Int
)