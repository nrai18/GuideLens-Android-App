package com.example.guidelensapp.navigation

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.example.guidelensapp.Config
import com.example.guidelensapp.sensors.SpatialTracker
import androidx.core.graphics.scale
import androidx.core.graphics.get
import kotlin.math.*

data class NavigationOutput(
    val command: String,
    val path: List<PointF>? = null,
    val dangerLevel: Int = 0,
    val targetCentered: Boolean = false // NEW: Is target in center?
)

/**
 * Sensor-Integrated Navigation System:
 * - Uses device orientation sensors for real-world navigation
 * - Visual servoing to keep target centered
 * - Combines VFH for obstacle avoidance with bearing-based guidance
 */
class PathPlanner {
    
    // State for Hysteresis & Smoothing
    private var previousBestSector: Int = -1
    private var smoothedDirection: Float? = null
    
    companion object {
        private const val TAG = "PathPlanner"

        // VFH Parameters
        private const val HISTOGRAM_SECTORS = 36
        private const val SECTOR_ANGLE = 360f / HISTOGRAM_SECTORS
        private const val OBSTACLE_THRESHOLD = 0.3f
        
        // Hysteresis: Cost reduction for sticking to previous sector
        private const val HYSTERESIS_BONUS = 0.5f 

        // Visual Servoing Parameters
        private const val CENTER_TOLERANCE = 0.15f // 15% of screen width
        private const val HORIZONTAL_FOV = 60f // Camera horizontal FOV in degrees

        // Distance thresholds
        private const val ARRIVAL_THRESHOLD = 150f
        private const val CLOSE_RANGE = 300f
        private const val MID_RANGE = 600f
    }

    /**
     * Get navigation command using sensor fusion and visual servoing
     */
    fun getNavigationCommand(
        floorMask: Bitmap,
        targetPosition: PointF?,
        imageWidth: Int,
        imageHeight: Int,
        spatialGuidance: SpatialTracker.DirectionalGuidance? = null,
        currentAzimuth: Float = 0f // NEW: Device heading from sensors
    ): NavigationOutput {
        // Case 1: No target visible - use spatial memory
        if (targetPosition == null && spatialGuidance != null) {
            return getOffScreenNavigation(spatialGuidance, currentAzimuth)
        }

        // Case 2: No target at all
        if (targetPosition == null) {
            return NavigationOutput("Searching for target...")
        }

        // Case 3: Target visible - navigate using visual servoing + sensors
        return getVisualServoingNavigation(
            floorMask,
            targetPosition,
            imageWidth,
            imageHeight,
            currentAzimuth
        )
    }

    /**
     * Navigate to off-screen target using spatial memory and sensors
     */
    private fun getOffScreenNavigation(
        guidance: SpatialTracker.DirectionalGuidance,
        currentAzimuth: Float
    ): NavigationOutput {
        val azimuthDiff = guidance.azimuthDifference

        // Calculate how much user needs to turn
        val turnAngle = azimuthDiff

        val command = when {
            abs(turnAngle) < 10 -> "Go straight."
            abs(turnAngle) < 30 -> if (turnAngle > 0) "Bear right." else "Bear left."
            abs(turnAngle) < 90 -> if (turnAngle > 0) "Turn right." else "Turn left."
            abs(turnAngle) < 135 -> if (turnAngle > 0) "Sharp right." else "Sharp left."
            else -> "Turn around."
        }

        // Log.d(TAG, "📍 Off-screen nav: $command (turn: ${turnAngle.toInt()}°)")
        return NavigationOutput(command, null, 0, false)
    }

