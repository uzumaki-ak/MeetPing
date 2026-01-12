package com.yourname.meetinglistener.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TranscriptChunk.kt (Room Database Entity)
 *
 * PURPOSE:
 * Database entity for storing transcript chunks
 * Allows saving meeting transcripts to local database
 *
 * NOTE: This is separate from the model TranscriptChunk
 * This is specifically for Room database persistence
 */
@Entity(tableName = "transcript_chunks")
data class TranscriptChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val meetingId: String,          // Which meeting this belongs to
    val text: String,               // Transcript text
    val timestamp: String,          // Readable timestamp
    val timestampMillis: Long,      // Unix timestamp
    val speakerInfo: String? = null // Optional speaker info
)