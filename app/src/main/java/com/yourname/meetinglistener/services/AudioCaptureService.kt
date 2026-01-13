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
import com.yourname.meetinglistener.ai.MoMGenerator
import com.yourname.meetinglistener.ai.SummarizerEngine
import com.yourname.meetinglistener.speech.AudioProcessor
import com.yourname.meetinglistener.speech.RecognitionStatus
import com.yourname.meetinglistener.speech.VoskSpeechRecognizer
import com.yourname.meetinglistener.storage.ContextManager
import com.yourname.meetinglistener.storage.MeetingDatabase
import com.yourname.meetinglistener.utils.NotificationHelper
import kotlinx.coroutines.*

/**
 * AudioCaptureService.kt (FIXED)
 *
 * FIXES:
 * - Proper duration tracking
 * - Guaranteed MoM generation
 * - Fixed suspend function call in stopListening
 */
class AudioCaptureService : Service() {

    private val TAG = "AudioCaptureService"

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "meeting_listener_channel"
        const val ACTION_START = "START_LISTENING"
        const val ACTION_STOP = "STOP_LISTENING"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_LANGUAGE = "language"
    }

    private lateinit var speechRecognizer: VoskSpeechRecognizer
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var contextManager: ContextManager
    private lateinit var llmManager: LLMManager
    private lateinit var summarizerEngine: SummarizerEngine
    private lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var userName: String = ""
    private var transcriptCount = 0

    // Duration update job
    private var durationUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

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
                val languageCode = intent.getStringExtra(EXTRA_LANGUAGE) ?: "en"
                val language = if (languageCode == "hi") {
                    VoskSpeechRecognizer.Language.HINDI
                } else {
                    VoskSpeechRecognizer.Language.ENGLISH
                }
                startListening(language)
            }
            ACTION_STOP -> {
                stopListening()
            }
        }

        return START_STICKY
    }

    private fun startListening(language: VoskSpeechRecognizer.Language) {
        Log.d(TAG, "🎤 Starting with ${language.displayName} for user: $userName")

        // Start meeting context
        contextManager.startMeeting(userName)

        // Start foreground
        val notification = createNotification("Initializing ${language.displayName}...")
        startForeground(NOTIFICATION_ID, notification)

        // Start duration update job (every 10 seconds)
        startDurationUpdates()

        // Initialize Vosk
        speechRecognizer = VoskSpeechRecognizer(this, language)
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

    private fun startDurationUpdates() {
        durationUpdateJob?.cancel()
        durationUpdateJob = serviceScope.launch {
            while (isActive) {
                contextManager.updateDuration()
                delay(10000)
            }
        }
    }

    private fun handleRecognitionStatus(status: RecognitionStatus) {
        when (status) {
            is RecognitionStatus.Initializing -> {
                updateNotification("Loading speech model...")
            }
            is RecognitionStatus.Ready -> {
                speechRecognizer.startListening()
            }
            is RecognitionStatus.Listening -> {
                updateNotification("🎤 Listening...")
            }
            is RecognitionStatus.Stopped -> {
                updateNotification("Stopped")
            }
            is RecognitionStatus.Error -> {
                updateNotification("⚠️ Error: ${status.message}")
            }
        }
    }

    private suspend fun processTranscript(rawTranscript: String) {
        transcriptCount++
        updateNotification("🎤 Listening... ($transcriptCount captured)")

        val chunk = audioProcessor.processTranscript(rawTranscript) ?: return
        contextManager.addTranscriptChunk(chunk)

        if (isNameMentioned(rawTranscript, userName)) {
            notificationHelper.sendNameMentionAlert(userName)
        }

        val meetingContext = contextManager.getActiveMeeting() ?: return
        summarizerEngine.processTranscriptChunk(chunk, meetingContext)
    }

    private fun isNameMentioned(transcript: String, name: String): Boolean {
        val lowerTranscript = transcript.lowercase()
        val lowerName = name.lowercase()
        return lowerTranscript.contains(lowerName)
    }

    private fun stopListening() {
        Log.d(TAG, "🛑 Stopping audio capture")

        // Cancel duration updates and stop recognition immediately
        durationUpdateJob?.cancel()
        speechRecognizer.stopListening()

        updateNotification("Generating meeting summary...")

        // Process summary and stop service in a coroutine
        serviceScope.launch {
            try {
                val context = contextManager.getActiveMeeting()
                if (context == null) {
                    Log.e(TAG, "❌ No active meeting context!")
                } else {
                    contextManager.updateDuration()
                    val finalTranscripts = context.recentTranscripts.size

                    if (finalTranscripts > 0) {
                        Log.d(TAG, "📝 Generating MoM for $finalTranscripts transcripts...")
                        val momGenerator = MoMGenerator(llmManager)
                        val meetingSummary = momGenerator.generateMoM(context)

                        val database = MeetingDatabase.getDatabase(this@AudioCaptureService)
                        database.meetingSummaryDao().insert(meetingSummary)

                        val chunks = context.recentTranscripts.map { chunk ->
                            com.yourname.meetinglistener.storage.entities.TranscriptChunkEntity(
                                meetingId = context.meetingId,
                                text = chunk.text,
                                timestamp = chunk.timestamp,
                                timestampMillis = chunk.timestampMillis,
                                speakerInfo = chunk.speakerInfo
                            )
                        }
                        if (chunks.isNotEmpty()) {
                            database.transcriptChunkDao().insertAll(chunks)
                        }
                        Log.d(TAG, "✅ Meeting saved successfully")
                    } else {
                        Log.w(TAG, "⚠️ No transcripts captured")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR saving meeting: ${e.message}", e)
            } finally {
                contextManager.endMeeting()
                // Small delay to ensure DB operations complete before service kills process
                delay(1500)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

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

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active meeting listening"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        durationUpdateJob?.cancel()
        speechRecognizer.destroy()
        audioProcessor.reset()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
