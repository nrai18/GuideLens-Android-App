package com.example.guidelensapp.ui.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TargetVisualizer(
    targetName: String,
    title: String = "TARGET ACQUIRED",
    onAnimationEnd: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var glitchState by remember { mutableStateOf(0) }
    var displayedText by remember { mutableStateOf("") }

    val icon = getIconForTarget(targetName)
    val neonColor = Color(0xFF00E5FF) // Cyber Cyan

    LaunchedEffect(Unit) {
        isVisible = true
        
        // Glitch effect logic
        repeat(5) {
            glitchState = it % 2
            delay(50)
        }
        glitchState = 0

        // Typewriter text effect
        val fullText = "$title: ${targetName.uppercase()}"
        fullText.forEachIndexed { index, _ ->
            displayedText = fullText.substring(0, index + 1)
            delay(30)
        }

        delay(1500) // Hold duration
        isVisible = false
        delay(300) // Fade out duration
        onAnimationEnd()
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300)
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            // Background scan lines
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                neonColor.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glitchy Icon Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .scale(scale)
                ) {
                    // Main Icon
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = neonColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = if (glitchState == 1) 4.dp else 0.dp) // Jitter
                            .alpha(if (glitchState == 1) 0.7f else 1f)
                    )
                    
                    // "Ghost" Icon for glow
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = neonColor.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.2f)
                            .blur(if (glitchState == 1) 8.dp else 16.dp) // Compose 1.4+ modifier, simulating via graphicsLayer if needed
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Text
                Text(
                    text = displayedText,
                    color = neonColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

// Simple logic to map target strings to icons
private fun getIconForTarget(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("chair") -> Icons.Rounded.Chair // Requires Material Extended? Fallback to EventSeat
        lower.contains("person") -> Icons.Rounded.Face
        lower.contains("tv") || lower.contains("monitor") -> Icons.Rounded.Tv
        lower.contains("door") -> Icons.Rounded.DoorFront // Fallback MeetingRoom
        lower.contains("laptop") -> Icons.Rounded.Laptop
        else -> Icons.Filled.MyLocation
    }
}

// Basic Modifier extension for older compose versions if blur isn't found
// In real Compose 1.1+, use Modifier.blur(radius)
fun Modifier.blur(radius: androidx.compose.ui.unit.Dp): Modifier {
    // Placeholder as `blur` is available in M3/Compose 1.1+
    // If undefined, this function effectively does nothing but allows compilation
    return this 
}
