package com.example.guidelensapp.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guidelensapp.Config
import com.example.guidelensapp.accessibility.TextToSpeechManager
import com.example.guidelensapp.ml.DetectionResult
import com.example.guidelensapp.ml.ObjectDetector
import com.example.guidelensapp.ml.FloorSegmenter
import com.example.guidelensapp.navigation.NavigationOutput
import com.example.guidelensapp.navigation.PathPlanner
import com.example.guidelensapp.sensors.SpatialTracker
import com.example.guidelensapp.utils.ThreadManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NavigationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState = _uiState.asStateFlow()

    private var objectDetector: ObjectDetector? = null
    private var floorSegmenter: FloorSegmenter? = null
    private var applicationContext: Context? = null
    private val threadManager = ThreadManager.getInstance()
    private val isProcessingFrame = AtomicBoolean(false)
    private val lastProcessedTime = AtomicLong(0L)
    private val isShuttingDown = AtomicBoolean(false)
    private var previousCameraImage: Bitmap? = null
    private var previousFloorMask: Bitmap? = null

    private var ttsManager: TextToSpeechManager? = null
    private var lastTTSAnnouncementTime = AtomicLong(0L)
    private var lastAnnouncedCommand: String? = null
    private var targetDetectedCount = 0
    private var targetLostCount = 0
    
    // Welcome flag
    var hasWelcomed = false

    private var spatialTracker: SpatialTracker? = null
    private var lastSpatialUpdate = AtomicLong(0L)
    private val SPATIAL_UPDATE_INTERVAL = 500L // Update every 500ms
    
    // NEW: Stateful PathPlanner
    private val pathPlanner = PathPlanner()

    companion object {
        private const val TAG = "NavigationViewModel"
    }
    
    /**
     * Set TTS manager from MainActivity (initialized early for audio reliability)
     */
    fun setTTSManager(manager: TextToSpeechManager) {
        this.ttsManager = manager
        Log.d(TAG, "✅ TTS Manager received from MainActivity")
    }

    fun initializeModels(context: Context) {
        if (objectDetector != null) return
        applicationContext = context.applicationContext

        viewModelScope.launch(threadManager.ioDispatcher) {
            try {
                Log.d(TAG, "🔧 Initializing heavy models...")
                _uiState.update { it.copy(navigationCommand = "Loading models...") }

                // Initialize Object Detection Model
                // Model loading is now handled internally by ObjectDetector
                objectDetector = com.example.guidelensapp.ml.ObjectDetector(context)
                Log.d(TAG, "✅ Object Detector initialized")

                // Initialize Floor Segmentation
                floorSegmenter = com.example.guidelensapp.ml.FloorSegmenter(context)
                Log.d(TAG, "✅ Floor Segmenter initialized")

                // Initialize Spatial Tracker (internal TTS usage removed/handled differently)
                spatialTracker = com.example.guidelensapp.sensors.SpatialTracker(context)
                spatialTracker?.startTracking()
                
                // Initialize OCR
                textRecognitionManager = com.example.guidelensapp.ml.TextRecognitionManager(context)
                Log.d(TAG, "✅ Text Recognition initialized")

                // Initialize Voice Commands
                initializeVoiceCommands(context)
                Log.d(TAG, "✅ Voice Command Manager initialized")

                // Start orientation update loop
                startOrientationUpdates()

                Log.d(TAG, "✅ Models initialized with spatial tracking")

                Log.d(TAG, "NavigationViewModel initialized with TTS")

                Log.d(TAG, "✅ Models initialized successfully")
                _uiState.update { it.copy(navigationCommand = "Models loaded - Warming up...") }

                var waitTime = 0
                while (floorSegmenter?.isReady() == false && waitTime < 5000) {
                    delay(100)
                    waitTime += 100
                }

                _uiState.update { it.copy(navigationCommand = "Ready - Select target and start navigation") }
                Log.d(TAG, "✅ Models ready for inference")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize models", e)
                _uiState.update { it.copy(navigationCommand = "Initialization failed: ${e.message}") }
            }
        }
    }

    /**
     * Continuously update device orientation in UI state
     */
    private fun startOrientationUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(100) // Update at 10Hz
                spatialTracker?.let { tracker ->
                    val orientation = tracker.getCurrentOrientation()
                    _uiState.update { it.copy(currentOrientation = orientation) }
                }
            }
        }
    }

    /**
     * Update spatial tracker with detections
     */
    private fun updateSpatialTracking(
        detections: List<DetectionResult>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpatialUpdate.get() < SPATIAL_UPDATE_INTERVAL) return

        spatialTracker?.let { tracker ->
            tracker.updateWithDetections(detections, imageWidth, imageHeight)

            val allTrackedObjects = tracker.getAllTrackedObjects()
            _uiState.update { it.copy(spatialObjects = allTrackedObjects) }

            // If navigating, provide off-screen guidance
            if (_uiState.value.isNavigating) {
                val guidance = tracker.getDirectionToObject(_uiState.value.targetObject)
                if (guidance != null && !guidance.isVisible) {
                    val offScreenMsg = "${_uiState.value.targetObject} is ${guidance.direction}"
                    _uiState.update { it.copy(offScreenGuidance = offScreenMsg) }
                    Log.d(TAG, "🧭 Off-screen guidance: $offScreenMsg")
                } else {
                    _uiState.update { it.copy(offScreenGuidance = null) }
                }
            }

            lastSpatialUpdate.set(currentTime)
        }
    }

    fun processFrame(bitmap: Bitmap) {
        if (isShuttingDown.get()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime.get() < Config.MIN_FRAME_INTERVAL_MS) {
            return
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch(threadManager.mlDispatcher) {
            try {
                val startTime = System.currentTimeMillis()
                val targetObjectName = _uiState.value.targetObject

                // Make a COPY of the bitmap to prevent recycling issues
                val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

                // Object Detection
                val detections = try {
                    if (_uiState.value.isNavigating && targetObjectName.isNotEmpty()) {
                        objectDetector?.detectObjects(bitmapCopy, listOf(targetObjectName))
                            ?: emptyList()
                    } else {
                        objectDetector?.detectObjects(bitmapCopy, Config.NAVIGABLE_OBJECTS)
                            ?: emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Object detection crashed", e)
                    emptyList()
                }

                val targetObject = detections.find {
                    it.label.equals(targetObjectName, ignoreCase = true)
                }

                val targetPos = targetObject?.let {
                    PointF(it.boundingBox.centerX(), it.boundingBox.centerY())
                }
                updateSpatialTracking(detections, bitmap.width, bitmap.height)

                // Floor Segmentation
                val floorMask = if (_uiState.value.isNavigating) {
                    try {
                        Log.d(TAG, "🟢 Running floor segmentation...")
                        floorSegmenter?.segmentFloor(bitmapCopy)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Floor segmentation crashed", e)
                        null
                    }
                } else {
                    null
                }

                // Path Planning
                val navigationOutput = when {
                    _uiState.value.isNavigating && floorMask != null && targetPos != null -> {
                        try {
                            Log.d(TAG, "🧭 Running sensor-integrated path planning...")

                            // Get spatial guidance for off-screen targets
                            val spatialGuidance = if (targetObject == null) {
                                spatialTracker?.getDirectionToObject(targetObjectName)
                            } else {
                                null
                            }

                            // Get current device orientation
                            val (azimuth, _, _) = spatialTracker?.getCurrentOrientation() ?: Triple(0f, 0f, 0f)

                            val navResult = pathPlanner.getNavigationCommand(
                                floorMask = floorMask,
                                targetPosition = targetPos,
                                imageWidth = bitmapCopy.width,
                                imageHeight = bitmapCopy.height,
                                spatialGuidance = spatialGuidance,
                                currentAzimuth = azimuth
                            )

                            Log.d(TAG, "📍 Nav result: ${navResult.command}, centered: ${navResult.targetCentered}")
                            announceNavigationCommand(navResult.command)
                            navResult
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Path planning crashed", e)
                            NavigationOutput("Navigation error", null)
                        }
                    }
                    _uiState.value.isNavigating && floorMask == null && targetPos != null -> {
                        Log.d(TAG, "⏳ Waiting for floor mask...")
                        NavigationOutput("Analyzing floor...", null)
                    }
                    _uiState.value.isNavigating -> {
                        Log.d(TAG, "🔍 Searching for $targetObjectName...")

                        // Use spatial guidance even when target not visible
                        val spatialGuidance = spatialTracker?.getDirectionToObject(targetObjectName)
                        if (spatialGuidance != null) {
                            val (azimuth, _, _) = spatialTracker?.getCurrentOrientation() ?: Triple(0f, 0f, 0f)
                            pathPlanner.getNavigationCommand(
                                floorMask = floorMask ?: bitmapCopy, // Fallback to bitmap if mask missing
                                targetPosition = null,
                                imageWidth = bitmapCopy.width,
                                imageHeight = bitmapCopy.height,
                                spatialGuidance = spatialGuidance,
                                currentAzimuth = azimuth
                            )
                        } else {
                            NavigationOutput("Searching for $targetObjectName...", null)
                        }
                    }
                    else -> null
                }

                val totalTime = System.currentTimeMillis() - startTime
                val navCmd = navigationOutput?.command ?: "none"
                
                // Calculate Performance Metrics
                val currentFPS = if (totalTime > 0) 1000f / totalTime else 0f
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val freeMem = runtime.freeMemory() / (1024 * 1024)
                
                val currentMetrics = com.example.guidelensapp.utils.PerformanceMetrics(
                    currentFPS = currentFPS,
                    averageInferenceTimeMs = totalTime,
                    lastFrameTimeMs = totalTime,
                    memoryUsedMB = usedMem,
                    memoryAvailableMB = freeMem,
                    frameCount = targetDetectedCount + targetLostCount // Approximate frame count
                )

                Log.d(
                    TAG,
                    "✅ Frame processed in ${totalTime}ms - Target: ${targetObject?.label ?: "none"} ${
                        targetObject?.confidence?.times(100)?.toInt() ?: 0
                    }% - Nav: $navCmd"
                )

                // Store old bitmaps to recycle AFTER UI update completes
                val oldCameraImage = previousCameraImage
                val oldFloorMask = previousFloorMask

                // Update UI State with converted ImageBitmaps
                _uiState.update { current ->
                    current.copy(
                        cameraImage = bitmapCopy.asImageBitmap(),
                        detectedObjects = if (current.isNavigating) {
                            targetObject?.let { listOf(it) } ?: emptyList()
                        } else {
                            detections
                        },
                        targetPosition = targetPos,
                        floorMaskOverlay = floorMask?.asImageBitmap(),
                        navigationCommand = navigationOutput?.command ?: current.navigationCommand,
                        path = navigationOutput?.path,
                        pathPoints = navigationOutput?.path,
                        performanceMetrics = currentMetrics // Update metrics
                    )
                }

                // Store new bitmaps for next iteration
                previousCameraImage = bitmapCopy
                previousFloorMask = floorMask

                // Delay recycling old bitmaps to ensure Compose has finished rendering
                delay(100) // Give Compose time to render

                // Now safe to recycle old bitmaps
                try {
                    if (oldCameraImage?.isRecycled == false) {
                        oldCameraImage.recycle()
                    }
                    if (oldFloorMask?.isRecycled == false) {
                        oldFloorMask.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error recycling old bitmaps", e)
                }

                lastProcessedTime.set(currentTime)
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }


    // UI Button Functions

    fun setTargetObject(objectLabel: String) {
        val normalizedLabel = objectLabel.lowercase().trim()
        _uiState.update {
            it.copy(
                targetObject = normalizedLabel,
                targetPosition = null,
                path = null,
                pathPoints = null,
                floorMaskOverlay = null
            )
        }
        Log.d(TAG, "🎯 Target object changed to: $normalizedLabel")
    }

    fun toggleObjectSelector() {
        _uiState.update { it.copy(showObjectSelector = !it.showObjectSelector) }
        Log.d(TAG, "⚙️ Object selector toggled: ${_uiState.value.showObjectSelector}")
    }

    fun startNavigation() {
        val targetObject = _uiState.value.targetObject
        if (targetObject.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot start navigation: no target object selected")
            ttsManager?.speak("Please select a target object first")
            return
        }

        _uiState.update {
            it.copy(
                isNavigating = true,
                showObjectSelector = false
            )
        }
        targetDetectedCount = 0
        targetLostCount = 0
        targetDetectedCount = 0
        targetLostCount = 0
        lastAnnouncedCommand = null
        _uiState.update { it.copy(hasArrived = false) } // Reset arrival state

        ttsManager?.speakImmediate("Starting navigation to $targetObject")
        Log.d(TAG, "🚀 Navigation started for: $targetObject")
    }

    fun setAppMode(mode: AppMode) {
        Log.d(TAG, "🔄 Changing app mode to: $mode")
        
        // Always stop listening when switching modes to ensure clean state
        voiceCommandManager?.stopListening()
        _uiState.update { it.copy(isListeningForCommands = false) }

        if (mode == AppMode.SIMPLE_NAVIGATION || mode == AppMode.DEBUG_MODE) {
            // Reset navigation state when switching modes
            _uiState.update { it.copy(
                appMode = mode,
                showModeSelector = false,
                isNavigating = false,
                showObjectSelector = true // Show selector for navigation modes
            )}
        } else {
            // Medicine ID mode
            _uiState.update { it.copy(
                appMode = mode,
                showModeSelector = false,
                showObjectSelector = false
            )}
            // Auto-start voice listening for Medicine ID mode ("Always On")
            viewModelScope.launch {
                delay(2000) // Brief delay for mode announcement start
                startListeningForCommands()
            }
        }
        
        // Announce mode
        val announcement = when (mode) {
            AppMode.SIMPLE_NAVIGATION -> "Simple navigation mode activated. Audio-only interface."
            AppMode.DEBUG_MODE -> "Debug mode activated. Visual overlays enabled."
            AppMode.MEDICINE_ID -> "Medicine Identifier mode. Point camera at medicine package."
        }
        speak(announcement, Config.TTSPriority.EMERGENCY)
    }

    fun showModeSelector() {
        stopNavigation()
        _uiState.update { it.copy(showModeSelector = true) }
        speak("Returning to home screen.", Config.TTSPriority.EMERGENCY)
    }

    fun cycleVoice() {
        ttsManager?.cycleVoice()
        val currentVoice = ttsManager?.getCurrentVoiceName() ?: "Default"
        speak("Voice changed to $currentVoice", Config.TTSPriority.NAVIGATION)
    }

    fun stopNavigation() {
        viewModelScope.launch {
            // Clear spatial memory
            spatialTracker?.clearMemory()

            // Reset all navigation state
            _uiState.update {
                it.copy(
                    isNavigating = false,
                    navigationCommand = "Ready",
                    path = null,
                    pathPoints = null,
                    targetPosition = null,
                    offScreenGuidance = null,
                    spatialObjects = emptyList(), // Clear UI spatial objects
                    floorMaskOverlay = null,
                    detectedObjects = emptyList() // Clear current detections
                )
            }

            // Reset tracking counters
            lastAnnouncedCommand = null
            targetDetectedCount = 0
            targetLostCount = 0

            // Announce stop
            ttsManager?.speakImmediate("Navigation ended")
            Log.d(TAG, "🛑 Navigation stopped and memory cleared")
        }
    }

    fun describeScene() {
        viewModelScope.launch {
            val detections = _uiState.value.detectedObjects
            if (detections.isEmpty()) {
                ttsManager?.speak("No objects detected in view")
                _uiState.update { it.copy(isSpeaking = true) }
                return@launch
            }

            val objectCounts = detections.groupingBy { it.label }.eachCount()
            val description = buildString {
                append("I can see ")
                objectCounts.entries.forEachIndexed { index, (obj, count) ->
                    if (index > 0 && index == objectCounts.size - 1) {
                        append(" and ")
                    } else if (index > 0) {
                        append(", ")
                    }
                    append("$count $obj")
                    if (count > 1) append("s")
                }
            }

            _uiState.update { it.copy(isSpeaking = true) }
            ttsManager?.speak(description)
            Log.d(TAG, "🔊 Scene description: $description")

            // Reset speaking state after delay
            delay(3000)
            _uiState.update { it.copy(isSpeaking = false) }
        }
    }

    fun stopSpeaking() {
        ttsManager?.stop()
        _uiState.update { it.copy(isSpeaking = false) }
        Log.d(TAG, "🔇 TTS stopped")
    }

    private fun announceNavigationCommand(command: String?) {
        if (command.isNullOrBlank()) return

        if (command.contains("Searching", ignoreCase = true) ||
            command.contains("Analyzing", ignoreCase = true)
        ) {
            return
        }

        if (command.contains("Arrived", ignoreCase = true) ||
            command.contains("destination", ignoreCase = true)
        ) {
            // One-shot arrival logic
            if (!_uiState.value.hasArrived) {
                val target = _uiState.value.targetObject
                val message = "Success! You have arrived at $target."

                _uiState.update { it.copy(
                    isSpeaking = true,
                    hasArrived = true,
                    showArrivalAnimation = true
                )}

                ttsManager?.speakImmediate(message)
                lastAnnouncedCommand = message

                viewModelScope.launch {
                    delay(4000) // Animation duration
                    _uiState.update { it.copy(isSpeaking = false, showArrivalAnimation = false) }
                }
            }
            return
        } else {
             // Reset arrival flag if we move away
             if (_uiState.value.hasArrived) {
                 _uiState.update { it.copy(hasArrived = false) }
             }
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAnnouncement = currentTime - lastTTSAnnouncementTime.get()

        val shouldAnnounce = (command != lastAnnouncedCommand) ||
                (timeSinceLastAnnouncement >= Config.TTS_ANNOUNCEMENT_INTERVAL_MS)

        if (shouldAnnounce) {
            _uiState.update { it.copy(isSpeaking = true) }
            ttsManager?.speak(command)
            lastAnnouncedCommand = command
            lastTTSAnnouncementTime.set(currentTime)
            Log.d(TAG, "🔊 TTS: $command")

            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(isSpeaking = false) }
            }
        }
    }

    fun cleanup() {
        isShuttingDown.set(true)
        viewModelScope.launch {
            delay(200) // Wait for any pending UI operations

            try {
                if (previousCameraImage?.isRecycled == false) {
                    previousCameraImage?.recycle()
                }
                if (previousFloorMask?.isRecycled == false) {
                    previousFloorMask?.recycle()
                }
                previousCameraImage = null
                previousFloorMask = null
                ttsManager?.shutdown()
                Log.d(TAG, "🧹 Cleanup completed with TTS shutdown")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }

    // Voice Command Manager - exposed for direct UI control in Medicine ID
    var voiceCommandManager: com.example.guidelensapp.accessibility.VoiceCommandManager? = null
    
    // Text Recognition Manager
    private var textRecognitionManager: com.example.guidelensapp.ml.TextRecognitionManager? = null

    // Initialize Voice Command Manager
private fun initializeVoiceCommands(context: Context) {
    voiceCommandManager = com.example.guidelensapp.accessibility.VoiceCommandManager(context).apply {
        onAudioLevelUpdate = { level ->
            _uiState.update { it.copy(audioLevel = level) }
        }
        onCommandRecognized = { command ->
            processVoiceCommand(command)
            // Auto-restart listening if in Medicine ID mode
            if (_uiState.value.appMode == AppMode.MEDICINE_ID) {
                viewModelScope.launch {
                    delay(1000) // Brief pause to prevent feedback loops
                    startListeningForCommands()
                }
            }
        }
        onError = { error ->
            if (error.contains("No match")) {
                // Silent fail in continuous mode
                if (_uiState.value.appMode != AppMode.MEDICINE_ID) {
                    ttsManager?.speak("I didn't catch that.")
                }
            } else {
                if (_uiState.value.appMode != AppMode.MEDICINE_ID) {
                    ttsManager?.speak("Voice error: $error")
                }
            }
            
            _uiState.update { it.copy(isListeningForCommands = false) }
            
            // Retry in Medicine ID mode
            if (_uiState.value.appMode == AppMode.MEDICINE_ID) {
                viewModelScope.launch {
                    delay(2000) // Wait before retrying
                    startListeningForCommands()
                }
            }
        }
    }
}
    fun startListeningForCommands(force: Boolean = false) {
        if (_uiState.value.isListeningForCommands) return
        
        // If forced (manual tap), stop TTS immediately
        if (force) {
            ttsManager?.stop()
        }

        // Prevent listening while speaking to avoid picking up own voice (unless forced OR in Medicine ID mode)
        if (ttsManager?.isSpeaking() == true && !force && _uiState.value.appMode != AppMode.MEDICINE_ID) {
            viewModelScope.launch {
                while(ttsManager?.isSpeaking() == true) {
                    delay(500)
                }
                startListeningForCommands(false)
            }
            return
        }

        _uiState.update { it.copy(isListeningForCommands = true) }
        voiceCommandManager?.startListening()
        Log.d(TAG, "🎙️ Started listening for commands (Force: $force)")
    }

    fun scanMedicineText() {
        val bitmap = previousCameraImage
        if (bitmap == null || bitmap.isRecycled) {
            speak("Camera not ready. Please wait.", Config.TTSPriority.EMERGENCY)
            return
        }
        
        _uiState.update { it.copy(isProcessing = true, scannedMedicineText = null) }
        speak("Capturing medicine picture...", Config.TTSPriority.NAVIGATION)
        
        // Create a safe copy
        val safeBitmap = try {
            bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy bitmap", e)
            _uiState.update { it.copy(isProcessing = false) }
            speak("Camera error.", Config.TTSPriority.EMERGENCY)
            return
        }
        
        viewModelScope.launch(threadManager.ioDispatcher) {
            try {
                Log.d(TAG, "📸 Starting OCR on image: ${safeBitmap.width}x${safeBitmap.height}")
                
                // Match UI Overlay dimensions: fillMaxWidth(0.9f) and fillMaxHeight(0.7f)
                // The overlay is centered in the top 60% of the screen in UI, but the camera feed
                // fills that same 60% box, so we crop relative to the *camera image itself*.
                
                val cropWidth = (safeBitmap.width * 0.9f).toInt()
                val cropHeight = (safeBitmap.height * 0.7f).toInt()
                val cropX = (safeBitmap.width - cropWidth) / 2
                val cropY = (safeBitmap.height - cropHeight) / 2
                
                Log.d(TAG, "✂️ Cropping: ${cropWidth}x${cropHeight} at ($cropX, $cropY)")
                
                val croppedBitmap = try {
                     Bitmap.createBitmap(safeBitmap, cropX, cropY, cropWidth, cropHeight)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to crop, using full image", e)
                    safeBitmap
                }
                
                // Perform OCR on CROPPED image
                val extractedText = textRecognitionManager?.recognizeText(croppedBitmap) ?: ""
                
                // Cleanup
                if (croppedBitmap != safeBitmap) {
                    croppedBitmap.recycle()
                }
                safeBitmap.recycle()
                
                Log.d(TAG, "📝 OCR Result: $extractedText")
                
                withContext(Dispatchers.Main) {
                    if (extractedText.isNotBlank()) {
                         // Store raw text but also give AI prompt
                        _uiState.update { it.copy(
                            isProcessing = false,
                            scannedMedicineText = extractedText
                        )}
                        
                        // Speak the first few words of the scanned text
                        val preview = extractedText.take(100) + if (extractedText.length > 100) "..." else ""
                        speak("Text found: $preview", Config.TTSPriority.EMERGENCY)
                        
                        // Wait a moment, then ask if user wants to search
                        viewModelScope.launch {
                            delay(2000) // Wait for text to be read
                            speak("Say 'search' or 'yes' to identify this medicine.", Config.TTSPriority.NAVIGATION)
                            // Auto-listen for the confirmation
                            startListeningForCommands()
                        }
                    } else {
                        _uiState.update { it.copy(
                            isProcessing = false,
                            scannedMedicineText = "No text detected"
                        )}
                        speak("No text found. Please try again.", Config.TTSPriority.EMERGENCY)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Medicine scan failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        scannedMedicineText = "Scan failed: ${e.message}"
                    )}
                    speak("Capture failed. Please try again.", Config.TTSPriority.EMERGENCY)
                }
            }
        }
    }
    
    fun identifyMedicineWithAI() {
        val rawText = _uiState.value.scannedMedicineText
        if (rawText.isNullOrBlank() || rawText == "No text detected") {
            speak("No captured text to analyze. Please capture first.", Config.TTSPriority.NAVIGATION)
            return
        }

        _uiState.update { it.copy(isProcessing = true) }
        speak("Analyzing with Open Artificial Intelligence...", Config.TTSPriority.NAVIGATION)

        viewModelScope.launch(threadManager.ioDispatcher) {
            // Replaced GeminiService with OpenAIService
            val summary = com.example.guidelensapp.network.OpenAIService.summarizeMedicine(rawText)
            
            withContext(Dispatchers.Main) {
                // Check if the summary indicates failure (check for specific error strings from OpenAIService)
                if (summary.contains("System busy") || summary.contains("Analysis failed") || summary.contains("Rate Limit")) {
                     _uiState.update { it.copy(
                        isProcessing = false,
                         scannedMedicineText = "AI Limit. Opening Web Search..."
                     )}
                     speak("Artificial Intelligence limit reached. Searching online.", Config.TTSPriority.EMERGENCY)
                     
                     // Fallback: Open Google Search
                     try {
                         val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                             data = android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(rawText)}")
                             flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                         }
                         applicationContext?.startActivity(intent)
                     } catch (e: Exception) {
                         Log.e(TAG, "Failed to open browser", e)
                         speak("Could not open web browser.", Config.TTSPriority.EMERGENCY)
                     }
                } else {
                     _uiState.update { it.copy(
                        isProcessing = false,
                         scannedMedicineText = summary 
                     )}
                     speak("Analysis complete: $summary", Config.TTSPriority.EMERGENCY)
                }
            }
        }
    }
    
    fun clearMedicineText() {
        _uiState.update { it.copy(scannedMedicineText = null) }
    }

    private fun processVoiceCommand(command: String) {
        Log.d(TAG, "🎤 Processing voice command: $command")
        
        val lowerCommand = command.lowercase()
        
        when {
            // Medicine ID mode - Scan command (Universal)
            lowerCommand.contains("scan") || lowerCommand.contains("read") -> {
                speak("Switching to Medicine Capture.", Config.TTSPriority.NAVIGATION)
                
                if (_uiState.value.appMode != AppMode.MEDICINE_ID) {
                    setAppMode(AppMode.MEDICINE_ID)
                }
                
                // Slight delay to allow camera/mode to settle before capturing
                viewModelScope.launch {
                    delay(800) 
                    scanMedicineText()
                }
            }
            
            // Medicine ID mode - Search/Identify command
            (lowerCommand.contains("search") || lowerCommand.contains("identify") || lowerCommand.contains("yes") || lowerCommand.contains("what is this")) && 
            _uiState.value.appMode == AppMode.MEDICINE_ID -> {
                identifyMedicineWithAI()
            }

            // Medicine ID mode - Capture/Picture/Click command (User Request: "capture the picture same as the work intended by the capture button")
            (lowerCommand.contains("capture") || lowerCommand.contains("picture") || lowerCommand.contains("click") || lowerCommand.contains("take photo")) &&
            _uiState.value.appMode == AppMode.MEDICINE_ID -> {
                speak("Capturing...", Config.TTSPriority.USER_ACTION)
                viewModelScope.launch {
                    scanMedicineText()
                }
            }
            
            // Navigation commands
            lowerCommand.contains("start") || lowerCommand.contains("begin") -> {
                speak("Starting navigation", Config.TTSPriority.NAVIGATION)
                startNavigation()
            }
            lowerCommand.contains("stop") || lowerCommand.contains("end") -> {
                speak("Stopping navigation", Config.TTSPriority.NAVIGATION)
                stopNavigation()
            }
            
            // Scene query
            lowerCommand.contains("what") || lowerCommand.contains("scene") || 
            lowerCommand.contains("describe") -> {
                describeScene()
            }
            
            // Target selection or Direct Navigation
            lowerCommand.contains("target") || lowerCommand.contains("find") || lowerCommand.contains("navigate to") -> {
                // Check if the command contains a valid object name
                val targetObject = Config.NAVIGABLE_OBJECTS.firstOrNull { lowerCommand.contains(it) }
                
                if (targetObject != null) {
                    speak("Navigating to $targetObject", Config.TTSPriority.NAVIGATION)
                    setTargetObject(targetObject)
                    // Support Navigation in both Simple and Debug modes
                    // Only switch if we are in Medicine ID mode (which lacks navigation UI)
                    if (_uiState.value.appMode == AppMode.MEDICINE_ID) {
                        setAppMode(AppMode.SIMPLE_NAVIGATION)
                    }
                    startNavigation()
                } else {
                    speak("Opening target selector", Config.TTSPriority.NAVIGATION)
                    _uiState.update { it.copy(showObjectSelector = true) }
                }
            }
            
            // Mode switching
            lowerCommand.contains("home") || lowerCommand.contains("menu") -> {
                showModeSelector()
            }
            
            // Voice change
            lowerCommand.contains("change voice") || lowerCommand.contains("different voice") -> {
                cycleVoice()
            }
            
            // Help
            lowerCommand.contains("help") || lowerCommand.contains("commands") -> {
                speak(
                    "Available commands: Start navigation. Stop. What do you see? Find target. Home. Change voice.",
                    Config.TTSPriority.NAVIGATION
                )
            }
            
            else -> {
                speak("Command not recognized. Say 'help' for available commands.", Config.TTSPriority.NAVIGATION)
            }
        }
    }
    fun onPause() {
        Log.d(TAG, "⏸️ App paused")
        ttsManager?.stop()
        spatialTracker?.stopTracking()
        voiceCommandManager?.stopListening()
    }

    fun onResume() {
        Log.d(TAG, "▶️ App resumed")
        if (_uiState.value.isNavigating || _uiState.value.offScreenGuidance != null) {
            spatialTracker?.startTracking()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 Cleaning up NavigationViewModel...")
        isShuttingDown.set(true)

        var waitCount = 0
        while (isProcessingFrame.get() && waitCount < 20) {
            Thread.sleep(100)
            waitCount++
        }

        try {
            viewModelScope.cancel()
            Thread.sleep(200)

            if (previousCameraImage?.isRecycled == false) {
                previousCameraImage?.recycle()
            }
            if (previousFloorMask?.isRecycled == false) {
                previousFloorMask?.recycle()
            }
            previousCameraImage = null
            previousFloorMask = null
            objectDetector?.close()
            objectDetector = null
            floorSegmenter = null
            ttsManager?.shutdown()
            applicationContext = null
            Log.d(TAG, "✅ NavigationViewModel cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during cleanup", e)
        }
    }



    fun toggleContinuousGuidance() {
        _uiState.update { it.copy(continuousAudioGuidance = !it.continuousAudioGuidance) }
    }

    fun toggleHapticFeedback() {
        _uiState.update { it.copy(hapticFeedbackEnabled = !it.hapticFeedbackEnabled) }
    }
    fun speak(text: String, priority: Int = Config.TTSPriority.NAVIGATION) {
        if (text.isBlank()) {
            Log.w(TAG, "⚠️ Empty text, skipping speech")
            return
        }

        when (priority) {
            Config.TTSPriority.EMERGENCY -> {
                // Interrupt current speech for emergency messages
                ttsManager?.speakImmediate(text)
                _uiState.update { it.copy(isSpeaking = true, lastSpokenCommand = text) }
                Log.d(TAG, "🔊 EMERGENCY TTS: $text")
            }
            else -> {
                // Queue for non-emergency messages
                ttsManager?.speak(text)
                _uiState.update { it.copy(lastSpokenCommand = text) }
                Log.d(TAG, "🔊 TTS: $text (Priority: $priority)")
            }
        }

        // Auto-reset speaking state
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(isSpeaking = false) }
        }
    }

    /**
     * Speak text immediately, interrupting current speech (convenience method)
     */
    fun speakImmediate(text: String) {
        speak(text, Config.TTSPriority.EMERGENCY)
    }
    fun togglePerformanceOverlay() {
        _uiState.update {
            it.copy(showPerformanceOverlay = !it.showPerformanceOverlay)
        }
        Log.d(TAG, "📊 Performance overlay toggled: ${_uiState.value.showPerformanceOverlay}")
    }

    /**
     * Toggle voice commands on/off
     */
    fun toggleVoiceCommands() {
        if (voiceCommandManager == null && applicationContext != null) {
            initializeVoiceCommands(applicationContext!!)
        }

        if (_uiState.value.isListeningForCommands) {
            _uiState.update { it.copy(isListeningForCommands = false) }
            voiceCommandManager?.stopListening()
            Log.d(TAG, "🎤 Voice toggled OFF")
        } else {
            startListeningForCommands(force = true)
        }
    }
    fun startVoiceCommands() {
        if (voiceCommandManager == null && applicationContext != null) {
             initializeVoiceCommands(applicationContext!!)
        }
        
        ttsManager?.stop() // Silence TTS so it doesn't hear itself
        voiceCommandManager?.startListening()
        // No TTS here, use UI indication or beep
    }

    fun stopVoiceCommands() {
        voiceCommandManager?.stopListening()
        _uiState.update { it.copy(isListeningForCommands = false) }
    }




}

