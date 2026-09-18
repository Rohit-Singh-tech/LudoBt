package com.ludobt.app.presentation.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.BgDark
import com.ludobt.app.presentation.theme.BorderSubtle
import com.ludobt.app.presentation.theme.LudoBlue
import com.ludobt.app.presentation.theme.LudoGreen
import com.ludobt.app.presentation.theme.LudoRed
import com.ludobt.app.presentation.theme.LudoYellow
import com.ludobt.app.presentation.theme.SurfaceDark
import com.ludobt.app.presentation.theme.SurfaceElevated
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.theme.TextSecondary
import com.ludobt.app.presentation.ui.board.getColorForPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun OnlineLobbyScreen(
    connectionStatus: ConnectionStatus,
    roomPin: String?,
    lobbyPlayers: List<Player>,
    errorEvents: Flow<String>,
    onQuickMatch: (playerCount: Int, playerName: String, color: PlayerColor) -> Unit,
    onCreateRoom: (playerCount: Int, playerName: String, color: PlayerColor) -> Unit,
    onJoinRoom: (roomPin: String, playerName: String, color: PlayerColor) -> Unit,
    onStartGame: () -> Unit = {},
    onLeaveLobby: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var playerName by remember { mutableStateOf("Player") }
    var selectedColor by remember { mutableStateOf(PlayerColor.RED) }
    var playerCount by remember { mutableIntStateOf(2) }
    var joinPinInput by remember { mutableStateOf("") }
    var isSearchingMatch by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        errorEvents.collect { err ->
            isSearchingMatch = false
            snackbarHostState.showSnackbar(err)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgDark,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        onLeaveLobby()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentGold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAY ONLINE",
                            color = AccentGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionStatus) {
                                            ConnectionStatus.CONNECTED_AS_HOST,
                                            ConnectionStatus.CONNECTED_AS_CLIENT -> Color(0xFF10B981)
                                            ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                                            else -> Color(0xFF10B981)
                                        }
                                    )
                            )
                            Text(
                                text = when (connectionStatus) {
                                    ConnectionStatus.CONNECTING -> "Connecting..."
                                    ConnectionStatus.CONNECTED_AS_HOST -> "Online • Host Active"
                                    ConnectionStatus.CONNECTED_AS_CLIENT -> "Online • Guest Active"
                                    else -> "Online Cloud"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1128), Color(0xFF001F54))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = AccentGold,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentGold,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            if (roomPin != null && selectedTab != 0) onLeaveLobby()
                            selectedTab = 0
                        },
                        text = {
                            Text(
                                "QUICK MATCH",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            if (roomPin != null && selectedTab != 1) onLeaveLobby()
                            selectedTab = 1
                        },
                        text = {
                            Text(
                                "CREATE ROOM",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            if (roomPin != null && selectedTab != 2) onLeaveLobby()
                            selectedTab = 2
                        },
                        text = {
                            Text(
                                "JOIN ROOM",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Active Room Display if in a room
                if (roomPin != null) {
                    ActiveRoomCard(
                        roomPin = roomPin,
                        players = lobbyPlayers,
                        onStartGame = onStartGame,
                        onLeave = onLeaveLobby
                    )
                } else if (isSearchingMatch) {
                    SearchingMatchCard(
                        playerCount = playerCount,
                        onCancel = {
                            isSearchingMatch = false
                            onLeaveLobby()
                        }
                    )
                } else {
                    when (selectedTab) {
                        0 -> QuickMatchTabContent(
                            playerName = playerName,
                            onNameChange = { playerName = it },
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = it },
                            playerCount = playerCount,
                            onPlayerCountChange = { playerCount = it },
                            onFindMatch = {
                                isSearchingMatch = true
                                onQuickMatch(playerCount, playerName, selectedColor)
                            }
                        )
                        1 -> CreateRoomTabContent(
                            playerName = playerName,
                            onNameChange = { playerName = it },
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = it },
                            playerCount = playerCount,
                            onPlayerCountChange = { playerCount = it },
                            onCreateRoom = {
                                onCreateRoom(playerCount, playerName, selectedColor)
                            }
                        )
                        2 -> JoinRoomTabContent(
                            playerName = playerName,
                            onNameChange = { playerName = it },
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = it },
                            pinInput = joinPinInput,
                            onPinChange = { if (it.length <= 6) joinPinInput = it },
                            onJoinRoom = {
                                if (joinPinInput.length == 6) {
                                    onJoinRoom(joinPinInput, playerName, selectedColor)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please enter a valid 6-digit Room PIN")
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun QuickMatchTabContent(
    playerName: String,
    onNameChange: (String) -> Unit,
    selectedColor: PlayerColor,
    onColorChange: (PlayerColor) -> Unit,
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    onFindMatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PLAYERS",
            color = AccentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerCountPill(label = "2", isSelected = playerCount == 2) {
                onPlayerCountChange(2)
            }
            PlayerCountPill(label = "4", isSelected = playerCount == 4) {
                onPlayerCountChange(4)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "YOUR NAME",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        NameInputBox(value = playerName, onValueChange = onNameChange)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PREFERRED COLOR",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).forEach { color ->
                SelectableGotiItem(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playerCount == 2) {
            Text(
                text = "🎯 2-Player Match: Diagonally opposite yard (${selectedColor.getOppositeColor().displayName}) will be automatically assigned to your opponent.",
                fontSize = 11.sp,
                color = AccentGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Text(
                text = "🎲 4-Player Match: Opponents will choose from remaining unselected colors.",
                fontSize = 11.sp,
                color = AccentGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LudoPlayButton(text = "FIND MATCH", onClick = onFindMatch)
    }
}

@Composable
fun CreateRoomTabContent(
    playerName: String,
    onNameChange: (String) -> Unit,
    selectedColor: PlayerColor,
    onColorChange: (PlayerColor) -> Unit,
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    onCreateRoom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ROOM CAPACITY",
            color = AccentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerCountPill(label = "2", isSelected = playerCount == 2) {
                onPlayerCountChange(2)
            }
            PlayerCountPill(label = "4", isSelected = playerCount == 4) {
                onPlayerCountChange(4)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HOST NAME",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        NameInputBox(value = playerName, onValueChange = onNameChange)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HOST COLOR",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).forEach { color ->
                SelectableGotiItem(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playerCount == 2) {
            Text(
                text = "🎯 Opponent Yard: Automatically assigned as ${selectedColor.getOppositeColor().displayName} (Diagonally Opposite)",
                fontSize = 11.sp,
                color = AccentGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Text(
                text = "🎲 4-Player Match: Participants will choose from remaining unselected colors.",
                fontSize = 11.sp,
                color = AccentGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LudoPlayButton(text = "CREATE ROOM", onClick = onCreateRoom)
    }
}

@Composable
fun JoinRoomTabContent(
    playerName: String,
    onNameChange: (String) -> Unit,
    selectedColor: PlayerColor,
    onColorChange: (PlayerColor) -> Unit,
    pinInput: String,
    onPinChange: (String) -> Unit,
    onJoinRoom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ENTER 6-DIGIT ROOM PIN",
            color = AccentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { str ->
                if (str.all { it.isDigit() }) onPinChange(str)
            },
            placeholder = { Text("e.g. 583921", color = Color(0xFF64748B)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(220.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "YOUR NAME",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        NameInputBox(value = playerName, onValueChange = onNameChange)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PREFERRED COLOR",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).forEach { color ->
                SelectableGotiItem(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "💡 For 2 players, the opposite court is automatically selected. For 4 players, taken colors cannot be picked.",
            fontSize = 11.sp,
            color = AccentGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        LudoPlayButton(text = "JOIN ROOM", onClick = onJoinRoom)
    }
}

@Composable
fun ActiveRoomCard(
    roomPin: String,
    players: List<Player>,
    onStartGame: () -> Unit = {},
    onLeave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(2.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ROOM PIN",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = roomPin,
            color = AccentGold,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 6.sp
        )
        val isLocalHost = players.any { it.isHost && (it.type == PlayerType.HUMAN || it.name.contains("(You)")) }

        Text(
            text = if (isLocalHost) "Share this 6-digit PIN with your friends to play!" else "Joined Room • Waiting for host to start match...",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CONNECTED PLAYERS (${players.size})",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        players.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(getColorForPlayer(p.color))
                    )
                    val cleanName = p.name.replace(" (You)", "").trim()
                    val isYou = (p.isHost && isLocalHost) || (!p.isHost && !isLocalHost)
                    val label = buildString {
                        append(cleanName)
                        if (isYou) append(" (You)")
                        if (p.isHost) append(" (Host)")
                    }
                    Text(
                        text = label,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ready",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Ready",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // When 1 player is connected in 2-player match: show appropriate preview for slot 2
        if (players.size == 1) {
            val player1 = players.first()
            val oppositeColor = player1.color.getOppositeColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated.copy(alpha = 0.6f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isLocalHost) getColorForPlayer(oppositeColor) else AccentGold)
                    )
                    Column {
                        Text(
                            text = if (isLocalHost) "Opponent (Waiting to join...)" else "Room Host (Syncing...)",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isLocalHost) "Yard: ${oppositeColor.displayName} (Diagonally Opposite)" else "Connecting to host...",
                            color = AccentGold,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Waiting",
                        color = AccentGold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
            val statusText = when {
                players.size >= 2 && isLocalHost -> "All players connected! Starting match..."
                players.size >= 2 -> "All players connected! Host starting match..."
                isLocalHost -> "Waiting for other players to join..."
                else -> "Connected as guest. Waiting for host to start..."
            }
            Text(
                text = statusText,
                color = AccentGold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLocalHost && players.size >= 2) {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
            ) {
                Text("START MATCH", color = Color(0xFF1E1B4B), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("LEAVE ROOM", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SearchingMatchCard(
    playerCount: Int,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentGold.copy(alpha = 0.4f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Matchmaking",
                tint = AccentGold,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SEARCHING FOR $playerCount-PLAYER MATCH",
            color = AccentGold,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connecting to players around the world...",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

