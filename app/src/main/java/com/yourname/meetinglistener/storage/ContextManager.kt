package com.yourname.meetinglistener.storage

import android.content.Context
import android.util.Log
import com.yourname.meetinglistener.models.*
import java.util.*

/**
 * ContextManager.kt (FIXED VERSION)
 *
 * FIXES:
 * - Proper duration calculation
 * - Thread-safe operations
 * - Better state management
 */
class ContextManager private constructor() {

    private val TAG = "ContextManager"

    private var activeMeetingContext: MeetingContext? = null
    private val lock = Any()

    companion object {
        @Volatile
        private var instance: ContextManager? = null

        fun getInstance(): ContextManager {
            return instance ?: synchronized(this) {
                instance ?: ContextManager().also { instance = it }
            }
        }
    }

    fun startMeeting(userName: String): MeetingContext {
        synchronized(lock) {
            endMeeting()

            val meetingId = UUID.randomUUID().toString()
            val newContext = MeetingContext(
                meetingId = meetingId,
                startTime = System.currentTimeMillis(),
                userName = userName
            )

            activeMeetingContext = newContext
            Log.d(TAG, "Started meeting: $meetingId at ${newContext.startTime}")

            return newContext
        }
    }

    fun getActiveMeeting(): MeetingContext? {
        synchronized(lock) {
            return activeMeetingContext
        }
    }

    fun isMeetingActive(): Boolean {
        synchronized(lock) {
            return activeMeetingContext != null
        }
    }

    fun endMeeting() {
        synchronized(lock) {
            if (activeMeetingContext != null) {
                Log.d(TAG, "Ending meeting: ${activeMeetingContext?.meetingId}")
                activeMeetingContext = null
            }
        }
    }

    /**
     * FIXED: Update duration based on start time
     */
    fun updateDuration() {
        synchronized(lock) {
            activeMeetingContext?.let { context ->
                val elapsedMs = System.currentTimeMillis() - context.startTime
                context.durationMinutes = (elapsedMs / 60000).toInt()
                Log.d(TAG, "Duration updated: ${context.durationMinutes} min")
            }
        }
    }

    fun addTranscriptChunk(chunk: TranscriptChunk) {
        synchronized(lock) {
            activeMeetingContext?.let { context ->
                context.recentTranscripts.add(chunk)

                // Update duration
                val elapsedMs = System.currentTimeMillis() - context.startTime
                context.durationMinutes = (elapsedMs / 60000).toInt()

                Log.d(TAG, "Added chunk. Total: ${context.recentTranscripts.size}, Duration: ${context.durationMinutes} min")
            }
        }
    }

    fun addMicroSummary(summary: MicroSummary) {
        synchronized(lock) {
            activeMeetingContext?.microSummaries?.add(summary)
            Log.d(TAG, "Added micro summary")
        }
    }

    fun addSectionSummary(summary: String) {
        synchronized(lock) {
            activeMeetingContext?.sectionSummaries?.add(summary)
            Log.d(TAG, "Added section summary")
        }
    }

    fun addDecision(decision: Decision) {
        synchronized(lock) {
            activeMeetingContext?.decisions?.add(decision)
            Log.d(TAG, "Added decision: ${decision.description}")
        }
    }

    fun addActionItem(actionItem: ActionItem) {
        synchronized(lock) {
            activeMeetingContext?.actionItems?.add(actionItem)
            Log.d(TAG, "Added action item: ${actionItem.task}")
        }
    }

    fun updateCurrentTopic(topic: String) {
        synchronized(lock) {
            activeMeetingContext?.currentTopic = topic
            Log.d(TAG, "Updated topic: $topic")
        }
    }

    fun getCondensedContext(): String {
        synchronized(lock) {
            return activeMeetingContext?.getCondensedContext()
                ?: "No active meeting"
        }
    }

    fun wasUserMentioned(): Boolean {
        synchronized(lock) {
            return activeMeetingContext?.wasUserMentioned() ?: false
        }
    }

    fun getMeetingDuration(): Int {
        synchronized(lock) {
            activeMeetingContext?.let { context ->
                val elapsedMs = System.currentTimeMillis() - context.startTime
                return (elapsedMs / 60000).toInt()
            }
            return 0
        }
    }

    fun getMeetingStats(): MeetingStats {
        synchronized(lock) {
            val context = activeMeetingContext

            // Calculate real-time duration
            val duration = if (context != null) {
                val elapsedMs = System.currentTimeMillis() - context.startTime
                (elapsedMs / 60000).toInt()
            } else {
                0
            }

            return MeetingStats(
                isActive = context != null,
                durationMinutes = duration,
                transcriptChunks = context?.recentTranscripts?.size ?: 0,
                microSummaries = context?.microSummaries?.size ?: 0,
                sectionSummaries = context?.sectionSummaries?.size ?: 0,
                decisions = context?.decisions?.size ?: 0,
                actionItems = context?.actionItems?.size ?: 0
            )
        }
    }
}

data class MeetingStats(
    val isActive: Boolean,
    val durationMinutes: Int,
    val transcriptChunks: Int,
    val microSummaries: Int,
    val sectionSummaries: Int,
    val decisions: Int,
    val actionItems: Int
)