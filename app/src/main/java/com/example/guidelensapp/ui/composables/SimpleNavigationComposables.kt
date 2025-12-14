// app/src/main/java/com/example/guidelensapp/ui/composables/SimpleNavigationComposables.kt

package com.example.guidelensapp.ui.composables

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidelensapp.viewmodel.NavigationUiState
import com.example.guidelensapp.viewmodel.NavigationViewModel

// Neon Color Palette
private val NeonYellow = Color(0xFFFFFF00)
private val NeonGreen = Color(0xFF00FF00)
private val NeonRed = Color(0xFFFF0055)
private val DeepBlack = Color(0xFF000000)

@Composable
fun SimpleNavigationTopZone(
    uiState: NavigationUiState,
    viewModel: NavigationViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        InteractiveObjectSelector(
            viewModel = viewModel,
            targetObject = uiState.targetObject,
            onTargetSelected = { viewModel.setTargetObject(it) }
        )
    }
}

@Composable
fun SimpleNavigationBottomZone(
    uiState: NavigationUiState,
    viewModel: NavigationViewModel
) {
    val haptics = LocalHapticFeedback.current
    val isNav = uiState.isNavigating
    val isListening = uiState.isListeningForCommands
    
    // Dynamic color: Blue (Listening) > Red (Stop) > Green (Go)
    val activeColor = when {
        isListening -> Color(0xFF00B0FF) // Neon Blue
        isNav -> NeonRed
        else -> NeonGreen
    }

    // Aggressive pulse when navigating or listening
    val infiniteTransition = rememberInfiniteTransition(label = "nav_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNav || isListening) 1.05f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 400 else 800, easing = FastOutSlowInEasing), // Faster pulse for listening
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .border(
                BorderStroke(if (isNav || isListening) 6.dp else 2.dp, activeColor),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .pointerInput(uiState.isNavigating, isListening) {
                detectTapGestures(
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val message = when {
                            isListening -> "Listening..."
                            isNav -> "Navigation Active. Double tap to STOP."
                            else -> "Navigation Ready. Double tap to START. Long press to SPEAK."
                        }
                        viewModel.speak(message, 1)
                    },
                    onDoubleTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (uiState.isNavigating) {
                            viewModel.stopNavigation()
                            viewModel.speak("Navigation STOPPED.", 0)
                        } else {
                            if (uiState.targetObject.isNotEmpty()) {
                                viewModel.startNavigation()
                                viewModel.speak("Navigation STARTED to ${uiState.targetObject}.", 0)
                            } else {
                                viewModel.speak("Select a target first!", 0)
                            }
                        }
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleVoiceCommands() // Activate voice
                    }
                )
            }
            .semantics {
                contentDescription = if (isNav) "Stop Navigation Button" else "Start Navigation Button. Long press for Voice Command."
                role = Role.Button
            }
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow effect
            if (isNav || isListening) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale)
                        .background(activeColor.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                )
            }
            
            // Icon logic
            val currentIcon = when {
                isListening -> Icons.Default.Mic
                isNav -> Icons.Default.Close
                else -> Icons.Default.Navigation
            }
            
            Icon(
                imageVector = currentIcon,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Text Logic
        val mainText = when {
            isListening -> "SPEAK"
            isNav -> "STOP"
            else -> "GO"
        }
        
        Text(
            text = mainText,
            fontSize = 48.sp, // Massive text
            fontWeight = FontWeight.Black,
            color = activeColor,
            textAlign = TextAlign.Center,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subtext Logic
        val subText = when {
            isListening -> "Listening..."
            isNav -> "TO ${uiState.targetObject.uppercase()}"
            else -> "TO ${uiState.targetObject.uppercase()}"
        }

        Text(
            text = subText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = activeColor.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
        
        if (!isNav && !isListening) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "(Hold to Speak)",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SimpleStatusBanner(
    uiState: NavigationUiState,
    viewModel: NavigationViewModel
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = Color.Black
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Command Button
            VoiceCommandButton(
                isListening = uiState.isListeningForCommands,
                onClick = { viewModel.toggleVoiceCommands() }
            )

            Text(
                text = "GUIDELENS",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            // High contrast mode switcher
            IconButton(
                onClick = { viewModel.showModeSelector() },
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun VoiceCommandButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isListening) Color(0xFF00B0FF) else Color(0xFF1E1E1E) // Blue / Dark Grey
    val iconColor = if (isListening) Color.White else Color(0xFF00E676) // White / Green
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(backgroundColor, androidx.compose.foundation.shape.CircleShape)
            .border(2.dp, iconColor.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
    ) {
        Icon(
             imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
             contentDescription = "Voice Commands",
             tint = iconColor,
             modifier = Modifier.size(32.dp)
        )
    }
}