    /**
     * Navigate to visible target using visual servoing
     */
    private fun getVisualServoingNavigation(
        floorMask: Bitmap,
        targetPosition: PointF,
        imageWidth: Int,
        imageHeight: Int,
        currentAzimuth: Float
    ): NavigationOutput {
        val userPosition = PointF(imageWidth / 2f, imageHeight * 0.85f)
        val screenCenter = PointF(imageWidth / 2f, imageHeight / 2f)

        // Calculate distance to target
        val distanceToTarget = hypot(
            targetPosition.x - userPosition.x,
            targetPosition.y - userPosition.y
        )

        // Check if arrived
        if (distanceToTarget < ARRIVAL_THRESHOLD) {
            resetState()
            return NavigationOutput(
                command = "Arrived.",
                path = listOf(userPosition, targetPosition),
                dangerLevel = 0,
                targetCentered = true
            )
        }

        // Visual servoing: Calculate centering error
        val horizontalError = targetPosition.x - screenCenter.x
        val horizontalErrorRatio = horizontalError / (imageWidth / 2f)
        val isCentered = abs(horizontalErrorRatio) < CENTER_TOLERANCE

        // Calculate angle offset from camera center
        val angleOffset = horizontalErrorRatio * (HORIZONTAL_FOV / 2f)

        // Build obstacle histogram
        val polarHistogram = buildPolarHistogram(
            floorMask,
            userPosition,
            imageWidth,
            imageHeight
        )

        // Calculate target bearing in image space
        val imageBearing = calculateBearing(userPosition, targetPosition)

        // Select best direction considering obstacles
        val navigationResult = selectBestDirection(
            polarHistogram,
            imageBearing,
            distanceToTarget
        )
        
        // Smooth the output direction (Low-pass filter)
        val targetDir = navigationResult.bestDirection
        val smoothed = smoothedDirection?.let { prev ->
            // Handle angle wrap-around for smoothing
            val diff = angleDifference(targetDir, prev)
            prev + diff * 0.3f // alpha = 0.3 (smooth slow changes)
        } ?: targetDir
        
        smoothedDirection = normalizeAngle(smoothed)

        // Generate command with visual servoing guidance
        val command = generateServoCommand(
            angleOffset,
            isCentered,
            distanceToTarget,
            navigationResult.dangerLevel
        )

        // Create visual path
        val visualPath = createVisualPath(
            userPosition,
            targetPosition,
            smoothedDirection ?: navigationResult.bestDirection
        )

        return NavigationOutput(
            command = command,
            path = visualPath,
            dangerLevel = navigationResult.dangerLevel,
            targetCentered = isCentered
        )
    }
    
    private fun resetState() {
        previousBestSector = -1
        smoothedDirection = null
    }

    /**
     * Generate navigation command with visual servoing (keep object centered)
     */
    private fun generateServoCommand(
        angleOffset: Float,
        isCentered: Boolean,
        distance: Float,
        dangerLevel: Int
    ): String {
        val cautionText = when (dangerLevel) {
            2 -> "⚠️ Blocked! "
            1 -> "Careful. "
            else -> ""
        }

        // Priority 1: Center the target if not centered
        if (!isCentered) {
            return when {
                angleOffset > 15 -> cautionText + "Turn right."
                angleOffset > 5 -> cautionText + "Bear right."
                angleOffset < -15 -> cautionText + "Turn left."
                angleOffset < -5 -> cautionText + "Bear left."
                else -> cautionText + "Center."
            }
        }

        // Priority 2: If centered, move forward
        return when (dangerLevel) {
            0 -> "Go forward."
            1 -> "Go forward carefully."
            2 -> "Path blocked. Go around."
            else -> "Go forward."
        }
    }

    private fun buildPolarHistogram(
        mask: Bitmap,
        userPos: PointF,
        imageWidth: Int,
        imageHeight: Int
    ): FloatArray {
        val histogram = FloatArray(HISTOGRAM_SECTORS) { 0f }
        val sectorCounts = IntArray(HISTOGRAM_SECTORS) { 0 }

        val scanRadius = min(imageWidth, imageHeight) / 3
        
        // BILINEAR FILTERING ENABLED (filter = true)
        // This blends pixels to reduce noise from multi-color floors
        val scaledMask = mask.scale(imageWidth / 4, imageHeight / 4, filter = true)
        
        val scaleX = scaledMask.width.toFloat() / imageWidth
        val scaleY = scaledMask.height.toFloat() / imageHeight

        val angleStep = 5
        val radiusStep = 20

        for (angle in 0 until 360 step angleStep) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()

            for (r in radiusStep until scanRadius step radiusStep) {
                val x = userPos.x + r * cos(rad)
                val y = userPos.y + r * sin(rad)

                if (x < 0 || x >= imageWidth || y < 0 || y >= imageHeight) continue

                val scaledX = (x * scaleX).toInt().coerceIn(0, scaledMask.width - 1)
                val scaledY = (y * scaleY).toInt().coerceIn(0, scaledMask.height - 1)

                val pixel = scaledMask[scaledX, scaledY]
                val green = (pixel ushr 8) and 0xFF
                val alpha = (pixel ushr 24) and 0xFF

                // Robust check: floor must be clearly visible
                val isWalkable = (alpha > 100 && green > 100)

                val sector = ((angle / SECTOR_ANGLE).toInt() % HISTOGRAM_SECTORS)
                if (!isWalkable) {
                    histogram[sector] += 1f
                }
                sectorCounts[sector]++
            }
        }

