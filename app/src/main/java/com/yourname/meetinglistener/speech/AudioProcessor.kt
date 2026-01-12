package com.yourname.meetinglistener.speech

import android.util.Log
import com.yourname.meetinglistener.models.TranscriptChunk
import java.text.SimpleDateFormat
import java.util.*

/**
 * AudioProcessor.kt
 *
 * PURPOSE:
 * Processes raw transcripts into structured TranscriptChunk objects
 * Handles timestamp formatting and chunk creation
 * Filters out empty or invalid transcripts
 *
 * FEATURES:
 * - Automatic timestamp generation
 * - Transcript validation and cleaning
 * - Chunk deduplication
 */
class AudioProcessor {

    private val TAG = "AudioProcessor"

    // Time formatter for readable timestamps
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Last processed transcript to avoid duplicates
    private var lastTranscript: String? = null

    /**
     * Process raw transcript into TranscriptChunk
     *
     * @param rawTranscript Raw text from speech recognizer
     * @return TranscriptChunk or null if invalid
     */
    fun processTranscript(rawTranscript: String): TranscriptChunk? {
        // Clean and validate transcript
        val cleaned = cleanTranscript(rawTranscript)

        if (cleaned.isBlank()) {
            Log.d(TAG, "Skipping empty transcript")
            return null
        }

        // Check for duplicates
        if (cleaned == lastTranscript) {
            Log.d(TAG, "Skipping duplicate transcript")
            return null
        }

        lastTranscript = cleaned

        // Create timestamp
        val currentTimeMillis = System.currentTimeMillis()
        val timestamp = timeFormatter.format(Date(currentTimeMillis))

        // Create chunk
        val chunk = TranscriptChunk(
            text = cleaned,
            timestamp = timestamp,
            timestampMillis = currentTimeMillis
        )

        Log.d(TAG, "Processed chunk: [$timestamp] ${cleaned.take(50)}...")
        return chunk
    }

    /**
     * Clean transcript text
     * Removes extra whitespace, fixes capitalization, etc.
     */
    private fun cleanTranscript(text: String): String {
        return text
            .trim()
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .let {
                // Capitalize first letter if not already
                if (it.isNotEmpty() && it[0].isLowerCase()) {
                    it.replaceFirstChar { char -> char.uppercase() }
                } else {
                    it
                }
            }
    }

    /**
     * Reset processor state
     * Useful when starting a new meeting
     */
    fun reset() {
        lastTranscript = null
        Log.d(TAG, "AudioProcessor reset")
    }

    /**
     * Check if transcript contains user's name
     * Case-insensitive check
     */
    fun containsUserName(transcript: String, userName: String): Boolean {
        return transcript.contains(userName, ignoreCase = true)
    }

    /**
     * Extract potential speaker name from transcript
     * Simple heuristic: looks for "Name:" pattern
     */
    fun extractSpeaker(transcript: String): String? {
        val speakerPattern = Regex("^([A-Za-z]+):\\s*")
        val match = speakerPattern.find(transcript)
        return match?.groupValues?.get(1)
    }
}