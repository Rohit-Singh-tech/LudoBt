package com.ludobt.app.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.PanelBorderCyan
import com.ludobt.app.presentation.ui.board.draw3DGoti
import com.ludobt.app.presentation.ui.dice.DiceView
import com.ludobt.app.presentation.ui.dice.EmptyDiceBox

/**
 * Authentic 4-corner Player Panel matching Image 2:
 * Goti token badge on one side, beveled dice slot on the other side.
 */
@Composable
fun PlayerCornerPanel(
    player: Player?,
    color: PlayerColor,
    isCurrentTurn: Boolean,
    isRolling: Boolean,
    diceValue: Int?,
    canRoll: Boolean,
    onRollClick: () -> Unit,
    isLeftBadge: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "panel_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panel_glow_alpha"
    )

    val borderBrush = if (isCurrentTurn) {
        Brush.linearGradient(
            listOf(AccentGold, Color(0xFFFFEEBB), AccentGold.copy(alpha = glowAlpha))
        )
    } else {
        Brush.linearGradient(
            listOf(PanelBorderCyan, Color(0xFF1D4ED8))
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isCurrentTurn) 8.dp else 4.dp,
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF0C2B6E),
                        Color(0xFF061B48)
                    )
                )
            )
            .border(
                width = if (isCurrentTurn) 2.dp else 1.5.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLeftBadge) {
                GotiTokenBadge(color = color)
                Spacer(modifier = Modifier.width(5.dp))
                DiceSection(
                    isCurrentTurn = isCurrentTurn,
                    isRolling = isRolling,
                    diceValue = diceValue,
                    canRoll = canRoll,
                    onRollClick = onRollClick
                )
            } else {
                DiceSection(
                    isCurrentTurn = isCurrentTurn,
                    isRolling = isRolling,
                    diceValue = diceValue,
                    canRoll = canRoll,
                    onRollClick = onRollClick
                )
                Spacer(modifier = Modifier.width(5.dp))
                GotiTokenBadge(color = color)
            }
        }
    }
}

@Composable
private fun GotiTokenBadge(color: PlayerColor) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2E7A),
                        Color(0xFF05143A)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = PanelBorderCyan.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            draw3DGoti(
                center = center,
                size = size.width * 0.88f,
                color = color
            )
        }
    }
}

@Composable
private fun DiceSection(
    isCurrentTurn: Boolean,
    isRolling: Boolean,
    diceValue: Int?,
    canRoll: Boolean,
    onRollClick: () -> Unit
) {
    if (isCurrentTurn) {
        DiceView(
            diceValue = diceValue,
            isRolling = isRolling,
            canRoll = canRoll,
            onRollClick = onRollClick,
            size = 46.dp
        )
    } else {
        EmptyDiceBox(size = 46.dp)
    }
}
