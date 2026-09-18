package com.ludobt.app.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.GameStatus
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.SurfaceElevated
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.theme.TextSecondary
import com.ludobt.app.presentation.ui.board.LudoBoardCanvas
import com.ludobt.app.presentation.ui.components.LudoScreenBackground
import com.ludobt.app.presentation.ui.components.PlayerCornerPanel

/**
 * Clean, modern Ludo game screen matching reference Image 2.
 * Panels are positioned close to the board yards. All player names are fully dynamic.
 * Includes top navigation row with quit confirmation button and move undo button.
 */
@Composable
fun GameScreen(
    gameState: GameState,
    canUndo: Boolean = false,
    onUndoMove: () -> Unit = {},
    onRollDice: () -> Unit,
    onTokenSelected: (Int) -> Unit,
    onRematch: () -> Unit,
    onQuit: () -> Unit,
    onSendReaction: (String) -> Unit = {}
) {
    var showQuitDialog by remember { mutableStateOf(false) }

    val currentPlayer = gameState.currentPlayer

    val redPlayer = gameState.players.firstOrNull { it.color == PlayerColor.RED }
    val greenPlayer = gameState.players.firstOrNull { it.color == PlayerColor.GREEN }
    val yellowPlayer = gameState.players.firstOrNull { it.color == PlayerColor.YELLOW }
    val bluePlayer = gameState.players.firstOrNull { it.color == PlayerColor.BLUE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Royal Blue Textured Checkerboard Background
        LudoScreenBackground()

        // 2. Main Game Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation Bar: Back Arrow on Top-Left, Undo Move on Top-Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left corner: Back Navigation Button
                CircularGoldenNavButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Leave Match",
                    enabled = true,
                    onClick = { showQuitDialog = true }
                )

                // Top-Right corner: Undo Move Button
                CircularGoldenNavButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo Move",
                    enabled = canUndo,
                    onClick = onUndoMove
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cohesive Centered Game Unit (Dice Panels + Board close together)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Red Panel (Top-Left) & Green Panel (Top-Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (redPlayer != null) {
                        val isRedTurn = currentPlayer?.color == PlayerColor.RED
                        PlayerCornerPanel(
                            player = redPlayer,
                            color = PlayerColor.RED,
                            isCurrentTurn = isRedTurn,
                            isRolling = isRedTurn && gameState.isRolling,
                            diceValue = if (isRedTurn) gameState.diceValue else null,
                            canRoll = isRedTurn && redPlayer.type == PlayerType.HUMAN && !gameState.isRolling && gameState.diceValue == null && gameState.status != GameStatus.GAME_OVER,
                            onRollClick = onRollDice,
                            isLeftBadge = true
                        )
                    } else {
                        Spacer(modifier = Modifier.size(width = 86.dp, height = 44.dp))
                    }

                    if (greenPlayer != null) {
                        val isGreenTurn = currentPlayer?.color == PlayerColor.GREEN
                        PlayerCornerPanel(
                            player = greenPlayer,
                            color = PlayerColor.GREEN,
                            isCurrentTurn = isGreenTurn,
                            isRolling = isGreenTurn && gameState.isRolling,
                            diceValue = if (isGreenTurn) gameState.diceValue else null,
                            canRoll = isGreenTurn && greenPlayer.type == PlayerType.HUMAN && !gameState.isRolling && gameState.diceValue == null && gameState.status != GameStatus.GAME_OVER,
                            onRollClick = onRollDice,
                            isLeftBadge = false
                        )
                    } else {
                        Spacer(modifier = Modifier.size(width = 86.dp, height = 44.dp))
                    }
                }

                // Tight spacing between top panels and the board (matching Image 2)
                Spacer(modifier = Modifier.height(10.dp))

                // Center: 15x15 Ludo Board Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LudoBoardCanvas(
                        gameState = gameState,
                        onTokenSelected = onTokenSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Tight spacing between board and bottom panels (matching Image 2)
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Blue Panel (Bottom-Left) & Yellow Panel (Bottom-Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bluePlayer != null) {
                        val isBlueTurn = currentPlayer?.color == PlayerColor.BLUE
                        PlayerCornerPanel(
                            player = bluePlayer,
                            color = PlayerColor.BLUE,
                            isCurrentTurn = isBlueTurn,
                            isRolling = isBlueTurn && gameState.isRolling,
                            diceValue = if (isBlueTurn) gameState.diceValue else null,
                            canRoll = isBlueTurn && bluePlayer.type == PlayerType.HUMAN && !gameState.isRolling && gameState.diceValue == null && gameState.status != GameStatus.GAME_OVER,
                            onRollClick = onRollDice,
                            isLeftBadge = true
                        )
                    } else {
                        Spacer(modifier = Modifier.size(width = 86.dp, height = 44.dp))
                    }

                    if (yellowPlayer != null) {
                        val isYellowTurn = currentPlayer?.color == PlayerColor.YELLOW
                        PlayerCornerPanel(
                            player = yellowPlayer,
                            color = PlayerColor.YELLOW,
                            isCurrentTurn = isYellowTurn,
                            isRolling = isYellowTurn && gameState.isRolling,
                            diceValue = if (isYellowTurn) gameState.diceValue else null,
                            canRoll = isYellowTurn && yellowPlayer.type == PlayerType.HUMAN && !gameState.isRolling && gameState.diceValue == null && gameState.status != GameStatus.GAME_OVER,
                            onRollClick = onRollDice,
                            isLeftBadge = false
                        )
                    } else {
                        Spacer(modifier = Modifier.size(width = 86.dp, height = 44.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Victory Dialog
        if (gameState.isGameOver) {
            VictoryDialog(
                gameState = gameState,
                onRematch = onRematch,
                onHome = onQuit
            )
        }
    }

    // Leave Game Confirmation Dialog
    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = {
                Text(
                    text = "Leave Game?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to quit the current match?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitDialog = false
                        onQuit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Quit Game", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Circular golden navigation action button matching the user's reference image.
 * Features a radial amber-gold gradient, crisp 2dp gold border, and centered black vector icon.
 */
@Composable
private fun CircularGoldenNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .shadow(if (enabled) 5.dp else 0.dp, CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (enabled) {
                        listOf(Color(0xFFFFD54F), Color(0xFFC48B08))
                    } else {
                        listOf(Color(0xFF8A7A48), Color(0xFF5A4815))
                    }
                )
            )
            .border(
                width = 2.dp,
                color = if (enabled) AccentGold else Color(0x66B48B18),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.Black else Color(0x66000000),
            modifier = Modifier.size(22.dp)
        )
    }
}

