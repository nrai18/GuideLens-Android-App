package com.example.guidelensapp.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidelensapp.viewmodel.NavigationUiState
import com.example.guidelensapp.viewmodel.NavigationViewModel
import androidx.compose.foundation.Image
import com.example.guidelensapp.Config

@Composable
fun MedicineIdScreen(
    uiState: NavigationUiState,
    viewModel: NavigationViewModel
) {
    val haptics = LocalHapticFeedback.current
    val isScanning = uiState.isProcessing

    // Pulse animation for the scan button
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isScanning) 1.1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Scanning progress indicator
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview Area (Top 60%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Live camera feed
                uiState.cameraImage?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = "Camera Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Scanning overlay
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF6200EA).copy(alpha = 0.3f))
                    ) {
                        // Scanning line animation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0xFF00E676),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .align(Alignment.TopCenter)
                                .offset(y = (300 * scanProgress).dp)
                        )
                    }
                }
                
                // Frame overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.7f)
                        .border(
                            2.dp,
                            if (isScanning) Color(0xFF6200EA) else Color(0xFF00E676),
                            RoundedCornerShape(16.dp)
                        )
                )
                
                // Camera icon placeholder when no camera feed
                if (uiState.cameraImage == null) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            // Status and Controls Area (Bottom 40%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Voice Command Microphone (Top right) with Pulse Animation
                val audioScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (uiState.isListeningForCommands) 1f + uiState.audioLevel * 0.8f else 1f,
                    label = "micPulse",
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable { viewModel.startListeningForCommands(true) },
                        contentAlignment = Alignment.Center
                    ) {
                         // Pulse Background
                        if (uiState.isListeningForCommands) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .scale(audioScale)
                                    .background(Color(0xFF00E676).copy(alpha = 0.3f), CircleShape)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Commands Active",
                            tint = if (uiState.isListeningForCommands) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Status Text (when no text scanned)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = if (isScanning) "ANALYZING..." else "READY TO SCAN",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isScanning) Color(0xFF6200EA) else Color(0xFF00E676),
                        letterSpacing = 2.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isScanning) 
                            "Reading medicine information..." 
                        else 
                            "Point camera at medicine package",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // Scan Button with Voice Hint
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Main Scan Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                if (isScanning) Color(0xFF6200EA) else Color(0xFF00E676)
                            )
                            .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable(enabled = !isScanning) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.scanMedicineText()
                            }
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.HourglassEmpty else Icons.Default.Camera,
                            contentDescription = "Scan Medicine",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Voice command hint
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Say 'Scan' to capture",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Back button
                OutlinedButton(
                    onClick = { viewModel.showModeSelector() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Home")
                }
            }
        }
        
        // Scanned Text Dialog
        if (uiState.scannedMedicineText != null) {
            AlertDialog(
                onDismissRequest = { 
                    viewModel.clearMedicineText()
                },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCANNED TEXT",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        IconButton(
                            onClick = { 
                                viewModel.speak(
                                    uiState.scannedMedicineText ?: "",
                                    Config.TTSPriority.EMERGENCY
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Read text",
                                tint = Color(0xFF00E676)
                            )
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = uiState.scannedMedicineText ?: "",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.clearMedicineText() }
                    ) {
                        Text("CLOSE", color = Color.White)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.identifyMedicineWithAI() },
                        enabled = !uiState.isProcessing
                    ) {
                        Text(
                            if (uiState.isProcessing) "ANALYZING..." else "IDENTIFY",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }
    }
}
