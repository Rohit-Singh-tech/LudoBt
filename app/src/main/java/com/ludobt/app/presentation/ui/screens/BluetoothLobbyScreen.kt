package com.ludobt.app.presentation.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.domain.transport.DiscoveredDevice
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothLobbyScreen(
    connectionStatus: ConnectionStatus,
    discoveredDevices: List<DiscoveredDevice>,
    connectedPeers: List<String>,
    lobbyColors: List<PlayerColor> = emptyList(),
    localDeviceName: String,
    onScan: () -> Unit,
    onHostRoom: (playerCount: Int, hostColor: PlayerColor) -> Unit,
    onHostColorChanged: (PlayerColor, Int) -> Unit = { _, _ -> },
    onSelectPeerColor: (PlayerColor) -> Unit = {},
    onJoinRoom: (String) -> Unit,
    onStartGame: (playerCount: Int, colors: List<PlayerColor>) -> Unit,
    onRequestDiscoverable: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Join, 1: Create
    var selectedPlayerCount by remember { mutableIntStateOf(2) } // Default to 2 players (Opposite Courts)
    var selectedHostColor by remember { mutableStateOf(PlayerColor.RED) }
    var isHostingActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onScan()
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = LudoBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bluetooth Multiplayer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Local Device Identification Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(SurfaceDark, SurfaceElevated)))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(LudoBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = LudoBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Your Device Name",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = localDeviceName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRequestDiscoverable,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Visible", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs: Join vs Create
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = AccentGold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AccentGold
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Join Nearby Game", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Create Room", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // JOIN TAB
                JoinRoomContent(
                    connectionStatus = connectionStatus,
                    connectedPeers = connectedPeers,
                    devices = discoveredDevices,
                    lobbyColors = lobbyColors,
                    onSelectPeerColor = onSelectPeerColor,
                    onJoinDevice = onJoinRoom,
                    onScanAgain = onScan
                )
            } else {
                // CREATE (HOST) TAB
                CreateRoomContent(
                    playerCount = selectedPlayerCount,
                    hostColor = selectedHostColor,
                    lobbyColors = lobbyColors,
                    isHosting = isHostingActive || connectionStatus == ConnectionStatus.CONNECTED_AS_HOST,
                    connectedPeers = connectedPeers,
                    localDeviceName = localDeviceName,
                    onPlayerCountSelected = { count ->
                        selectedPlayerCount = count
                        onHostColorChanged(selectedHostColor, count)
                    },
                    onHostColorChange = { color ->
                        selectedHostColor = color
                        onHostColorChanged(color, selectedPlayerCount)
                    },
                    onStartHosting = {
                        isHostingActive = true
                        onRequestDiscoverable()
                        onHostRoom(selectedPlayerCount, selectedHostColor)
                    },
                    onStartGame = { colors -> onStartGame(selectedPlayerCount, colors) }
                )
            }
        }
    }
}

