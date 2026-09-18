package com.ludobt.app.domain.engine

import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.model.GameMode
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.model.Token
import com.ludobt.app.domain.model.TokenState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoGameEngineTest {

    @Test
    fun testInitialGameState() {
        val configs = listOf(
            "Player 1" to PlayerType.HUMAN,
            "Player 2" to PlayerType.HUMAN
        )
        val state = LudoGameEngine.createInitialGame(GameMode.PASS_AND_PLAY, configs)

        assertEquals(2, state.players.size)
        assertEquals("P1", state.currentPlayer?.id)
        assertEquals(4, state.currentPlayer?.tokens?.size)
        assertTrue(state.currentPlayer?.tokens?.all { it.isHomeBase } == true)
        assertEquals(0, state.consecutiveSixes)
    }

    @Test
    fun testTwoPlayerOppositeCourts() {
        val configs = listOf(
            "Player 1" to PlayerType.HUMAN,
            "Player 2" to PlayerType.HUMAN
        )
        val state = LudoGameEngine.createInitialGame(GameMode.PASS_AND_PLAY, configs)

        assertEquals(2, state.players.size)
        assertEquals(PlayerColor.RED, state.players[0].color)
        assertEquals(PlayerColor.YELLOW, state.players[1].color) // Diagonally opposite court!
    }

    @Test
    fun testDiceRollAndExitBaseRule() {
        val configs = listOf(
            "Alice" to PlayerType.HUMAN,
            "Bob" to PlayerType.HUMAN
        )
        val state = LudoGameEngine.createInitialGame(GameMode.PASS_AND_PLAY, configs)

        // Non-six roll: Cannot exit home base
        val stateRoll5 = LudoGameEngine.rollDice(state, forcedValue = 5)
        assertEquals(5, stateRoll5.diceValue)
        assertTrue("Tokens should not be movable on 5 from home base", stateRoll5.validTokenIds.isEmpty())

        // Six roll: All 4 base tokens can exit
        val stateRoll6 = LudoGameEngine.rollDice(state, forcedValue = 6)
        assertEquals(6, stateRoll6.diceValue)
        assertEquals(4, stateRoll6.validTokenIds.size)

        // Move token 0 out of base
        val moveResult = LudoGameEngine.moveToken(stateRoll6, tokenId = 0)
        assertEquals(1, moveResult.toStep)
        assertTrue(moveResult.extraTurnGranted) // Rolling 6 gives extra turn
        assertEquals(0, moveResult.nextState.currentPlayerIndex) // Still Alice's turn
    }

    @Test
    fun testThreeConsecutiveSixesPenalty() {
        val configs = listOf(
            "Alice" to PlayerType.HUMAN,
            "Bob" to PlayerType.HUMAN
        )
        var state = LudoGameEngine.createInitialGame(GameMode.PASS_AND_PLAY, configs)

        state = LudoGameEngine.rollDice(state, forcedValue = 6)
        assertEquals(1, state.consecutiveSixes)

        // Fake 2 consecutive 6s in state and roll 3rd six
        state = state.copy(consecutiveSixes = 2)
        val penalizedState = LudoGameEngine.rollDice(state, forcedValue = 6)

        // Third consecutive 6 voids turn (validTokenIds empty, consecutiveSixes reset)
        assertEquals(0, penalizedState.consecutiveSixes)
        assertTrue(penalizedState.validTokenIds.isEmpty())

        // Passing turn advances to Bob with diceValue reset to null
        val afterPass = LudoGameEngine.passTurnToNextPlayer(penalizedState)
        assertEquals(1, afterPass.currentPlayerIndex)
        assertEquals("Bob", afterPass.currentPlayer?.name)
        assertEquals(null, afterPass.diceValue)
    }

    @Test
    fun testCaptureMechanic() {
        val configs = listOf(
            "Alice" to PlayerType.HUMAN,
            "Bob" to PlayerType.HUMAN
        )
        var state = LudoGameEngine.createInitialGame(
            GameMode.PASS_AND_PLAY,
            configs,
            colors = listOf(PlayerColor.RED, PlayerColor.GREEN)
        )

        // Put Bob's token 0 on track at an unsafe position:
        // Bob starts at index 13. Step count 2 on Bob's path -> track index 14 (not safe).
        val bobTokens = state.players[1].tokens.toMutableList()
        bobTokens[0] = Token(id = 0, color = PlayerColor.GREEN, state = TokenState.ACTIVE, stepCount = 2)
        val updatedBob = state.players[1].copy(tokens = bobTokens)

        // Put Alice's token 0 on track such that rolling 3 lands exactly on track index 14:
        // Alice starts at index 0. Step count 12 -> track index 11.
        // Alice step count 15 -> track index 14.
        val aliceTokens = state.players[0].tokens.toMutableList()
        aliceTokens[0] = Token(id = 0, color = PlayerColor.RED, state = TokenState.ACTIVE, stepCount = 12)
        val updatedAlice = state.players[0].copy(tokens = aliceTokens)

        state = state.copy(
            players = listOf(updatedAlice, updatedBob),
            currentPlayerIndex = 0,
            diceValue = 3,
            validTokenIds = listOf(0)
        )

        val result = LudoGameEngine.moveToken(state, tokenId = 0)

        // Alice token should now be at step 15
        assertEquals(15, result.toStep)
        // Opponent token should be captured!
        assertNotNull(result.capturedToken)
        assertEquals(PlayerColor.GREEN, result.capturedToken?.color)
        assertTrue(result.extraTurnGranted)

        // Bob's token 0 should be sent back to base
        val bobAfter = result.nextState.players[1]
        assertEquals(TokenState.HOME_BASE, bobAfter.tokens[0].state)
        assertEquals(0, bobAfter.tokens[0].stepCount)
    }

    @Test
    fun testSafeCellProtection() {
        val configs = listOf(
            "Alice" to PlayerType.HUMAN,
            "Bob" to PlayerType.HUMAN
        )
        var state = LudoGameEngine.createInitialGame(
            GameMode.PASS_AND_PLAY,
            configs,
            colors = listOf(PlayerColor.RED, PlayerColor.GREEN)
        )

        // Track index 8 is a SAFE STAR cell
        // Alice step count 9 -> track index 8
        // Put Bob's token on track index 8
        val bobTokens = state.players[1].tokens.toMutableList()
        // Green starts at 13. Track 8 is (8 - 13 + 52) = 47 steps.
        bobTokens[0] = Token(id = 0, color = PlayerColor.GREEN, state = TokenState.ACTIVE, stepCount = 48)
        val updatedBob = state.players[1].copy(tokens = bobTokens)

        val aliceTokens = state.players[0].tokens.toMutableList()
        aliceTokens[0] = Token(id = 0, color = PlayerColor.RED, state = TokenState.ACTIVE, stepCount = 7)
        val updatedAlice = state.players[0].copy(tokens = aliceTokens)

        state = state.copy(
            players = listOf(updatedAlice, updatedBob),
            currentPlayerIndex = 0,
            diceValue = 2,
            validTokenIds = listOf(0)
        )

        val result = LudoGameEngine.moveToken(state, tokenId = 0)
        // Alice reaches step 9 (track index 8)
        assertEquals(9, result.toStep)
        // Since it's a safe star cell, Bob is NOT captured!
        assertEquals(null, result.capturedToken)
    }

    @Test
    fun testJsonSerializationOfMessages() {
        val json = Json { ignoreUnknownKeys = true }
        val message: GameMessage = GameMessage.TokenMovedEvent(
            playerId = "P1",
            tokenId = 2,
            fromStep = 10,
            toStep = 15,
            capturedPlayerColor = PlayerColor.GREEN,
            capturedTokenId = 0,
            extraTurnGranted = true,
            nextPlayerIndex = 0,
            seq = 42L
        )

        val encoded = json.encodeToString(message)
        assertTrue(encoded.contains("P1"))
        assertTrue(encoded.contains("42"))

        val decoded = json.decodeFromString<GameMessage>(encoded)
        assertTrue(decoded is GameMessage.TokenMovedEvent)
        val event = decoded as GameMessage.TokenMovedEvent
        assertEquals("P1", event.playerId)
        assertEquals(2, event.tokenId)
        assertEquals(15, event.toStep)
        assertEquals(42L, event.seq)
    }

    @Test
    fun testStartGameMessageSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val configs = listOf(
            "Host (You)" to PlayerType.HUMAN,
            "Dr.Doom" to PlayerType.BLUETOOTH_REMOTE
        )
        val initial = LudoGameEngine.createInitialGame(GameMode.BLUETOOTH_NEARBY, configs)
        val message = GameMessage.StartGame(initialGameState = initial, assignedPlayerIndex = 1)

        val encoded = json.encodeToString<GameMessage>(message)
        val decoded = json.decodeFromString<GameMessage>(encoded) as GameMessage.StartGame

        assertEquals(1, decoded.assignedPlayerIndex)
        assertEquals(2, decoded.initialGameState.players.size)
        assertEquals(PlayerColor.RED, decoded.initialGameState.players[0].color)
        assertEquals(PlayerColor.YELLOW, decoded.initialGameState.players[1].color)
    }
}
