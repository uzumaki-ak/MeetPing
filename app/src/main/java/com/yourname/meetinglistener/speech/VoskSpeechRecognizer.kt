package com.yourname.meetinglistener.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

/**
 * VoskSpeechRecognizer.kt (MULTI-LANGUAGE VERSION)
 *
 * PURPOSE:
 * Supports both English and Hindi speech recognition
 * Automatically loads correct model based on user preference
 *
 * SUPPORTED LANGUAGES:
 * - English: model/
 * - Hindi: model-hi/
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val language: Language = Language.ENGLISH
) {

    private val TAG = "VoskRecognizer"

    private var model: Model? = null
    private var speechService: SpeechService? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val transcriptChannel = Channel<String>(Channel.BUFFERED)
    val transcriptFlow: Flow<String> = transcriptChannel.receiveAsFlow()

    private val statusChannel = Channel<RecognitionStatus>(Channel.BUFFERED)
    val statusFlow: Flow<RecognitionStatus> = statusChannel.receiveAsFlow()

    private var initialized = false
    private var listening = false

    /**
     * Language enum
     */
    enum class Language(val modelFolder: String, val displayName: String) {
        ENGLISH("model", "English"),
        HINDI("model-hi", "Hindi (हिंदी)")
    }

    /**
     * Initialize with selected language
     */
    fun initialize() {
        scope.launch {
            try {
                statusChannel.send(RecognitionStatus.Initializing)
                Log.d(TAG, "Initializing ${language.displayName} model...")

                val modelDir = copyAssetModelIfNeeded(language.modelFolder)
                model = Model(modelDir.absolutePath)

                initialized = true
                statusChannel.send(RecognitionStatus.Ready)

                Log.d(TAG, "${language.displayName} model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Init failed for ${language.displayName}", e)
                statusChannel.send(
                    RecognitionStatus.Error("Failed to load ${language.displayName} model: ${e.message}")
                )
            }
        }
    }

    fun startListening() {
        if (!initialized || listening) {
            Log.w(TAG, "Cannot start: initialized=$initialized, listening=$listening")
            return
        }

        try {
            val recognizer = Recognizer(model!!, 16000f)
            speechService = SpeechService(recognizer, 16000f)
            speechService!!.startListening(listener)

            listening = true
            scope.launch {
                statusChannel.send(RecognitionStatus.Listening)
                Log.d(TAG, "Started listening in ${language.displayName}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Start failed", e)
            scope.launch {
                statusChannel.send(RecognitionStatus.Error(e.message ?: "Start failed"))
            }
        }
    }

    fun stopListening() {
        if (!listening) return

        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            listening = false

            scope.launch { statusChannel.send(RecognitionStatus.Stopped) }
            Log.d(TAG, "Stopped listening")

        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
    }

    fun destroy() {
        stopListening()
        model?.close()
        model = null
        initialized = false
        scope.cancel()
        Log.d(TAG, "Destroyed")
    }

    /**
     * Recognition listener
     */
    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            // Partial results - can be used for real-time display
        }

        override fun onResult(hypothesis: String?) {
            hypothesis ?: return
            try {
                val text = JSONObject(hypothesis).optString("text", "")
                if (text.isNotBlank()) {
                    Log.d(TAG, "Result: $text")
                    transcriptChannel.trySend(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error", e)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            onResult(hypothesis)
        }

        override fun onError(e: Exception?) {
            Log.e(TAG, "Recognition error", e)
            scope.launch {
                statusChannel.send(
                    RecognitionStatus.Error(e?.message ?: "Recognition error")
                )
            }
        }

        override fun onTimeout() {
            Log.d(TAG, "Timeout (silence) - this is normal")
        }
    }

    /**
     * Copy model from assets to internal storage
     */
    private fun copyAssetModelIfNeeded(modelFolder: String): File {
        val outDir = File(context.filesDir, "vosk-$modelFolder")

        if (outDir.exists()) {
            Log.d(TAG, "Model already exists: ${outDir.absolutePath}")
            return outDir
        }

        Log.d(TAG, "Copying model from assets/$modelFolder...")
        outDir.mkdirs()
        copyAssetsRecursively(modelFolder, outDir)
        Log.d(TAG, "Model copied successfully")

        return outDir
    }

    private fun copyAssetsRecursively(assetPath: String, outDir: File) {
        val assets = context.assets
        val files = assets.list(assetPath) ?: return

        for (name in files) {
            val fullPath = "$assetPath/$name"
            val outFile = File(outDir, name)

            if (assets.list(fullPath)?.isNotEmpty() == true) {
                outFile.mkdirs()
                copyAssetsRecursively(fullPath, outFile)
            } else {
                assets.open(fullPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

/**
 * Recognition status
 */
sealed class RecognitionStatus {
    object Initializing : RecognitionStatus()
    object Ready : RecognitionStatus()
    object Listening : RecognitionStatus()
    object Stopped : RecognitionStatus()
    data class Error(val message: String) : RecognitionStatus()
}