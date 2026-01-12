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

class VoskSpeechRecognizer(private val context: Context) {

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

    /* ---------------- INIT ---------------- */

    fun initialize() {
        scope.launch {
            try {
                statusChannel.send(RecognitionStatus.Initializing)

                val modelDir = copyAssetModelIfNeeded()
                model = Model(modelDir.absolutePath)

                initialized = true
                statusChannel.send(RecognitionStatus.Ready)

                Log.d(TAG, "Vosk model loaded: ${modelDir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                statusChannel.send(
                    RecognitionStatus.Error(e.message ?: "Model init failed")
                )
            }
        }
    }

    /* ---------------- START ---------------- */

    fun startListening() {
        if (!initialized || listening) return

        try {
            val recognizer = Recognizer(model!!, 16000f)
            speechService = SpeechService(recognizer, 16000f)
            speechService!!.startListening(listener)

            listening = true
            scope.launch { statusChannel.send(RecognitionStatus.Listening) }

        } catch (e: Exception) {
            Log.e(TAG, "Start failed", e)
            scope.launch {
                statusChannel.send(
                    RecognitionStatus.Error(e.message ?: "Start failed")
                )
            }
        }
    }

    /* ---------------- STOP ---------------- */

    fun stopListening() {
        if (!listening) return

        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            listening = false

            scope.launch { statusChannel.send(RecognitionStatus.Stopped) }

        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
    }

    fun destroy() {
        stopListening()
        model?.close()
        model = null
        scope.cancel()
    }

    /* ---------------- LISTENER ---------------- */

    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {}

        override fun onResult(hypothesis: String?) {
            hypothesis ?: return
            val text = JSONObject(hypothesis).optString("text")
            if (text.isNotBlank()) transcriptChannel.trySend(text)
        }

        override fun onFinalResult(hypothesis: String?) {
            onResult(hypothesis)
        }

        override fun onError(e: Exception?) {
            scope.launch {
                statusChannel.send(
                    RecognitionStatus.Error(e?.message ?: "Recognition error")
                )
            }
        }

        override fun onTimeout() {}
    }

    /* ---------------- ASSET COPY ---------------- */

    private fun copyAssetModelIfNeeded(): File {
        val outDir = File(context.filesDir, "vosk-model")
        if (outDir.exists()) return outDir

        outDir.mkdirs()
        copyAssetsRecursively("model", outDir)
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

/* ---------------- STATUS ---------------- */

sealed class RecognitionStatus {
    object Initializing : RecognitionStatus()
    object Ready : RecognitionStatus()
    object Listening : RecognitionStatus()
    object Stopped : RecognitionStatus()
    data class Error(val message: String) : RecognitionStatus()
}
