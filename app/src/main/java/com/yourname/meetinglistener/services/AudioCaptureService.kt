package com.yourname.meetinglistener.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.meetinglistener.MainActivity
import com.yourname.meetinglistener.R
import com.yourname.meetinglistener.ai.LLMManager
import com.yourname.meetinglistener.ai.SummarizerEngine
import com.yourname.meetinglistener.speech.AudioProcessor
import com.yourname.meetinglistener.speech.RecognitionStatus
import com.yourname.meetinglistener.speech.VoskSpeechRecognizer
import com.yourname.meetinglistener.storage.ContextManager
import com.yourname.meetinglistener.utils.NotificationHelper
import kotlinx.coroutines.*

/**
 * AudioCaptureService.kt (FIXED VERSION with Vosk)
 *
 * PURPOSE:
 * Foreground service using Vosk for continuous speech recognition
 * Handles pauses naturally without restart
 *
 * IMPROVEMENTS:
 * - Uses Vosk instead of Android SpeechRecognizer
 * - Truly continuous (no restart needed)
 * - Better for meetings with pauses
 * - 100% offline and free
 */
class AudioCaptureService : Service() {

    private val TAG = "AudioCaptureService"

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "meeting_listener_channel"
        const val ACTION_START = "START_LISTENING"
        const val ACTION_STOP = "STOP_LISTENING"
        const val EXTRA_USER_NAME = "user_name"
    }

    // Core components
    private lateinit var speechRecognizer: VoskSpeechRecognizer
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var contextManager: ContextManager
    private lateinit var llmManager: LLMManager
    private lateinit var summarizerEngine: SummarizerEngine
    private lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var userName: String = ""
    private var transcriptCount = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Initialize components
        speechRecognizer = VoskSpeechRecognizer(this)
        audioProcessor = AudioProcessor()
        contextManager = ContextManager.getInstance()
        llmManager = LLMManager(this)
        summarizerEngine = SummarizerEngine(llmManager)
        notificationHelper = NotificationHelper(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
                startListening()
            }
            ACTION_STOP -> {
                stopListening()
            }
        }

        return START_STICKY
    }

    /**
     * Start listening with Vosk
     */
    private fun startListening() {
        Log.d(TAG, "Starting Vosk audio capture for user: $userName")

        // Start meeting context
        contextManager.startMeeting(userName)

        // Start foreground
        val notification = createNotification("Initializing...")
        startForeground(NOTIFICATION_ID, notification)

        // Initialize Vosk
        speechRecognizer.initialize()

        // Monitor status
        serviceScope.launch {
            speechRecognizer.statusFlow.collect { status ->
                handleRecognitionStatus(status)
            }
        }

        // Collect transcripts
        serviceScope.launch {
            speechRecognizer.transcriptFlow.collect { transcript ->
                processTranscript(transcript)
            }
        }
    }

    /**
     * Handle recognition status changes
     */
    private fun handleRecognitionStatus(status: RecognitionStatus) {
        when (status) {
            is RecognitionStatus.Initializing -> {
                updateNotification("Loading speech model...")
                Log.d(TAG, "Initializing...")
            }
            is RecognitionStatus.Ready -> {
                Log.d(TAG, "Ready - starting recognition")
                speechRecognizer.startListening()
            }
            is RecognitionStatus.Listening -> {
                updateNotification("Listening to meeting...")
                Log.d(TAG, "Now listening")
            }
            is RecognitionStatus.Stopped -> {
                updateNotification("Stopped")
                Log.d(TAG, "Stopped")
            }
            is RecognitionStatus.Error -> {
                updateNotification("Error: ${status.message}")
                Log.e(TAG, "Error: ${status.message}")
            }
        }
    }

    /**
     * Process incoming transcript
     */
    private suspend fun processTranscript(rawTranscript: String) {
        transcriptCount++
        Log.d(TAG, "Transcript #$transcriptCount: ${rawTranscript.take(50)}...")

        // Update notification with count
        updateNotification("Listening... ($transcriptCount segments captured)")

        // Convert to chunk
        val chunk = audioProcessor.processTranscript(rawTranscript) ?: return

        // Add to context
        contextManager.addTranscriptChunk(chunk)

        // Check for name mention
        if (audioProcessor.containsUserName(rawTranscript, userName)) {
            Log.d(TAG, "🔔 User name detected!")
            notificationHelper.sendNameMentionAlert(userName)
        }

        // Get context
        val meetingContext = contextManager.getActiveMeeting() ?: return

        // Process through summarizer
        summarizerEngine.processTranscriptChunk(chunk, meetingContext)
    }

    /**
     * Stop listening
     */
    private fun stopListening() {
        Log.d(TAG, "Stopping audio capture")

        // Stop recognition
        speechRecognizer.stopListening()

        // Generate final summary
        serviceScope.launch {
            val context = contextManager.getActiveMeeting()
            if (context != null) {
                val finalSummary = summarizerEngine.generateFinalSummary(context)
                Log.d(TAG, "Final summary generated")
                // TODO: Save to database
            }
        }

        // End meeting
        contextManager.endMeeting()

        // Stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Create notification
     */
    private fun createNotification(text: String = "Listening..."): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meeting Listener Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification text
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active meeting listening"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        speechRecognizer.destroy()
        audioProcessor.reset()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}