package com.ludobt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ludobt.app.data.bluetooth.BluetoothPermissionManager
import com.ludobt.app.domain.model.AiDifficulty
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.presentation.theme.BgDark
import com.ludobt.app.presentation.theme.LudoBtTheme
import com.ludobt.app.presentation.ui.screens.BluetoothLobbyScreen
import com.ludobt.app.presentation.ui.screens.GameScreen
import com.ludobt.app.presentation.ui.screens.HomeScreen
import com.ludobt.app.presentation.viewmodel.GameViewModel

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.SurfaceDark
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.ui.board.GotiBitmapProvider
import com.ludobt.app.presentation.ui.screens.OnlineLobbyScreen

enum class AppScreen {
    HOME,
    BLUETOOTH_LOBBY,
    ONLINE_LOBBY,
    GAME
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GotiBitmapProvider.preload(applicationContext)

        setContent {
            LudoBtTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                    var playerLeftDialogMessage by remember { mutableStateOf<String?>(null) }

                    // Permission launcher for Bluetooth
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val allGranted = permissions.values.all { it }
                        if (allGranted) {
                            viewModel.switchToRealTransport()
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (!BluetoothPermissionManager.hasAllPermissions(this@MainActivity)) {
                            permissionLauncher.launch(BluetoothPermissionManager.getRequiredPermissions())
                        }
                    }

                    val gameState by viewModel.gameState.collectAsState()
                    val canUndo by viewModel.canUndo.collectAsState()
                    val connectionStatus by viewModel.connectionStatus.collectAsState()
                    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                    val connectedPeers by viewModel.connectedPeers.collectAsState()
                    val bluetoothLobbyColors by viewModel.bluetoothLobbyColors.collectAsState()

                    // Online Lobby States
                    val onlineRoomPin by viewModel.onlineRoomPin.collectAsState()
                    val onlineLobbyPlayers by viewModel.onlineLobbyPlayers.collectAsState()
                    val onlineConnectionStatus by viewModel.onlineConnectionStatus.collectAsState()

                    // Synchronized, event-driven game transition for both Host and Client
                    LaunchedEffect(Unit) {
                        viewModel.onGameStarted.collect {
                            currentScreen = AppScreen.GAME
                        }
                    }

                    // Handle other player leaving room in Bluetooth or Online mode
                    LaunchedEffect(Unit) {
                        viewModel.onGameTerminatedByPeer.collect { reason ->
                            playerLeftDialogMessage = reason
                        }
                    }

                    when (currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                onStartBluetooth = {
                                    viewModel.prepareBluetoothMode()
                                    currentScreen = AppScreen.BLUETOOTH_LOBBY
                                },
                                onStartPassAndPlay = { configs ->
                                    viewModel.startPassAndPlay(configs)
                                },
                                onStartComputer = { difficulty, bots, userColor, userName ->
                                    viewModel.startVsComputer(difficulty, bots, userColor, userName)
                                },
                                onStartOnline = {
                                    currentScreen = AppScreen.ONLINE_LOBBY
                                }
                            )
                        }
                        AppScreen.BLUETOOTH_LOBBY -> {
                            BluetoothLobbyScreen(
                                connectionStatus = connectionStatus,
                                discoveredDevices = discoveredDevices,
                                connectedPeers = connectedPeers,
                                lobbyColors = bluetoothLobbyColors,
                                localDeviceName = viewModel.getLocalDeviceName(),
                                onScan = { viewModel.startBluetoothScan() },
                                onHostRoom = { count, hostColor -> viewModel.hostBluetoothRoom(count, hostColor) },
                                onHostColorChanged = { color, count -> viewModel.changeBluetoothHostColor(color, count) },
                                onSelectPeerColor = { color -> viewModel.requestBluetoothColorChange(color) },
                                onJoinRoom = { address ->
                                    viewModel.joinBluetoothRoom(address)
                                },
                                onStartGame = { count, colors ->
                                    viewModel.startBluetoothGame(count, colors)
                                },
                                onRequestDiscoverable = {
                                    try {
                                        val discoverableIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                            putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                                        }
                                        startActivity(discoverableIntent)
                                    } catch (_: Exception) {}
                                },
                                onBack = {
                                    viewModel.disconnect()
                                    viewModel.resetGame()
                                    currentScreen = AppScreen.HOME
                                }
                            )
                        }
                        AppScreen.ONLINE_LOBBY -> {
                            OnlineLobbyScreen(
                                connectionStatus = onlineConnectionStatus,
                                roomPin = onlineRoomPin,
                                lobbyPlayers = onlineLobbyPlayers,
                                errorEvents = viewModel.onlineError,
                                onQuickMatch = { count, name, color ->
                                    viewModel.startOnlineQuickMatch(count, name, color)
                                },
                                onCreateRoom = { count, name, color ->
                                    viewModel.createOnlineRoom(count, name, color)
                                },
                                onJoinRoom = { pin, name, color ->
                                    viewModel.joinOnlineRoom(pin, name, color)
                                },
                                onStartGame = {
                                    viewModel.startOnlineGameAsHost()
                                },
                                onLeaveLobby = {
                                    viewModel.leaveOnlineLobby()
                                    viewModel.prepareBluetoothMode()
                                },
                                onBack = {
                                    viewModel.leaveOnlineLobby()
                                    viewModel.prepareBluetoothMode()
                                    currentScreen = AppScreen.HOME
                                }
                            )
                        }
                        AppScreen.GAME -> {
                            GameScreen(
                                gameState = gameState,
                                canUndo = canUndo,
                                onUndoMove = { viewModel.undoMove() },
                                onRollDice = { viewModel.rollDice() },
                                onTokenSelected = { tokenId -> viewModel.selectToken(tokenId) },
                                onRematch = { viewModel.rematch() },
                                onQuit = {
                                    viewModel.quitGame()
                                    currentScreen = AppScreen.HOME
                                }
                            )
                        }
                    }

                    // Dialog shown when another player leaves Bluetooth or Online match
                    if (playerLeftDialogMessage != null) {
                        AlertDialog(
                            onDismissRequest = {
                                playerLeftDialogMessage = null
                                viewModel.quitGame()
                                currentScreen = AppScreen.HOME
                            },
                            title = {
                                Text("Match Ended", color = AccentGold, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(
                                    text = playerLeftDialogMessage ?: "A player has left the room. Game ended for all players.",
                                    color = TextPrimary
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        playerLeftDialogMessage = null
                                        viewModel.quitGame()
                                        currentScreen = AppScreen.HOME
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                                ) {
                                    Text("Back to Home", color = Color(0xFF1E1B4B), fontWeight = FontWeight.Bold)
                                }
                            },
                            containerColor = SurfaceDark
                        )
                    }
                }
            }
        }
    }
}