        scaledMask.recycle()

        for (i in histogram.indices) {
            if (sectorCounts[i] > 0) {
                histogram[i] /= sectorCounts[i]
            }
        }
        return histogram
    }

    private fun calculateBearing(from: PointF, to: PointF): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle
    }

    private fun selectBestDirection(
        histogram: FloatArray,
        targetBearing: Float,
        distanceToTarget: Float
    ): NavigationResult {
        var bestDirection = targetBearing
        var minCost = Float.MAX_VALUE
        var bestSectorIndex = -1

        val freeSectors = mutableListOf<Int>()
        for (i in histogram.indices) {
            if (histogram[i] < OBSTACLE_THRESHOLD) {
                freeSectors.add(i)
            }
        }

        if (freeSectors.isEmpty()) {
            val leastOccupied = histogram.indices.minByOrNull { histogram[it] } ?: 0
            return NavigationResult(leastOccupied * SECTOR_ANGLE, 2)
        }

        for (sector in freeSectors) {
            val sectorAngle = sector * SECTOR_ANGLE
            val angleDiff = abs(angleDifference(sectorAngle, targetBearing))
            val obstacleCost = histogram[sector]
            
            // Hysteresis: Reduce cost if this is the previous best sector
            // This prevents "jitter" between two equal choices
            val hysteresisCost = if (sector == previousBestSector) -HYSTERESIS_BONUS else 0f
            
            val totalCost = (angleDiff / 180f) * 2.0f + obstacleCost + hysteresisCost

            if (totalCost < minCost) {
                minCost = totalCost
                bestDirection = sectorAngle
                bestSectorIndex = sector
            }
        }
        
        // Update state
        if (bestSectorIndex != -1) {
            previousBestSector = bestSectorIndex
        }

        val bestSector = (bestDirection / SECTOR_ANGLE).toInt() % HISTOGRAM_SECTORS
        val nearbyDensity = (max(0, bestSector - 1)..min(HISTOGRAM_SECTORS - 1, bestSector + 1))
            .map { histogram[it % HISTOGRAM_SECTORS] }
            .average()
            .toFloat()

        val dangerLevel = when {
            nearbyDensity > 0.2f -> 2
            nearbyDensity > 0.1f -> 1
            else -> 0
        }

        return NavigationResult(bestDirection, dangerLevel)
    }

    private fun createVisualPath(
        start: PointF,
        target: PointF,
        suggestedDirection: Float
    ): List<PointF> {
        val path = mutableListOf<PointF>()
        path.add(start)

        val distance = hypot(target.x - start.x, target.y - start.y)
        val numPoints = min(15, max(5, (distance / 100).toInt()))

        for (i in 1..numPoints) {
            val t = i.toFloat() / numPoints
            val directAngle = calculateBearing(start, target)

            val blendedAngle = if (t < 0.5f) {
                suggestedDirection * (1 - 2 * t) + directAngle * 2 * t
            } else {
                directAngle
            }

            val rad = Math.toRadians(blendedAngle.toDouble()).toFloat()
            val pointDist = distance * t
            val x = start.x + pointDist * cos(rad)
            val y = start.y + pointDist * sin(rad)

            path.add(PointF(x, y))
        }

        path.add(target)
        return path
    }
    
    // Utils
    private fun angleDifference(angle1: Float, angle2: Float): Float {
        var diff = angle1 - angle2
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return diff
    }
    
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0) normalized += 360f
        return normalized
    }

    private data class NavigationResult(
        val bestDirection: Float,
        val dangerLevel: Int
    )
}
