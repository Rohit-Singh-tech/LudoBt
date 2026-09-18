package com.ludobt.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlayerColor(val displayName: String, val hexCode: Long) {
    RED("Crimson Red", 0xFFEF4444),
    GREEN("Emerald Green", 0xFF10B981),
    YELLOW("Amber Yellow", 0xFFF59E0B),
    BLUE("Royal Blue", 0xFF3B82F6);

    fun getOppositeColor(): PlayerColor = when (this) {
        RED -> YELLOW
        YELLOW -> RED
        BLUE -> GREEN
        GREEN -> BLUE
    }
}

@Serializable
enum class TokenState {
    HOME_BASE,
    ACTIVE,
    HOME_COLUMN,
    FINISHED
}

@Serializable
data class Token(
    val id: Int,
    val color: PlayerColor,
    val state: TokenState = TokenState.HOME_BASE,
    val stepCount: Int = 0 // 0 = at base; 1 = on starting cell; 51 = cell before home stretch; 52..56 = home column; 57 = FINISHED
) {
    val isHomeBase: Boolean get() = state == TokenState.HOME_BASE
    val isFinished: Boolean get() = state == TokenState.FINISHED
    val isInPlay: Boolean get() = state == TokenState.ACTIVE || state == TokenState.HOME_COLUMN
}

@Serializable
enum class PlayerType {
    HUMAN,
    AI,
    BLUETOOTH_REMOTE
}

@Serializable
data class Player(
    val id: String,
    val name: String,
    val color: PlayerColor,
    val type: PlayerType = PlayerType.HUMAN,
    val tokens: List<Token> = (0..3).map { Token(id = it, color = color) },
    val rank: Int = 0, // 1 for 1st place, 2 for 2nd, etc. 0 = still playing
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val isConnected: Boolean = true
) {
    val hasWon: Boolean get() = tokens.all { it.isFinished }
    val finishedTokensCount: Int get() = tokens.count { it.isFinished }
}

@Serializable
enum class GameStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    PAUSED,
    GAME_OVER
}

@Serializable
enum class GameMode(val title: String, val subtitle: String) {
    PASS_AND_PLAY("Pass & Play", "2-4 players on one phone"),
    VS_COMPUTER("Play vs Computer", "Smart AI with 4 difficulty levels"),
    BLUETOOTH_NEARBY("Nearby (Bluetooth)", "No internet needed — play with nearby friends"),
    ONLINE("Play Online", "Connect with players globally")
}

@Serializable
enum class AiDifficulty(val label: String, val description: String) {
    EASY("Easy", "Casual moves, good for beginners"),
    MEDIUM("Medium", "Attacks when available, active releases"),
    HARD("Hard", "Calculates capture risks and safe spots"),
    EXPERT("Expert", "Masterful position control and threat avoidance")
}

@Serializable
data class GameState(
    val gameId: String = "LUDO-${(1000..9999).random()}",
    val mode: GameMode = GameMode.PASS_AND_PLAY,
    val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val players: List<Player> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val diceValue: Int? = null,
    val isRolling: Boolean = false,
    val consecutiveSixes: Int = 0,
    val status: GameStatus = GameStatus.WAITING_FOR_PLAYERS,
    val validTokenIds: List<Int> = emptyList(),
    val turnTimeRemainingSec: Int = 15,
    val lastMoveDescription: String = "Game ready to start",
    val winnerPodium: List<PlayerColor> = emptyList(),
    val sequenceNumber: Long = 0L
) {
    val currentPlayer: Player?
        get() = players.getOrNull(currentPlayerIndex)

    val isGameOver: Boolean
        get() = status == GameStatus.GAME_OVER
}


/**
 * Transport message definitions for Bluetooth / Local / Online sync
 */
@Serializable
sealed class GameMessage {
    @Serializable
    @SerialName("CreateRoomRequest")
    data class CreateRoomRequest(val playerCount: Int, val playerName: String, val color: PlayerColor) : GameMessage()

    @Serializable
    @SerialName("JoinRoomRequest")
    data class JoinRoomRequest(val roomPin: String, val playerName: String, val color: PlayerColor) : GameMessage()

    @Serializable
    @SerialName("QuickMatchRequest")
    data class QuickMatchRequest(val playerCount: Int, val playerName: String, val color: PlayerColor) : GameMessage()

    @Serializable
    @SerialName("RoomCreatedEvent")
    data class RoomCreatedEvent(val roomPin: String, val hostPlayerId: String) : GameMessage()

    @Serializable
    @SerialName("ErrorMessage")
    data class ErrorMessage(val message: String) : GameMessage()

    @Serializable
    @SerialName("LobbyJoin")
    data class LobbyJoin(val playerId: String, val playerName: String, val requestedColor: PlayerColor?) : GameMessage()

    @Serializable
    @SerialName("LobbyUpdate")
    data class LobbyUpdate(val players: List<Player>, val hostId: String, val roomPin: String) : GameMessage()

    @Serializable
    @SerialName("StartGame")
    data class StartGame(
        val initialGameState: GameState,
        val assignedPlayerIndex: Int = 1
    ) : GameMessage()

    @Serializable
    @SerialName("LobbySlotUpdate")
    data class LobbySlotUpdate(
        val playerCount: Int,
        val hostName: String,
        val connectedPeerNames: List<String>,
        val colors: List<PlayerColor> = emptyList()
    ) : GameMessage()

    @Serializable
    @SerialName("LobbyColorChange")
    data class LobbyColorChange(
        val playerId: String,
        val newColor: PlayerColor
    ) : GameMessage()

    @Serializable
    @SerialName("RollDiceRequest")
    data class RollDiceRequest(val playerId: String) : GameMessage()

    @Serializable
    @SerialName("DiceRolledEvent")
    data class DiceRolledEvent(val playerId: String, val value: Int, val validTokenIds: List<Int>, val seq: Long) : GameMessage()

    @Serializable
    @SerialName("MoveTokenRequest")
    data class MoveTokenRequest(val playerId: String, val tokenId: Int) : GameMessage()

    @Serializable
    @SerialName("TokenMovedEvent")
    data class TokenMovedEvent(
        val playerId: String,
        val tokenId: Int,
        val fromStep: Int,
        val toStep: Int,
        val capturedPlayerColor: PlayerColor?,
        val capturedTokenId: Int?,
        val extraTurnGranted: Boolean,
        val nextPlayerIndex: Int,
        val seq: Long
    ) : GameMessage()

    @Serializable
    @SerialName("TurnTimeoutEvent")
    data class TurnTimeoutEvent(val playerId: String, val nextPlayerIndex: Int, val seq: Long) : GameMessage()

    @Serializable
    @SerialName("FullStateSync")
    data class FullStateSync(val gameState: GameState) : GameMessage()

    @Serializable
    @SerialName("QuickReaction")
    data class QuickReaction(val playerId: String, val emojiOrText: String) : GameMessage()

    @Serializable
    @SerialName("Ping")
    data class Ping(val timestamp: Long) : GameMessage()

    @Serializable
    @SerialName("Pong")
    data class Pong(val timestamp: Long) : GameMessage()

    @Serializable
    @SerialName("PlayerDisconnected")
    data class PlayerDisconnected(val playerId: String) : GameMessage()

    @Serializable
    @SerialName("PlayerReconnected")
    data class PlayerReconnected(val playerId: String) : GameMessage()
}
