package com.ludobt.app.domain.engine

import com.ludobt.app.domain.model.GameMode
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.GameStatus
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.model.Token
import com.ludobt.app.domain.model.TokenState
import kotlin.random.Random

data class MoveResult(
    val nextState: GameState,
    val movedTokenId: Int,
    val fromStep: Int,
    val toStep: Int,
    val capturedToken: Token?,
    val extraTurnGranted: Boolean
)

object LudoGameEngine {

    const val MAX_STEPS = 57 // Exact finish cell
    const val START_STEP = 1
    const val MAX_CONSECUTIVE_SIXES = 3

    /**
     * Create an initial GameState with standard player assignments.
     * When 2 players play, they are assigned opposite courts (RED and YELLOW).
     */
    fun createInitialGame(
        mode: GameMode,
        playerConfigs: List<Pair<String, PlayerType>>,
        colors: List<PlayerColor>? = null
    ): GameState {
        val assignedColors = colors ?: when (playerConfigs.size) {
            2 -> listOf(PlayerColor.RED, PlayerColor.YELLOW) // Diagonally opposite courts
            3 -> listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW)
            else -> listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
        }

        val players = playerConfigs.mapIndexed { index, (name, type) ->
            val color = assignedColors[index % assignedColors.size]
            Player(
                id = "P${index + 1}",
                name = name.ifBlank { "Player ${index + 1}" },
                color = color,
                type = type,
                tokens = (0..3).map { Token(id = it, color = color) },
                isHost = index == 0,
                isReady = true,
                isConnected = true
            )
        }

