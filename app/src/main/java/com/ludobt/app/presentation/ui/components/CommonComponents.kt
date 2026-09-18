package com.ludobt.app.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.BorderSubtle
import com.ludobt.app.presentation.theme.SurfaceDark
import com.ludobt.app.presentation.theme.SurfaceElevated
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.theme.TextSecondary
import com.ludobt.app.presentation.ui.board.getColorForPlayer
import com.ludobt.app.presentation.ui.board.getLightColorForPlayer

@Composable
fun PlayerStatusCard(
    player: Player,
    isCurrentTurn: Boolean,
    isRolling: Boolean,
    modifier: Modifier = Modifier
) {
    val playerColor = getColorForPlayer(player.color)
    val lightColor = getLightColorForPlayer(player.color)

    val infiniteTransition = rememberInfiniteTransition(label = "active_player_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val borderBrush = if (isCurrentTurn) {
        Brush.sweepGradient(listOf(playerColor, lightColor, AccentGold, playerColor))
    } else {
        Brush.linearGradient(listOf(BorderSubtle, BorderSubtle))
    }

    Box(
        modifier = modifier
            .shadow(if (isCurrentTurn) 8.dp else 2.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrentTurn) SurfaceElevated else SurfaceDark)
            .border(
                width = if (isCurrentTurn) 2.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Player Color Avatar Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(lightColor, playerColor))
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            ) {
                if (player.rank > 0) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Winner",
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = player.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = player.name,
                        color = TextPrimary,
                        fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (player.type == PlayerType.AI) {
                        Text(
                            text = "AI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier
                                .background(AccentGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Progress dots (4 tokens progress)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    player.tokens.forEach { token ->
                        val dotColor = when {
                            token.isFinished -> AccentGold
                            token.isInPlay -> playerColor
                            else -> Color(0xFF475569)
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${player.finishedTokensCount}/4",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReactionOverlay(
    onSendReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("😀", "🔥", "🏆", "😭", "🎲", "👏")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark.copy(alpha = 0.9f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { emoji ->
            Text(
                text = emoji,
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSendReaction(emoji) }
                    .padding(4.dp)
            )
        }
    }
}
