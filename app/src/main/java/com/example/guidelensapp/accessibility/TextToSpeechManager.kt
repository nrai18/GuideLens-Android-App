package com.example.guidelensapp.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class TextToSpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val UTTERANCE_ID = "GUIDELENS_TTS"
    }

    private var tts: TextToSpeech? = null
    private val isInitialized = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    // State flow for UI feedback
    private val _speakingState = MutableStateFlow(false)
    val speakingState = _speakingState.asStateFlow()

    // Pending messages queue
    private val pendingMessages = Collections.synchronizedList(mutableListOf<String>())

    // Voices
    private var availableVoices: List<android.speech.tts.Voice> = emptyList()
    private var currentVoiceIndex = 0

    // Settings
    var speechRate: Float = 0.7f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(field)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setPitch(field)
        }

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        try {
            tts = TextToSpeech(context) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        val result = tts?.setLanguage(Locale.getDefault())

                        when (result) {
                            TextToSpeech.LANG_MISSING_DATA,
                            TextToSpeech.LANG_NOT_SUPPORTED -> {
                                Log.e(TAG, "Language not supported: ${Locale.getDefault()}")
                                // Fallback to English
                                tts?.setLanguage(Locale.US)
                            }
                            else -> {
                                Log.d(TAG, "TTS initialized successfully")
                                isInitialized.set(true)
                            }
                        }

                        // Set default parameters
                        tts?.setSpeechRate(speechRate)
                        tts?.setPitch(pitch)
                        
                        // Load voices
                        availableVoices = tts?.voices?.toList()?.sortedBy { it.name } ?: emptyList()
                        Log.d(TAG, "Found ${availableVoices.size} voices")
                        
                        // Try to find a better default voice (e.g. female/network)
                        selectBetterDefaultVoice()

                        // Set up utterance listener
                        setupUtteranceListener()

                        // Process pending messages
                        processPendingMessages()

                    }
                    else -> {
                        Log.e(TAG, "TTS initialization failed")
                        isInitialized.set(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS", e)
            isInitialized.set(false)
        }
    }

    private fun processPendingMessages() {
        synchronized(pendingMessages) {
            if (pendingMessages.isNotEmpty()) {
                Log.d(TAG, "Processing ${pendingMessages.size} pending messages")
                pendingMessages.forEach { text ->
                    speak(text)
                }
                pendingMessages.clear()
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking.set(true)
                _speakingState.value = true
                Log.d(TAG, "TTS started speaking")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking.set(false)
                _speakingState.value = false
                Log.d(TAG, "TTS finished speaking")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking.set(false)
                _speakingState.value = false
                Log.e(TAG, "TTS error occurred")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking.set(false)
                _speakingState.value = false
                Log.e(TAG, "TTS error: $errorCode")
            }
        })
    }

    /**
     * Speak text and add to queue
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping")
            return
        }

        if (!isInitialized.get()) {
            Log.d(TAG, "TTS not initialized, queuing message: $text")
            synchronized(pendingMessages) {
                pendingMessages.add(text)
            }
            return
        }

        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            Log.d(TAG, "Speaking: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    /**
     * Speak text immediately, interrupting current speech
     */
    fun speakImmediate(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping")
            return
        }

        if (!isInitialized.get()) {
            Log.d(TAG, "TTS not initialized, queuing immediate message: $text")
            synchronized(pendingMessages) {
                pendingMessages.add(text)
            }
            return
        }

        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            Log.d(TAG, "Speaking immediately: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text immediately", e)
        }
    }

    /**
     * Stop current speech
     */
    fun stop() {
        try {
            tts?.stop()
            synchronized(pendingMessages) {
                pendingMessages.clear()
            }
            isSpeaking.set(false)
            _speakingState.value = false
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            isInitialized.set(false)
            Log.d(TAG, "TTS shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
    fun cycleVoice() {
        if (availableVoices.isEmpty()) {
            speak("No alternative voices available.")
            return
        }

        currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.size
        val newVoice = availableVoices[currentVoiceIndex]
        
        try {
            tts?.voice = newVoice
            Log.d(TAG, "Switched to voice: ${newVoice.name}")
            // Don't announce the voice name as it might be technical (e.g. "en-us-x-iom-network")
            // Just speak a sample
            speak("Voice changed. I hope this is better.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set voice", e)
            speak("Failed to change voice.")
        }
    }
    
    fun getCurrentVoiceName(): String {
        return tts?.voice?.name ?: "Default"
    }
    
    private fun selectBetterDefaultVoice() {
        // User specifically requested "Male English British Accent"
        // 1. First priority: British Male voices (common codes: "rjs", "fis", or explicit "male")
        var preferred = availableVoices.find { voice -> 
            val name = voice.name.lowercase()
            (name.contains("en-gb") || name.contains("en_gb")) && 
            (name.contains("male") || name.contains("rjs") || name.contains("fis"))
        }
        
        // 2. Second priority: Any British voice
        if (preferred == null) {
            preferred = availableVoices.find { voice -> 
                val name = voice.name.lowercase()
                name.contains("en-gb") || name.contains("en_gb")
            }
        }

        // 3. Third priority: Any Male voice (US, etc.)
        if (preferred == null) {
            preferred = availableVoices.find { voice -> 
                val name = voice.name.lowercase()
                name.contains("male") || name.contains("iom")
            }
        }
        
        if (preferred != null) {
            try {
                tts?.voice = preferred
                currentVoiceIndex = availableVoices.indexOf(preferred)
                Log.d(TAG, "Selected preferred voice (Targeting British Male): ${preferred.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set preferred voice")
            }
        }
    }
}
