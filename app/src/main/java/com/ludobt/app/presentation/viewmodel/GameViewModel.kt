package com.ludobt.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ludobt.app.data.bluetooth.BluetoothClassicTransport
import com.ludobt.app.data.bluetooth.BluetoothPermissionManager
import com.ludobt.app.data.bluetooth.SimulatedBluetoothTransport
import com.ludobt.app.domain.model.AiDifficulty
import com.ludobt.app.domain.model.GameMode
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.session.SessionManager
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.data.cloud.CloudGameTransport
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.transport.DiscoveredDevice
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothTransport = BluetoothClassicTransport(application)
    val onlineTransport = CloudGameTransport()
    private var activeTransport: GameTransport = bluetoothTransport
    val transport: GameTransport get() = activeTransport

    private val sessionManager = SessionManager(bluetoothTransport)

    val gameState: StateFlow<GameState> = sessionManager.gameState
    val canUndo: StateFlow<Boolean> = sessionManager.canUndo
    val onGameStarted: SharedFlow<Unit> = sessionManager.onGameStarted
    val onGameTerminatedByPeer: SharedFlow<String> = sessionManager.onGameTerminatedByPeer
    val connectionStatus: StateFlow<ConnectionStatus> = bluetoothTransport.connectionStatus
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bluetoothTransport.discoveredDevices
    val connectedPeers: StateFlow<List<String>> = bluetoothTransport.connectedPeers
    val bluetoothLobbyColors: StateFlow<List<PlayerColor>> = bluetoothTransport.lobbyColors

    // Online Multiplayer State Flows
    val onlineRoomPin: StateFlow<String?> = onlineTransport.currentRoomPin
    val onlineLobbyPlayers: StateFlow<List<Player>> = onlineTransport.lobbyPlayers
    val onlineConnectionStatus: StateFlow<ConnectionStatus> = onlineTransport.connectionStatus
    val onlineError: Flow<String> = onlineTransport.lastError

    init {
        viewModelScope.launch {
            onlineTransport.lobbyPlayers.collect { players ->
                if (onlineTransport.isQuickMatch() && onlineTransport.isHostRole() && players.size >= 2 && gameState.value.status != com.ludobt.app.domain.model.GameStatus.IN_PROGRESS) {
                    startOnlineGameAsHost()
                }
            }
        }
    }

    fun startOnlineQuickMatch(playerCount: Int, playerName: String, color: PlayerColor) {
        sessionManager.setTransport(onlineTransport)
        activeTransport = onlineTransport
        viewModelScope.launch {
            onlineTransport.quickMatch(playerCount, playerName, color)
        }
    }

    fun createOnlineRoom(playerCount: Int, playerName: String, color: PlayerColor) {
        sessionManager.setTransport(onlineTransport)
        activeTransport = onlineTransport
        viewModelScope.launch {
            onlineTransport.createRoom(playerCount, playerName, color)
        }
    }

    fun joinOnlineRoom(roomPin: String, playerName: String, color: PlayerColor) {
        sessionManager.setTransport(onlineTransport)
        activeTransport = onlineTransport
        viewModelScope.launch {
            onlineTransport.joinRoom(roomPin, playerName, color)
        }
    }

    fun startOnlineGameAsHost() {
        val players = onlineTransport.lobbyPlayers.value
        if (players.size < 2) return

        val configs = players.mapIndexed { index, p ->
            val cleanName = p.name.replace(" (You)", "").trim().ifBlank { "Player ${index + 1}" }
            val type = if (index == 0) PlayerType.HUMAN else PlayerType.BLUETOOTH_REMOTE
            cleanName to type
        }
        val assignedColors = players.map { it.color }
        val playerIds = players.map { it.id }

        sessionManager.startOnlineGameAsHost(
            playerConfigs = configs,
            playerIds = playerIds,
            colors = assignedColors
        )
    }

    fun leaveOnlineLobby() {
        viewModelScope.launch {
            onlineTransport.disconnect()
        }
    }

    fun getLocalDeviceName(): String = bluetoothTransport.getLocalDeviceName()

    fun resetGame() {
        prepareBluetoothMode()
        sessionManager.resetGame()
    }

    fun switchToRealTransport() {
        // Kept for interface compatibility
    }

    fun startPassAndPlay(playerConfigs: List<Pair<String, com.ludobt.app.domain.model.PlayerColor>>) {
        val configs = playerConfigs.map { (name, _) -> name to PlayerType.HUMAN }
        val colors = playerConfigs.map { (_, color) -> color }
        sessionManager.startNewGame(
            mode = GameMode.PASS_AND_PLAY,
            playerConfigs = configs,
            colors = colors
        )
    }

    fun startPassAndPlay(playerCount: Int = 4) {
        val colors = when (playerCount) {
            2 -> listOf(com.ludobt.app.domain.model.PlayerColor.RED, com.ludobt.app.domain.model.PlayerColor.YELLOW)
            3 -> listOf(com.ludobt.app.domain.model.PlayerColor.RED, com.ludobt.app.domain.model.PlayerColor.GREEN, com.ludobt.app.domain.model.PlayerColor.YELLOW)
            else -> listOf(com.ludobt.app.domain.model.PlayerColor.RED, com.ludobt.app.domain.model.PlayerColor.GREEN, com.ludobt.app.domain.model.PlayerColor.YELLOW, com.ludobt.app.domain.model.PlayerColor.BLUE)
        }
        val configs = (1..playerCount).map { "Player $it" to colors[it - 1] }
        startPassAndPlay(configs)
    }

    fun startVsComputer(
        difficulty: AiDifficulty,
        botCount: Int,
        userColor: com.ludobt.app.domain.model.PlayerColor = com.ludobt.app.domain.model.PlayerColor.BLUE,
        userName: String = "You"
    ) {
        val configs = mutableListOf<Pair<String, PlayerType>>()
        val colors = mutableListOf<com.ludobt.app.domain.model.PlayerColor>()

        // Human player is added first with their chosen color
        configs.add(userName.ifBlank { "You" } to PlayerType.HUMAN)
        colors.add(userColor)

        val allColors = listOf(
            com.ludobt.app.domain.model.PlayerColor.RED,
            com.ludobt.app.domain.model.PlayerColor.GREEN,
            com.ludobt.app.domain.model.PlayerColor.YELLOW,
            com.ludobt.app.domain.model.PlayerColor.BLUE
        )

        when (botCount) {
            1 -> {
                // In 2-player match: AI opponent is ALWAYS placed in the diagonally opposite yard!
                val oppositeColor = when (userColor) {
                    com.ludobt.app.domain.model.PlayerColor.RED -> com.ludobt.app.domain.model.PlayerColor.YELLOW
                    com.ludobt.app.domain.model.PlayerColor.YELLOW -> com.ludobt.app.domain.model.PlayerColor.RED
                    com.ludobt.app.domain.model.PlayerColor.BLUE -> com.ludobt.app.domain.model.PlayerColor.GREEN
                    com.ludobt.app.domain.model.PlayerColor.GREEN -> com.ludobt.app.domain.model.PlayerColor.BLUE
                }
                configs.add("Computer 1" to PlayerType.AI)
                colors.add(oppositeColor)
            }
            2 -> {
                // In 3-player match: pick 2 remaining colors for the 2 AI bots
                val remaining = allColors.filter { it != userColor }
                configs.add("Computer 1" to PlayerType.AI)
                colors.add(remaining[0])
                configs.add("Computer 2" to PlayerType.AI)
                colors.add(remaining[1])
            }
            else -> {
                // In 4-player match: 3 AI bots take the other 3 colors
                val remaining = allColors.filter { it != userColor }
                configs.add("Computer 1" to PlayerType.AI)
                colors.add(remaining[0])
                configs.add("Computer 2" to PlayerType.AI)
                colors.add(remaining[1])
                configs.add("Computer 3" to PlayerType.AI)
                colors.add(remaining[2])
            }
        }

        sessionManager.startNewGame(
            mode = GameMode.VS_COMPUTER,
            playerConfigs = configs,
            difficulty = difficulty,
            colors = colors,
            startingPlayerIndex = 0 // Human always starts with their chosen color
        )
    }

    fun prepareBluetoothMode() {
        activeTransport = bluetoothTransport
        sessionManager.setTransport(bluetoothTransport)
    }

    fun startBluetoothScan() {
        prepareBluetoothMode()
        viewModelScope.launch {
            bluetoothTransport.startDiscovery()
        }
    }

    fun hostBluetoothRoom(playerCount: Int = 2, hostColor: PlayerColor = PlayerColor.RED) {
        prepareBluetoothMode()
        bluetoothTransport.setHostColor(hostColor, playerCount)
        viewModelScope.launch {
            bluetoothTransport.startHost("Ludo Nearby Room", maxPlayers = playerCount)
        }
    }

    fun changeBluetoothHostColor(color: PlayerColor, playerCount: Int) {
        bluetoothTransport.setHostColor(color, playerCount)
    }

    fun requestBluetoothColorChange(color: PlayerColor) {
        viewModelScope.launch {
            bluetoothTransport.requestColorChange(color)
        }
    }

    fun changeOnlineColor(color: PlayerColor) {
        viewModelScope.launch {
            onlineTransport.changeOnlineColor(color)
        }
    }

    fun joinBluetoothRoom(deviceAddress: String) {
        prepareBluetoothMode()
        viewModelScope.launch {
            bluetoothTransport.joinHost(deviceAddress)
        }
    }

    fun startBluetoothGame(playerCount: Int = 2, colors: List<PlayerColor>? = null) {
        prepareBluetoothMode()
        val requiredPeers = playerCount - 1
        val peers = bluetoothTransport.connectedPeers.value
        // Strict check: all members of selected room must have joined
        if (peers.size < requiredPeers) return

        val configs = mutableListOf<Pair<String, PlayerType>>()
        val myHostName = getLocalDeviceName()
        configs.add(myHostName to PlayerType.HUMAN)
        for (i in 2..playerCount) {
            val peerName = peers.getOrNull(i - 2)?.ifBlank { "Player $i" } ?: "Player $i"
            configs.add(peerName to PlayerType.BLUETOOTH_REMOTE)
        }
        val assignedColors = colors ?: bluetoothTransport.lobbyColors.value
        sessionManager.startBluetoothGameAsHost(
            playerConfigs = configs,
            colors = assignedColors
        )
    }

    fun rollDice() {
        sessionManager.rollDice()
    }

    fun selectToken(tokenId: Int) {
        sessionManager.moveToken(tokenId)
    }

    fun undoMove() {
        sessionManager.undoMove()
    }

    fun rematch() {
        val current = gameState.value
        val configs = current.players.map { it.name to it.type }
        val colors = current.players.map { it.color }
        val humanIndex = configs.indexOfFirst { it.second == PlayerType.HUMAN }.coerceAtLeast(0)
        sessionManager.startNewGame(
            mode = current.mode,
            playerConfigs = configs,
            difficulty = current.aiDifficulty,
            colors = colors,
            startingPlayerIndex = humanIndex
        )
    }

    fun quitGame() {
        viewModelScope.launch {
            sessionManager.quitGame()
            bluetoothTransport.disconnect()
            onlineTransport.disconnect()
            prepareBluetoothMode()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bluetoothTransport.disconnect()
            onlineTransport.disconnect()
            prepareBluetoothMode()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.cleanup()
        disconnect()
    }
}