        return GameState(
            mode = mode,
            players = players,
            currentPlayerIndex = 0,
            status = GameStatus.IN_PROGRESS,
            lastMoveDescription = "${players.firstOrNull()?.name}'s turn. Roll the dice!"
        )
    }

    /**
     * Roll the dice for the current player.
     * Generates a 1..6 roll, checks for 3-consecutive 6s penalty,
     * and computes all valid movable tokens for the player.
     */
    fun rollDice(currentState: GameState, forcedValue: Int? = null): GameState {
        if (currentState.status != GameStatus.IN_PROGRESS) return currentState
        val player = currentState.currentPlayer ?: return currentState

        val roll = forcedValue ?: Random.nextInt(1, 7)
        val consecutiveSixes = if (roll == 6) currentState.consecutiveSixes + 1 else 0

        // Penalty rule: 3 consecutive sixes voids turn
        if (consecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
            return currentState.copy(
                diceValue = roll,
                isRolling = false,
                consecutiveSixes = 0,
                validTokenIds = emptyList(),
                lastMoveDescription = "${player.name} rolled three 6s in a row! Turn forfeited.",
                sequenceNumber = currentState.sequenceNumber + 1
            )
        }

        val validTokens = getValidTokens(player, roll)

        return currentState.copy(
            diceValue = roll,
            isRolling = false,
            consecutiveSixes = consecutiveSixes,
            validTokenIds = validTokens,
            lastMoveDescription = if (validTokens.isEmpty()) {
                "${player.name} rolled $roll. No valid moves!"
            } else {
                "${player.name} rolled $roll. Select a token to move!"
            },
            sequenceNumber = currentState.sequenceNumber + 1
        )
    }

    /**
     * Pass turn to the next active player, resetting diceValue to null so they can roll.
     */
    fun passTurnToNextPlayer(currentState: GameState): GameState {
        val nextPlayerIndex = getNextActivePlayerIndex(currentState, currentState.currentPlayerIndex)
        val nextPlayer = currentState.players.getOrNull(nextPlayerIndex)
        return currentState.copy(
            diceValue = null,
            isRolling = false,
            validTokenIds = emptyList(),
            consecutiveSixes = 0,
            currentPlayerIndex = nextPlayerIndex,
            lastMoveDescription = "${nextPlayer?.name ?: "Next player"}'s turn. Roll the dice!",
            sequenceNumber = currentState.sequenceNumber + 1
        )
    }

    /**
     * Determine which tokens of the player can legally move given the dice roll.
     */
    fun getValidTokens(player: Player, diceValue: Int): List<Int> {
        val valid = mutableListOf<Int>()
        for (token in player.tokens) {
            when (token.state) {
                TokenState.HOME_BASE -> {
                    // Requires a 6 to exit home yard to start cell
                    if (diceValue == 6) {
                        valid.add(token.id)
                    }
                }
                TokenState.ACTIVE, TokenState.HOME_COLUMN -> {
                    val nextStep = token.stepCount + diceValue
                    if (nextStep <= MAX_STEPS) {
                        valid.add(token.id)
                    }
                }
                TokenState.FINISHED -> {
                    // Cannot move finished tokens
                }
            }
        }
        return valid
    }

    /**
     * Execute a token move for the current player.
     * Evaluates track movement, captures, extra turns, and win conditions.
     */
    fun moveToken(currentState: GameState, tokenId: Int): MoveResult {
        val player = currentState.currentPlayer ?: return MoveResult(currentState, tokenId, 0, 0, null, false)
        val dice = currentState.diceValue ?: return MoveResult(currentState, tokenId, 0, 0, null, false)
        val token = player.tokens.find { it.id == tokenId } ?: return MoveResult(currentState, tokenId, 0, 0, null, false)

        val fromStep = token.stepCount
        val toStep = when (token.state) {
            TokenState.HOME_BASE -> START_STEP
            else -> token.stepCount + dice
        }

        val newState = when {
            toStep == MAX_STEPS -> TokenState.FINISHED
            toStep in 52..56 -> TokenState.HOME_COLUMN
            else -> TokenState.ACTIVE
        }

        val updatedToken = token.copy(stepCount = toStep, state = newState)
        val updatedTokens = player.tokens.map { if (it.id == tokenId) updatedToken else it }
        val updatedPlayer = player.copy(tokens = updatedTokens)

        // Capture check: only if landing on common track (1..51)
        var capturedToken: Token? = null
        var capturedPlayerColor: PlayerColor? = null
        val updatedPlayers = currentState.players.toMutableList()

        if (toStep in 1..51) {
            val landingTrackIndex = LudoBoardCoordinates.getTrackIndex(player.color, toStep)
            val isSafe = LudoBoardCoordinates.isSafeCell(landingTrackIndex)

            if (!isSafe) {
                // Check all opponents for a piece at this landing track index
                for (pIdx in updatedPlayers.indices) {
                    val opp = updatedPlayers[pIdx]
                    if (opp.color == player.color) continue

                    val oppTokens = opp.tokens.toMutableList()
                    var opponentHit = false

                    for (tIdx in oppTokens.indices) {
                        val oppToken = oppTokens[tIdx]
                        if (oppToken.stepCount in 1..51) {
                            val oppTrack = LudoBoardCoordinates.getTrackIndex(opp.color, oppToken.stepCount)
                            if (oppTrack == landingTrackIndex) {
                                // Captured! Reset to home base
                                capturedToken = oppToken
                                capturedPlayerColor = opp.color
                                oppTokens[tIdx] = oppToken.copy(
                                    stepCount = 0,
                                    state = TokenState.HOME_BASE
                                )
                                opponentHit = true
                                break // Standard rules: capture one token per landing
                            }
                        }
                    }

                    if (opponentHit) {
                        updatedPlayers[pIdx] = opp.copy(tokens = oppTokens)
                        break
                    }
                }
            }
        }

        // Put current player into updated players list
        val currPlayerIdx = currentState.currentPlayerIndex
        updatedPlayers[currPlayerIdx] = updatedPlayer

        // Check if current player just finished all tokens
        val hasPlayerWonNow = updatedPlayer.tokens.all { it.isFinished }
        val updatedPodium = currentState.winnerPodium.toMutableList()
        if (hasPlayerWonNow && !updatedPodium.contains(player.color)) {
            updatedPodium.add(player.color)
            updatedPlayers[currPlayerIdx] = updatedPlayer.copy(rank = updatedPodium.size)
        }

        // Determine if extra turn is granted:
        // 1. Rolled a 6
        // 2. Captured an opponent token
        // 3. Reached home finish (MAX_STEPS)
        val gotExtraTurn = (dice == 6) || (capturedToken != null) || (toStep == MAX_STEPS)

        // Check if game over (e.g. only 1 player remains without winning, or 1st place in 2-player match)
        val activePlayersRemaining = updatedPlayers.filter { !it.hasWon }
        val isGameOver = (updatedPlayers.size == 2 && updatedPodium.size >= 1) || activePlayersRemaining.size <= 1

        val nextPlayerIndex = if (isGameOver || gotExtraTurn) {
            currPlayerIdx
        } else {
            getNextActivePlayerIndex(currentState.copy(players = updatedPlayers), currPlayerIdx)
        }

        val description = buildString {
            append("${player.name} moved token to ")
            if (toStep == MAX_STEPS) append("Finish! ⭐ ")
            else append("step $toStep. ")

            if (capturedToken != null && capturedPlayerColor != null) {
                append("Captured ${capturedPlayerColor.displayName} token! Extra turn! ⚔️ ")
            } else if (dice == 6) {
                append("Rolled 6, extra turn! 🎲 ")
            } else if (toStep == MAX_STEPS) {
                append("Reached Home, extra turn! 🏆 ")
            }
        }

        val nextGameState = currentState.copy(
            players = updatedPlayers,
            currentPlayerIndex = nextPlayerIndex,
            diceValue = null,
            validTokenIds = emptyList(),
            winnerPodium = updatedPodium,
            status = if (isGameOver) GameStatus.GAME_OVER else GameStatus.IN_PROGRESS,
            lastMoveDescription = description,
            sequenceNumber = currentState.sequenceNumber + 1
        )

        return MoveResult(
            nextState = nextGameState,
            movedTokenId = tokenId,
            fromStep = fromStep,
            toStep = toStep,
            capturedToken = capturedToken,
            extraTurnGranted = gotExtraTurn
        )
    }

    /**
     * Find next player in clockwise rotation who still has uncompleted tokens.
     */
    fun getNextActivePlayerIndex(state: GameState, currentIndex: Int): Int {
        val totalPlayers = state.players.size
        if (totalPlayers == 0) return 0
        for (i in 1..totalPlayers) {
            val candidateIndex = (currentIndex + i) % totalPlayers
            val candidatePlayer = state.players[candidateIndex]
            if (!candidatePlayer.hasWon && candidatePlayer.isConnected) {
                return candidateIndex
            }
        }
        return (currentIndex + 1) % totalPlayers
    }
}
