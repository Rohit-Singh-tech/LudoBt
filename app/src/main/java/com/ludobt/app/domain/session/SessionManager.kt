package com.ludobt.app.domain.session

import com.ludobt.app.domain.engine.LudoAiEngine
import com.ludobt.app.domain.engine.LudoGameEngine
import com.ludobt.app.domain.model.AiDifficulty
import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.model.GameMode
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.GameStatus
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionManager(
    private var transport: GameTransport? = null
) {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val stateHistory = ArrayDeque<GameState>()

    private val _onGameStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onGameStarted: SharedFlow<Unit> = _onGameStarted.asSharedFlow()

    private val _onGameTerminatedByPeer = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val onGameTerminatedByPeer: SharedFlow<String> = _onGameTerminatedByPeer.asSharedFlow()

    private var aiJob: Job? = null
    private var transportJob: Job? = null

    init {
        bindTransport(transport)
    }

    fun setTransport(newTransport: GameTransport) {
        this.transport = newTransport
        bindTransport(newTransport)
    }

    private fun bindTransport(tp: GameTransport?) {
        transportJob?.cancel()
        if (tp == null) return
        transportJob = sessionScope.launch {
            tp.incomingMessages.collect { msg ->
                handleIncomingTransportMessage(msg)
            }
        }
    }

    /**
     * Start a new game session.
     */
    fun startNewGame(
        mode: GameMode,
        playerConfigs: List<Pair<String, PlayerType>>,
        difficulty: AiDifficulty = AiDifficulty.MEDIUM,
        colors: List<PlayerColor>? = null,
        startingPlayerIndex: Int = 0
    ) {
        aiJob?.cancel()
        stateHistory.clear()
        _canUndo.value = false
        val initial = LudoGameEngine.createInitialGame(mode, playerConfigs, colors).copy(
            aiDifficulty = difficulty,
            currentPlayerIndex = startingPlayerIndex,
            lastMoveDescription = "${playerConfigs.getOrNull(startingPlayerIndex)?.first ?: "Player"}'s turn. Roll the dice!"
        )
        _gameState.value = initial
        _onGameStarted.tryEmit(Unit)

        // If Bluetooth host, send GameStart to clients
        sessionScope.launch {
            transport?.sendMessage(GameMessage.StartGame(initial))
        }

        checkAndTriggerAi()
    }

    /**
     * Request a dice roll.
     */
    fun rollDice() {
        val current = _gameState.value
        if (current.status != GameStatus.IN_PROGRESS) return
        if (current.isRolling || current.diceValue != null) return

        val currentPlayer = current.currentPlayer ?: return
        if (currentPlayer.type != PlayerType.HUMAN) return // STRICT: Only local human player can roll!

        // Save state snapshot for undo before roll
        if (current.mode == GameMode.VS_COMPUTER || current.mode == GameMode.PASS_AND_PLAY) {
            if (stateHistory.size >= 50) stateHistory.removeFirst()
            stateHistory.addLast(current.copy(isRolling = false))
            _canUndo.value = true
        }

        // Set rolling animation flag briefly
        _gameState.value = current.copy(isRolling = true)

        sessionScope.launch {
            delay(400) // Realistic dice roll duration
            val afterRoll = LudoGameEngine.rollDice(_gameState.value)
            _gameState.value = afterRoll

            // Broadcast to transport if connected
            transport?.sendMessage(
                GameMessage.DiceRolledEvent(
                    playerId = currentPlayer.id,
                    value = afterRoll.diceValue ?: 1,
                    validTokenIds = afterRoll.validTokenIds,
                    seq = afterRoll.sequenceNumber
                )
            )

            if (afterRoll.validTokenIds.isEmpty()) {
                // Keep the rolled number visible for 900ms so the player can see it, then pass turn!
                delay(900)
                val nextState = LudoGameEngine.passTurnToNextPlayer(_gameState.value)
                _gameState.value = nextState

                // Broadcast turn passed event so all remote peers advance turn in lockstep
                transport?.sendMessage(
                    GameMessage.TurnTimeoutEvent(
                        playerId = currentPlayer.id,
                        nextPlayerIndex = nextState.currentPlayerIndex,
                        seq = nextState.sequenceNumber
                    )
                )

                // If connected as host, also send FullStateSync
                if (transport?.connectionStatus?.value == com.ludobt.app.domain.transport.ConnectionStatus.CONNECTED_AS_HOST) {
                    transport?.sendMessage(GameMessage.FullStateSync(nextState))
                }

                checkAndTriggerAi()
            } else if (afterRoll.validTokenIds.size == 1 && afterRoll.currentPlayer?.type == PlayerType.HUMAN && afterRoll.diceValue != 6) {
                delay(350)
                moveToken(afterRoll.validTokenIds.first())
            } else {
                checkAndTriggerAi()
            }
        }
    }

    /**
     * Request token movement.
     */
    fun moveToken(tokenId: Int) {
        val current = _gameState.value
        if (current.status != GameStatus.IN_PROGRESS) return
        if (current.diceValue == null) return
        if (!current.validTokenIds.contains(tokenId)) return
        val currentPlayer = current.currentPlayer ?: return
        if (currentPlayer.type != PlayerType.HUMAN) return // STRICT: Only local human player can move!

        // Save state snapshot for undo before token moves
        if (current.mode == GameMode.VS_COMPUTER || current.mode == GameMode.PASS_AND_PLAY) {
            if (stateHistory.size >= 50) stateHistory.removeFirst()
            stateHistory.addLast(current.copy(isRolling = false))
            _canUndo.value = true
        }

        val moveResult = LudoGameEngine.moveToken(current, tokenId)
        _gameState.value = moveResult.nextState

        // Broadcast token move to multiplayer peers
        sessionScope.launch {
            transport?.sendMessage(
                GameMessage.TokenMovedEvent(
                    playerId = current.currentPlayer?.id ?: "",
                    tokenId = tokenId,
                    fromStep = moveResult.fromStep,
                    toStep = moveResult.toStep,
                    capturedPlayerColor = moveResult.capturedToken?.color,
                    capturedTokenId = moveResult.capturedToken?.id,
                    extraTurnGranted = moveResult.extraTurnGranted,
                    nextPlayerIndex = moveResult.nextState.currentPlayerIndex,
                    seq = moveResult.nextState.sequenceNumber
                )
            )

            // If connected as host, also broadcast authoritative FullStateSync
            if (transport?.connectionStatus?.value == com.ludobt.app.domain.transport.ConnectionStatus.CONNECTED_AS_HOST) {
                transport?.sendMessage(GameMessage.FullStateSync(moveResult.nextState))
            }

            checkAndTriggerAi()
        }
    }

    /**
     * Handle automated turns for AI players.
     */
    private fun checkAndTriggerAi() {
        val current = _gameState.value
        if (current.status != GameStatus.IN_PROGRESS) return

        val currentPlayer = current.currentPlayer ?: return
        if (currentPlayer.type != PlayerType.AI) return

        aiJob?.cancel()
        aiJob = sessionScope.launch {
            delay(600) // Natural thinking delay before rolling
            if (_gameState.value.diceValue == null) {
                _gameState.value = _gameState.value.copy(isRolling = true)
                delay(350)
                val rolledState = LudoGameEngine.rollDice(_gameState.value)
                _gameState.value = rolledState

                if (rolledState.validTokenIds.isNotEmpty()) {
                    delay(700) // Natural pause before moving
                    val chosenTokenId = LudoAiEngine.selectBestMove(
                        rolledState,
                        currentPlayer,
                        rolledState.validTokenIds
                    )
                    if (chosenTokenId != -1) {
                        val moveResult = LudoGameEngine.moveToken(rolledState, chosenTokenId)
                        _gameState.value = moveResult.nextState
                        checkAndTriggerAi()
                    }
                } else {
                    // AI rolled a number with no valid moves: show roll for 900ms then pass turn!
                    delay(900)
                    val nextState = LudoGameEngine.passTurnToNextPlayer(_gameState.value)
                    _gameState.value = nextState
                    checkAndTriggerAi()
                }
            }
        }
    }

    fun startBluetoothGameAsHost(
        playerConfigs: List<Pair<String, PlayerType>>,
        colors: List<PlayerColor>? = null
    ) {
        aiJob?.cancel()
        val initial = LudoGameEngine.createInitialGame(GameMode.BLUETOOTH_NEARBY, playerConfigs, colors)
        _gameState.value = initial
        _onGameStarted.tryEmit(Unit)

        sessionScope.launch {
            transport?.sendMessage(GameMessage.StartGame(initialGameState = initial, assignedPlayerIndex = 1))
        }
    }

    fun startOnlineGameAsHost(
        playerConfigs: List<Pair<String, PlayerType>>,
        playerIds: List<String>,
        colors: List<PlayerColor>
    ) {
        aiJob?.cancel()
        stateHistory.clear()
        _canUndo.value = false

        val initial = LudoGameEngine.createInitialGame(GameMode.ONLINE, playerConfigs, colors)
        val hostPlayers = initial.players.mapIndexed { index, player ->
            val realId = playerIds.getOrNull(index) ?: player.id
            val isLocal = index == 0
            val cleanName = player.name.replace(" (You)", "").trim()
            player.copy(
                id = realId,
                name = if (isLocal) "$cleanName (You)" else cleanName,
                type = if (isLocal) PlayerType.HUMAN else PlayerType.BLUETOOTH_REMOTE
            )
        }
        val hostInitialState = initial.copy(
            players = hostPlayers,
            currentPlayerIndex = 0,
            lastMoveDescription = "${hostPlayers[0].name}'s turn. Roll the dice!"
        )
        _gameState.value = hostInitialState
        _onGameStarted.tryEmit(Unit)

        sessionScope.launch {
            // Send clean StartGame to all guests without "(You)" in names
            val networkPlayers = hostPlayers.map {
                it.copy(name = it.name.replace(" (You)", "").trim())
            }
            val networkInitialState = hostInitialState.copy(players = networkPlayers)
            transport?.sendMessage(
                GameMessage.StartGame(
                    initialGameState = networkInitialState,
                    assignedPlayerIndex = 1
                )
            )
        }
    }

    private fun handleIncomingTransportMessage(msg: GameMessage) {
        when (msg) {
            is GameMessage.StartGame -> {
                val localId = transport?.getLocalPlayerId()
                var seat = -1

                // 1. Primary: Exact match by unique player ID
                if (!localId.isNullOrBlank()) {
                    seat = msg.initialGameState.players.indexOfFirst { it.id == localId }
                }

                // 2. Secondary: Assigned player index if valid
                if (seat < 0 && msg.assignedPlayerIndex in msg.initialGameState.players.indices) {
                    seat = msg.assignedPlayerIndex
                }

                // 3. Fallback: Exact clean name match (never startsWith)
                if (seat < 0) {
                    val localName = transport?.getLocalDeviceName()?.replace(" (You)", "")?.trim() ?: ""
                    if (localName.isNotBlank()) {
                        seat = msg.initialGameState.players.indexOfFirst {
                            it.name.replace(" (You)", "").trim().equals(localName, ignoreCase = true)
                        }
                    }
                }

                if (seat < 0) seat = 1 // Safe fallback for guests

                val clientPlayers = msg.initialGameState.players.mapIndexed { idx, player ->
                    val cleanName = player.name.replace(" (You)", "").trim()
                    if (idx == seat) {
                        player.copy(type = PlayerType.HUMAN, name = "$cleanName (You)")
                    } else {
                        player.copy(type = PlayerType.BLUETOOTH_REMOTE, name = cleanName)
                    }
                }
                _gameState.value = msg.initialGameState.copy(
                    players = clientPlayers,
                    mode = msg.initialGameState.mode
                )
                _onGameStarted.tryEmit(Unit)
            }
            is GameMessage.FullStateSync -> {
                val currentPlayers = _gameState.value.players
                val synchronizedPlayers = msg.gameState.players.map { serverPl ->
                    val local = currentPlayers.find { it.id == serverPl.id }
                    if (local != null) {
                        serverPl.copy(type = local.type, name = local.name)
                    } else {
                        serverPl
                    }
                }
                _gameState.value = msg.gameState.copy(
                    players = synchronizedPlayers,
                    mode = _gameState.value.mode
                )
            }
            is GameMessage.DiceRolledEvent -> {
                val current = _gameState.value
                val rollingPlayer = current.players.find { it.id == msg.playerId }
                // STRICT: Only process dice rolls from remote players. Local human roll is already handled locally in rollDice().
                if (rollingPlayer?.type == PlayerType.BLUETOOTH_REMOTE || current.currentPlayer?.type == PlayerType.BLUETOOTH_REMOTE) {
                    _gameState.value = current.copy(
                        diceValue = msg.value,
                        isRolling = false,
                        validTokenIds = msg.validTokenIds
                    )
                    if (msg.validTokenIds.isEmpty()) {
                        sessionScope.launch {
                            delay(900)
                            val nextState = LudoGameEngine.passTurnToNextPlayer(_gameState.value)
                            _gameState.value = nextState
                        }
                    }
                }
            }
            is GameMessage.TurnTimeoutEvent -> {
                val current = _gameState.value
                if (current.currentPlayerIndex != msg.nextPlayerIndex) {
                    val nextState = LudoGameEngine.passTurnToNextPlayer(current)
                    _gameState.value = nextState.copy(
                        currentPlayerIndex = msg.nextPlayerIndex,
                        sequenceNumber = msg.seq,
                        diceValue = null,
                        isRolling = false,
                        validTokenIds = emptyList()
                    )
                }
            }
            is GameMessage.TokenMovedEvent -> {
                val current = _gameState.value
                val movingPlayer = current.players.find { it.id == msg.playerId }
                // STRICT: Only process moves from remote players. Local human moves are already applied locally in moveToken().
                if (movingPlayer?.type == PlayerType.BLUETOOTH_REMOTE || current.currentPlayer?.type == PlayerType.BLUETOOTH_REMOTE) {
                    val moveResult = LudoGameEngine.moveToken(current, msg.tokenId)
                    _gameState.value = moveResult.nextState.copy(
                        currentPlayerIndex = msg.nextPlayerIndex,
                        sequenceNumber = msg.seq
                    )
                }
            }
            is GameMessage.PlayerDisconnected -> {
                val updatedPlayers = _gameState.value.players.map {
                    if (it.id == msg.playerId) it.copy(isConnected = false) else it
                }
                val leavingPlayerName = _gameState.value.players.find { it.id == msg.playerId }?.name?.replace(" (You)", "") ?: "A player"
                _gameState.value = _gameState.value.copy(
                    players = updatedPlayers,
                    status = GameStatus.GAME_OVER,
                    lastMoveDescription = "$leavingPlayerName left the room. Game ended."
                )
                _onGameTerminatedByPeer.tryEmit("$leavingPlayerName has left the match. The game is closed for all players.")
            }
            else -> {}
        }
    }

    fun undoMove() {
        val current = _gameState.value
        if (current.mode != GameMode.VS_COMPUTER && current.mode != GameMode.PASS_AND_PLAY) return
        if (stateHistory.isEmpty()) return

        aiJob?.cancel()
        aiJob = null

        var targetState: GameState? = null
        while (stateHistory.isNotEmpty()) {
            val candidate = stateHistory.removeLast()
            if (candidate.currentPlayer?.type == PlayerType.HUMAN) {
                targetState = candidate
                // If candidate had an already-rolled dice but only 1 move (or 0 moves),
                // it was an automatic or forced move, so popping back before the roll gives control back
                if (candidate.diceValue != null && candidate.validTokenIds.size <= 1 && stateHistory.isNotEmpty()) {
                    val beforeRoll = stateHistory.lastOrNull()
                    if (beforeRoll != null && beforeRoll.currentPlayer?.type == PlayerType.HUMAN && beforeRoll.diceValue == null) {
                        targetState = stateHistory.removeLast()
                    }
                }
                break
            }
        }

        if (targetState != null) {
            _gameState.value = targetState.copy(
                isRolling = false,
                status = GameStatus.IN_PROGRESS
            )
        }
        _canUndo.value = stateHistory.isNotEmpty()
    }

    suspend fun quitGame() {
        val current = _gameState.value
        if (current.mode == GameMode.BLUETOOTH_NEARBY || current.mode == GameMode.ONLINE) {
            val localPlayer = current.players.find { it.type == PlayerType.HUMAN }
            val id = localPlayer?.id ?: "local"
            try {
                transport?.sendMessage(GameMessage.PlayerDisconnected(id))
            } catch (_: Exception) {}
        }
        resetGame()
    }

    fun resetGame() {
        aiJob?.cancel()
        aiJob = null
        stateHistory.clear()
        _canUndo.value = false
        _gameState.value = GameState()
    }

    fun cleanup() {
        aiJob?.cancel()
        transportJob?.cancel()
    }
}