@Composable
private fun JoinRoomContent(
    connectionStatus: ConnectionStatus,
    connectedPeers: List<String>,
    devices: List<DiscoveredDevice>,
    lobbyColors: List<PlayerColor>,
    onSelectPeerColor: (PlayerColor) -> Unit,
    onJoinDevice: (String) -> Unit,
    onScanAgain: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        when (connectionStatus) {
            ConnectionStatus.CONNECTING -> {
                // Connecting Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(radarScale)
                                .clip(CircleShape)
                                .background(AccentGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting via Bluetooth...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Handshaking with Host",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            ConnectionStatus.CONNECTED_AS_CLIENT -> {
                // Connected Card: Waiting for Host to Start
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.5.dp, LudoGreen, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(LudoGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LudoGreen,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connected to Host!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = LudoGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val hostName = connectedPeers.firstOrNull() ?: "Host"
                        val hostColor = lobbyColors.firstOrNull() ?: PlayerColor.RED
                        Text(
                            text = "Room Host: $hostName",
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (lobbyColors.size <= 2) {
                            // 2-Player Match: Diagonally Opposite Court is automatically selected
                            val myColor = lobbyColors.getOrNull(1) ?: hostColor.getOppositeColor()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(getColorForPlayer(myColor))
                                )
                                Text(
                                    text = "Your Yard: ${myColor.displayName} (Opposite Court)",
                                    fontSize = 13.sp,
                                    color = getColorForPlayer(myColor),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // 4-Player Match: Participant chooses from unselected colors
                            val myColor = lobbyColors.getOrNull(1) ?: PlayerColor.GREEN
                            val takenByOthers = listOf(hostColor) + lobbyColors.drop(2)
                            Text(
                                text = "Choose Your Yard Color:",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).forEach { c ->
                                    val isTaken = takenByOthers.contains(c) && c != myColor
                                    SelectableGotiItem(
                                        color = c,
                                        isSelected = myColor == c,
                                        isEnabled = !isTaken,
                                        onClick = { onSelectPeerColor(c) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for host to tap 'Start Game'...",
                                fontSize = 13.sp,
                                color = AccentGold,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            else -> {
                // Scan / Discovery List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Nearby Devices (${devices.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (connectionStatus == ConnectionStatus.SCANNING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(radarScale)
                                    .clip(CircleShape)
                                    .background(AccentGold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Scanning...", fontSize = 12.sp, color = AccentGold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(56.dp)
                                    .scale(radarScale)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Searching for nearby hosts...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Make sure the other phone clicked 'Open Bluetooth Lobby'",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = onScanAgain) {
                                Text("Scan Again", color = AccentGold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(devices) { device ->
                            DeviceItemCard(device = device, onJoin = { onJoinDevice(device.address) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItemCard(
    device: DiscoveredDevice,
    onJoin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LudoGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = LudoGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = device.address,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(containerColor = LudoBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Join", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CreateRoomContent(
    playerCount: Int,
    hostColor: PlayerColor,
    lobbyColors: List<PlayerColor>,
    isHosting: Boolean,
    connectedPeers: List<String>,
    localDeviceName: String,
    onPlayerCountSelected: (Int) -> Unit,
    onHostColorChange: (PlayerColor) -> Unit,
    onStartHosting: () -> Unit,
    onStartGame: (List<PlayerColor>) -> Unit
) {
    val colors = if (playerCount == 2) {
        listOf(hostColor, hostColor.getOppositeColor())
    } else {
        if (lobbyColors.size >= playerCount) lobbyColors else {
            val remaining = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).filter { it != hostColor }
            (listOf(hostColor) + remaining).take(playerCount)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Select Number of Players",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(2, 3, 4).forEach { count ->
                    val isSelected = playerCount == count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentGold else SurfaceDark)
                            .border(
                                1.dp,
                                if (isSelected) AccentGold else BorderSubtle,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isHosting) { onPlayerCountSelected(count) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$count Players",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontSize = 13.sp
                            )
                            if (count == 2) {
                                Text(
                                    text = "Opposite Courts",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color(0xFF1E293B) else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your Court Color (Host)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).forEach { c ->
                    SelectableGotiItem(
                        color = c,
                        isSelected = hostColor == c,
                        isEnabled = !isHosting,
                        onClick = { onHostColorChange(c) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (playerCount == 2) {
                Text(
                    text = "🎯 Opponent Yard: Automatically selected as ${hostColor.getOppositeColor().displayName} (Diagonally Opposite)",
                    fontSize = 11.sp,
                    color = AccentGold
                )
            } else {
                Text(
                    text = "🎲 4-Player Match: Participants will choose from remaining unselected colors.",
                    fontSize = 11.sp,
                    color = AccentGold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Lobby Slots (${if (isHosting) "${connectedPeers.size + 1}/$playerCount Ready" else "Ready to Open"})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            for (i in 0 until playerCount) {
                val color = colors.getOrElse(i) { PlayerColor.RED }
                val isHostSlot = i == 0
                val peerName = connectedPeers.getOrNull(i - 1)
                val isConnected = isHostSlot || peerName != null

                val displayName = if (isHostSlot) {
                    "Host (You - $localDeviceName)"
                } else if (peerName != null) {
                    "$peerName (Connected)"
                } else if (isHosting) {
                    "Waiting for Friend to join..."
                } else {
                    "Open Lobby to Invite Player ${i + 1}"
                }

                LobbySlotCard(
                    slotNumber = i + 1,
                    playerColor = color,
                    playerName = displayName,
                    isReady = isConnected
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Action Buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isHosting) {
                Button(
                    onClick = onStartHosting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Bluetooth Lobby ($playerCount Players)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            } else {
                val requiredPeers = playerCount - 1
                val hasAllMembersJoined = connectedPeers.size >= requiredPeers

                if (!hasAllMembersJoined) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📡 Lobby Open ($playerCount Players): On friends' phones, tap 'Join Nearby Game' and select '$localDeviceName'",
                            fontSize = 12.sp,
                            color = AccentGold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { onStartGame(colors) },
                    enabled = hasAllMembersJoined,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LudoGreen,
                        disabledContainerColor = SurfaceElevated
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (hasAllMembersJoined) Color.White else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasAllMembersJoined) "Start Game Now 🎲 ($playerCount Players Ready)" else "Waiting for Friends (${connectedPeers.size}/$requiredPeers Joined)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasAllMembersJoined) Color.White else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbySlotCard(
    slotNumber: Int,
    playerColor: PlayerColor,
    playerName: String,
    isReady: Boolean
) {
    val color = getColorForPlayer(playerColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (isReady) color.copy(alpha = 0.6f) else BorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$slotNumber",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = playerName,
                        fontSize = 13.sp,
                        fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal,
                        color = if (isReady) TextPrimary else TextSecondary
                    )
                    Text(
                        text = "Court: ${playerColor.name}",
                        fontSize = 11.sp,
                        color = color
                    )
                }
            }

            if (isReady) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Ready",
                    tint = LudoGreen,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "Waiting",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
