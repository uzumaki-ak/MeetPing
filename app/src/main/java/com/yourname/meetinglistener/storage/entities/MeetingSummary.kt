package com.yourname.meetinglistener.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MeetingSummary.kt (Room Database Entity)
 *
 * PURPOSE:
 * Database entity for storing complete meeting summaries
 * Saved when meeting ends for history access
 */
@Entity(tableName = "meeting_summaries")
data class MeetingSummaryEntity(
    @PrimaryKey
    val meetingId: String,

    val startTime: Long,            // When meeting started
    val endTime: Long,              // When meeting ended
    val durationMinutes: Int,       // Total duration
    val userName: String,           // User's name

    val finalSummary: String,       // Complete meeting summary
    val decisions: String,          // JSON list of decisions
    val actionItems: String,        // JSON list of action items

    val transcriptCount: Int,       // Number of transcript chunks
    val createdAt: Long = System.currentTimeMillis()
)