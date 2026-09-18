package com.ludobt.app.presentation.ui.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 3D Beveled Dice matching Image 2:
 * Cream-white face with soft lighting, golden metallic border, black pips, and rolling animation.
 */
@Composable
fun DiceView(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val rotation = remember { Animatable(0f) }
    var displayRoll by remember { mutableIntStateOf(diceValue ?: 2) }

    val infiniteTransition = rememberInfiniteTransition(label = "dice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (canRoll) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(isRolling) {
        if (isRolling) {
            for (i in 0..7) {
                rotation.animateTo(
                    targetValue = if (i % 2 == 0) 16f else -16f,
                    animationSpec = tween(45, easing = LinearEasing)
                )
                displayRoll = Random.nextInt(1, 7)
                delay(35)
            }
            rotation.animateTo(0f, animationSpec = tween(40))
        } else {
            diceValue?.let { displayRoll = it }
        }
    }

    LaunchedEffect(diceValue) {
        diceValue?.let { displayRoll = it }
    }

    val goldBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFEEBB),
            Color(0xFFE5B842),
            Color(0xFFB8860B),
            Color(0xFFF5CE66)
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(pulseScale)
            .rotate(rotation.value)
            .shadow(
                elevation = if (canRoll) 8.dp else 4.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFAF6F0),
                        Color(0xFFEFE8DD)
                    )
                )
            )
            .border(
                width = 1.8.dp,
                brush = goldBorderBrush,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = canRoll, onClick = onRollClick)
    ) {
        DiceFace(value = displayRoll)
    }
}

/**
 * Empty beveled dice slot placeholder matching Image 2:
 * Soft cream/rose-tinted cavity with golden metallic border.
 */
@Composable
fun EmptyDiceBox(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val goldBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFEEAA),
            Color(0xFFD4AF37),
            Color(0xFF996515)
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFAF0EE),
                        Color(0xFFEEDCD7)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = goldBorderBrush,
                shape = RoundedCornerShape(8.dp)
            )
    )
}

@Composable
fun DiceFace(value: Int) {
    val dotSize = 7.5.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.5.dp),
        contentAlignment = Alignment.Center
    ) {
        when (value) {
            1 -> {
                DiceDot(dotSize)
            }
            2 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    DiceDot(dotSize)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    DiceDot(dotSize)
                }
            }
            3 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    DiceDot(dotSize)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DiceDot(dotSize)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    DiceDot(dotSize)
                }
            }
            4 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                }
            }
            5 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                }
                DiceDot(dotSize)
            }
            else -> { // 6
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                        DiceDot(dotSize)
                    }
                }
            }
        }
    }
}

@Composable
fun DiceDot(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1E293B))
    )
}
