package com.example.guidelensapp.accessibility

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCommandManager(private val context: Context) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val TAG = "VoiceCommandManager"

    // Callback for when command is recognized
    var onCommandRecognized: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // State flow to update UI
    private val _isListeningFlow = MutableStateFlow(false)
    val isListeningFlow = _isListeningFlow.asStateFlow()

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
            Log.d(TAG, "🎤 SpeechRecognizer initialized")
        } else {
            Log.e(TAG, "❌ SpeechRecognizer NOT available on this device")
            onError?.invoke("Voice commands not supported on this device")
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isListening) {
                    stopListening()
                    delay(100) // Small buffer
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                _isListeningFlow.value = true
                Log.d(TAG, "🎤 Started listening...")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting listening", e)
                isListening = false
                _isListeningFlow.value = false
            }
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            _isListeningFlow.value = false
            Log.d(TAG, "🛑 Stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listener", e)
        }
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // RecognitionListener Implementation

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "🎤 Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "🎤 Speech started")
    }

    // Callback for audio level (0f to 1f)
    var onAudioLevelUpdate: ((Float) -> Unit)? = null
    private var lastLevelUpdateTime = 0L

    override fun onRmsChanged(rmsdB: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLevelUpdateTime < 50) return // Throttle to 20fps
        
        // Normalize typical speech RMS (-2dB to 10dB) to 0.0-1.0 range
        // Clamp values to ensure safety
        val normalized = ((rmsdB + 2) / 12f).coerceIn(0f, 1f)
        onAudioLevelUpdate?.invoke(normalized)
        lastLevelUpdateTime = currentTime
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "🎤 Speech ended, processing...")
        isListening = false
        _isListeningFlow.value = false
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Unknown error"
        }
        Log.e(TAG, "❌ Speech Error: $message ($error)")
        
        // Don't report "No match" as a critical error, just ignore
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            onError?.invoke(message)
        }
        
        isListening = false
        _isListeningFlow.value = false
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val command = matches[0]
            Log.d(TAG, "✅ Voice Recognized: $command")
            onCommandRecognized?.invoke(command)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // Optional: Update UI with live transcription
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
