package com.yourname.meetinglistener.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * SpeechRecognizer.kt
 *
 * PURPOSE:
 * Wrapper around Android's SpeechRecognizer API
 * Converts speech to text continuously during meetings
 * Provides Kotlin Flow for reactive transcript updates
 *
 * FEATURES:
 * - Continuous speech recognition
 * - Automatic restart on errors
 * - Flow-based transcript streaming
 * - Partial results support for real-time feedback
 *
 * USAGE:
 * val recognizer = SpeechRecognizerWrapper(context)
 * recognizer.startListening()
 * recognizer.transcriptFlow.collect { transcript -> ... }
 */
class SpeechRecognizerWrapper(private val context: Context) {

    private val TAG = "SpeechRecognizer"

    // Android SpeechRecognizer instance
    private var speechRecognizer: SpeechRecognizer? = null

    // Channel for transcript results
    private val transcriptChannel = Channel<String>(Channel.UNLIMITED)

    // Public flow for collecting transcripts
    val transcriptFlow: Flow<String> = transcriptChannel.receiveAsFlow()

    // Recognition intent configuration
    private val recognizerIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Prefer offline recognition if available (faster, more private)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    // Flag to track if we should keep listening
    private var shouldContinueListening = false

    /**
     * Initialize speech recognizer
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        Log.d(TAG, "SpeechRecognizer initialized")
    }

    /**
     * Start continuous listening
     */
    fun startListening() {
        shouldContinueListening = true

        if (speechRecognizer == null) {
            initialize()
        }

        speechRecognizer?.startListening(recognizerIntent)
        Log.d(TAG, "Started listening")
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        shouldContinueListening = false
        speechRecognizer?.stopListening()
        Log.d(TAG, "Stopped listening")
    }

    /**
     * Release resources
     */
    fun destroy() {
        shouldContinueListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        transcriptChannel.close()
        Log.d(TAG, "SpeechRecognizer destroyed")
    }

    /**
     * Recognition listener implementation
     */
    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - can be used for visualizations
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer received
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }

            Log.w(TAG, "Recognition error: $errorMessage")

            // Auto-restart if we should continue listening
            // Skip restart for "no match" errors (silence is normal)
            if (shouldContinueListening && error != SpeechRecognizer.ERROR_NO_MATCH) {
                restartListening()
            }
        }

        override fun onResults(results: Bundle?) {
            // Final results received
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { transcript ->
                Log.d(TAG, "Final result: $transcript")
                transcriptChannel.trySend(transcript)
            }

            // Restart listening for continuous recognition
            if (shouldContinueListening) {
                restartListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Partial results received (real-time updates)
            val matches = partialResults?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )
            matches?.firstOrNull()?.let { partial ->
                Log.d(TAG, "Partial result: $partial")
                // Optionally send partial results too
                // transcriptChannel.trySend("PARTIAL: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Recognition event: $eventType")
        }
    }

    /**
     * Restart listening after a short delay
     */
    private fun restartListening() {
        // Small delay to prevent rapid restart loops
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (shouldContinueListening) {
                speechRecognizer?.startListening(recognizerIntent)
                Log.d(TAG, "Restarted listening")
            }
        }, 100)
    }
}