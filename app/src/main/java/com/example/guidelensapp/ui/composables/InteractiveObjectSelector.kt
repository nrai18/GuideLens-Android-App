package com.example.guidelensapp.ui.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidelensapp.Config
import com.example.guidelensapp.viewmodel.NavigationViewModel
import kotlinx.coroutines.launch

@Composable
fun InteractiveObjectSelector(
    viewModel: NavigationViewModel,
    targetObject: String,
    onTargetSelected: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to current target
    LaunchedEffect(targetObject) {
        val index = Config.NAVIGABLE_OBJECTS.indexOfFirst { it.equals(targetObject, ignoreCase = true) }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SELECT TARGET",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            itemsIndexed(Config.NAVIGABLE_OBJECTS) { index, obj ->
                val isSelected = obj.equals(targetObject, ignoreCase = true)
                
                ObjectSelectionCard(
                    label = obj,
                    isSelected = isSelected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTargetSelected(obj)
                        viewModel.speak("$obj selected", Config.TTSPriority.NAVIGATION)
                    }
                )
            }
        }
    }
}

@Composable
fun ObjectSelectionCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF00E676) else Color.Transparent,
        label = "border"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1E1E1E) else Color(0xFF2C2C2C),
        label = "bg"
    )

    // Pulse effect when selected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = if (isSelected) 0.3f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp, 160.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        // Pulse Overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E676).copy(alpha = pulseAlpha))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = getIconForObject(label),
                contentDescription = null,
                tint = if (isSelected) Color(0xFF00E676) else Color.White,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = label.replaceFirstChar { it.uppercase() },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getIconForObject(label: String): ImageVector {
    return when (label.lowercase()) {
        "person" -> Icons.Default.Person
        "chair" -> Icons.Default.Chair
        "door" -> Icons.Default.DoorFront
        "table" -> Icons.Default.TableRestaurant
        "bottle" -> Icons.Default.LocalDrink
        "cup" -> Icons.Default.LocalCafe
        "laptop" -> Icons.Default.Computer
        "tv" -> Icons.Default.Tv
        else -> Icons.Default.Category
    }
}
