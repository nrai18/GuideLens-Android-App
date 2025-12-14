package com.example.guidelensapp.ui.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidelensapp.viewmodel.AppMode
import kotlinx.coroutines.delay

@Composable
fun StartScreen(
    viewModel: com.example.guidelensapp.viewmodel.NavigationViewModel,
    onModeSelected: (AppMode) -> Unit,
    onSpeak: (String, Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    
    // State to control Splash vs Home
    var showSplash by remember { mutableStateOf(!viewModel.hasWelcomed) }
    var showHomeContent by remember { mutableStateOf(viewModel.hasWelcomed) }

    // Splash Screen Logic
    LaunchedEffect(Unit) {
        if (!viewModel.hasWelcomed) {
            // Play welcome audio IMMEDIATELY - queue will hold it if TTS not ready
            onSpeak("Welcome to GuideLens", 0)
            
            // Show splash animation for 3 seconds total
            delay(3000)
            
            // Transition to home
            showSplash = false
            showHomeContent = true
            viewModel.hasWelcomed = true
        } else {
            // Returning to Home: Just show content immediately
            showHomeContent = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding()
    ) {
        // --- HOME CONTENT ---
        AnimatedVisibility(
            visible = showHomeContent,
            enter = fadeIn(animationSpec = tween(800)) + slideInVertically(initialOffsetY = { 100 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with integrated logo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // "GUIDE" text
                        Text(
                            text = "GUIDE",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        // Lens Icon
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Simple lens circle
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        // "LENS" text
                        Text(
                            text = "LENS",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }
                }
                // Mode Selection Cards
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Simple Navigation
                    ModeSelectionCard(
                        title = "Simple Navigation",
                        description = "Audio-guided navigation. Blind-friendly interface.",
                        icon = Icons.Default.VolumeUp,
                        backgroundBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00C853), Color(0xFF69F0AE))
                        ),
                        onSelected = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeak("Simple navigation mode selected.", 0)
                            onModeSelected(AppMode.SIMPLE_NAVIGATION)
                        },
                        onFocus = { onSpeak("Simple navigation. Audio interface.", 1) }
                    )

                    // Debug Mode
                    ModeSelectionCard(
                        title = "Debug Mode",
                        description = "Visual overlays and tools.",
                        icon = Icons.Default.BugReport,
                        backgroundBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFEF6C00), Color(0xFFFFB74D))
                        ),
                        onSelected = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeak("Debug mode selected.", 0)
                            onModeSelected(AppMode.DEBUG_MODE)
                        },
                        onFocus = { onSpeak("Debug mode. Visual tools.", 1) }
                    )

                    // Medicine Identifier
                    ModeSelectionCard(
                        title = "Medicine Identifier",
                        description = "Scan medicine packages.",
                        icon = Icons.Rounded.Medication,
                        backgroundBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6200EA), Color(0xFFB388FF))
                        ),
                        onSelected = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeak("Medicine Identifier selected.", 0)
                            onModeSelected(AppMode.MEDICINE_ID)
                        },
                        onFocus = { onSpeak("Medicine Identifier. Scan text.", 1) }
                    )
                }
            }
        }

        // --- SPLASH SCREEN OVERLAY ---
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(800)) + scaleOut(targetScale = 1.5f, animationSpec = tween(800))
        ) {
            SplashScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    // "Focusing Lens" Animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    // Rotate the lens (acts as the "O" in LOGO)
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Pulsing Scale (Breathing)
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Welcome text fade-in animation
    var showWelcomeText by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(500) // Delay before text appears
        showWelcomeText = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Main Logo Area - GUIDE[LENS]LENS where [LENS] is the animated icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top text row: GUIDE • LENS with lens icon as separator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // "GUIDE" text
                Text(
                    text = "GUIDE",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Animated Lens Icon (replaces "O" conceptually)
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer rotating ring
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .scale(scale * 0.8f)
                            .rotate(rotation)
                            .border(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF00E676),
                                        Color.Transparent
                                    )
                                ),
                                androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    
                    // Center lens
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // "LENS" text
                Text(
                    text = "LENS",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }
        
        // Welcome message (bottom, fades in)
        AnimatedVisibility(
            visible = showWelcomeText,
            enter = fadeIn(animationSpec = tween(800)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Text(
                text = "Welcome to GuideLens",
                color = Color(0xFF00E676),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ModeSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    backgroundBrush: Brush,
    onSelected: () -> Unit,
    onFocus: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            .semantics {
                contentDescription = "$title. $description"
                role = Role.Button
                onClick {
                    onSelected()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onFocus()
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onDoubleTap = {
                        onSelected()
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
