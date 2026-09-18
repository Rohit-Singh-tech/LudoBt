package com.ludobt.app.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.BorderSubtle
import com.ludobt.app.presentation.theme.SurfaceDark
import com.ludobt.app.presentation.theme.SurfaceElevated
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.theme.TextSecondary
import com.ludobt.app.presentation.ui.board.getColorForPlayer

@Composable
fun VictoryDialog(
    gameState: GameState,
    onRematch: () -> Unit,
    onHome: () -> Unit
) {
    val podium = gameState.winnerPodium
    val playersByColor = gameState.players.associateBy { it.color }

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(2.dp, Brush.linearGradient(listOf(AccentGold, BorderSubtle)), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Trophy Icon with glow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AccentGold.copy(alpha = 0.15f))
                        .border(2.dp, AccentGold, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = AccentGold,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "VICTORY!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Match Completed",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Podium Rankings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 2nd Place (if exists)
                    if (podium.size >= 2) {
                        val p2Color = podium[1]
                        val p2 = playersByColor[p2Color]
                        PodiumColumn(
                            player = p2?.name ?: "Player",
                            rank = "2nd",
                            color = getColorForPlayer(p2Color),
                            height = 85,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 1st Place (Center and Tallest)
                    if (podium.isNotEmpty()) {
                        val p1Color = podium[0]
                        val p1 = playersByColor[p1Color]
                        PodiumColumn(
                            player = p1?.name ?: "Champion",
                            rank = "1st 👑",
                            color = getColorForPlayer(p1Color),
                            height = 110,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // 3rd Place (if exists)
                    if (podium.size >= 3) {
                        val p3Color = podium[2]
                        val p3 = playersByColor[p3Color]
                        PodiumColumn(
                            player = p3?.name ?: "Player",
                            rank = "3rd",
                            color = getColorForPlayer(p3Color),
                            height = 70,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onHome,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Home", color = TextPrimary)
                    }

                    Button(
                        onClick = onRematch,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rematch", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    player: String,
    rank: String,
    color: Color,
    height: Int,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = player.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = player,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(SurfaceElevated)
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        ) {
            Text(
                text = rank,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
        }
    }
}
