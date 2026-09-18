package com.ludobt.app.domain.engine

import com.ludobt.app.domain.model.AiDifficulty
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.TokenState

object LudoAiEngine {

    /**
     * Choose the best tokenId to move for an AI player based on difficulty.
     */
    fun selectBestMove(state: GameState, aiPlayer: Player, validTokenIds: List<Int>): Int {
        if (validTokenIds.isEmpty()) return -1
        if (validTokenIds.size == 1) return validTokenIds.first()

        val dice = state.diceValue ?: return validTokenIds.first()

        return when (state.aiDifficulty) {
            AiDifficulty.EASY -> validTokenIds.random()
            AiDifficulty.MEDIUM -> evaluateMedium(state, aiPlayer, validTokenIds, dice)
            AiDifficulty.HARD -> evaluateHard(state, aiPlayer, validTokenIds, dice)
            AiDifficulty.EXPERT -> evaluateExpert(state, aiPlayer, validTokenIds, dice)
        }
    }

    private fun evaluateMedium(state: GameState, aiPlayer: Player, validTokens: List<Int>, dice: Int): Int {
        var bestToken = validTokens.first()
        var highestScore = -1000

        for (tokenId in validTokens) {
            val token = aiPlayer.tokens.firstOrNull { it.id == tokenId } ?: continue
            var score = 0

            val toStep = if (token.state == TokenState.HOME_BASE) 1 else token.stepCount + dice

            // Priority 1: Finish
            if (toStep == LudoGameEngine.MAX_STEPS) {
                score += 150
            }

            // Priority 2: Capture
            if (toStep in 1..51) {
                val landingTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, toStep)
                if (!LudoBoardCoordinates.isSafeCell(landingTrack) && hasOpponentOnTrack(state, aiPlayer, landingTrack)) {
                    score += 200
                }
            }

            // Priority 3: Release token from base
            if (token.state == TokenState.HOME_BASE && dice == 6) {
                score += 100
            }

            // Priority 4: Advance further
            score += toStep

            if (score > highestScore) {
                highestScore = score
                bestToken = tokenId
            }
        }

        return bestToken
    }

    private fun evaluateHard(state: GameState, aiPlayer: Player, validTokens: List<Int>, dice: Int): Int {
        var bestToken = validTokens.first()
        var highestScore = -10000

        for (tokenId in validTokens) {
            val token = aiPlayer.tokens.firstOrNull { it.id == tokenId } ?: continue
            var score = 0

            val fromStep = token.stepCount
            val toStep = if (token.state == TokenState.HOME_BASE) 1 else fromStep + dice

            // 1. Finish token: High value
            if (toStep == LudoGameEngine.MAX_STEPS) {
                score += 400
            }

            // 2. Capture opponent
            if (toStep in 1..51) {
                val landingTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, toStep)
                val isSafe = LudoBoardCoordinates.isSafeCell(landingTrack)
                if (!isSafe && hasOpponentOnTrack(state, aiPlayer, landingTrack)) {
                    score += 350
                }
                // 3. Land on Safe Cell
                if (isSafe) {
                    score += 120
                }
            }

            // 4. Escape current danger if token is currently vulnerable
            if (fromStep in 1..51) {
                val currentTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, fromStep)
                if (!LudoBoardCoordinates.isSafeCell(currentTrack) && isThreatenedByOpponents(state, aiPlayer, currentTrack)) {
                    score += 160 // Escaping threat
                }
            }

            // 5. Entering home column safely (safe from all attacks)
            if (toStep in 52..56 && fromStep <= 51) {
                score += 180
            }

            // 6. Release new token from base
            if (token.state == TokenState.HOME_BASE && dice == 6) {
                val activeTokens = aiPlayer.tokens.count { it.isInPlay }
                score += if (activeTokens == 0) 250 else 100
            }

            // 7. General advancement
            score += toStep * 2

            if (score > highestScore) {
                highestScore = score
                bestToken = tokenId
            }
        }

        return bestToken
    }

    private fun evaluateExpert(state: GameState, aiPlayer: Player, validTokens: List<Int>, dice: Int): Int {
        var bestToken = validTokens.first()
        var highestScore = -50000

        for (tokenId in validTokens) {
            val token = aiPlayer.tokens.firstOrNull { it.id == tokenId } ?: continue
            var score = 0

            val fromStep = token.stepCount
            val toStep = if (token.state == TokenState.HOME_BASE) 1 else fromStep + dice

            // 1. Instant Victory / Finish
            if (toStep == LudoGameEngine.MAX_STEPS) {
                score += 1000
            }

            // 2. High-value Capture (Bonus if opponent is deep into their run)
            if (toStep in 1..51) {
                val landingTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, toStep)
                if (!LudoBoardCoordinates.isSafeCell(landingTrack)) {
                    val opponentThreat = getOpponentThreatValue(state, aiPlayer, landingTrack)
                    if (opponentThreat > 0) {
                        score += 500 + (opponentThreat * 8)
                    }
                }
            }

            // 3. Reaching Safe Haven
            if (toStep in 1..51) {
                val landingTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, toStep)
                if (LudoBoardCoordinates.isSafeCell(landingTrack)) {
                    score += 200
                } else if (isThreatenedByOpponents(state, aiPlayer, landingTrack)) {
                    // Moving into danger penalty
                    score -= 150
                }
            }

            // 4. Safe Home Column Entry
            if (toStep in 52..56 && fromStep <= 51) {
                score += 350
            }

            // 5. Escaping high danger on current cell
            if (fromStep in 1..51) {
                val currentTrack = LudoBoardCoordinates.getTrackIndex(aiPlayer.color, fromStep)
                if (!LudoBoardCoordinates.isSafeCell(currentTrack) && isThreatenedByOpponents(state, aiPlayer, currentTrack)) {
                    score += 260
                }
            }

            // 6. Base exit balance
            if (token.state == TokenState.HOME_BASE && dice == 6) {
                val tokensInPlay = aiPlayer.tokens.count { it.isInPlay }
                score += when (tokensInPlay) {
                    0 -> 400
                    1 -> 180
                    else -> 90
                }
            }

            // 7. Distance progress weight
            score += toStep * 3

            if (score > highestScore) {
                highestScore = score
                bestToken = tokenId
            }
        }

        return bestToken
    }

    private fun hasOpponentOnTrack(state: GameState, aiPlayer: Player, trackIndex: Int): Boolean {
        for (opp in state.players) {
            if (opp.color == aiPlayer.color) continue
            for (t in opp.tokens) {
                if (t.stepCount in 1..51) {
                    val oppTrack = LudoBoardCoordinates.getTrackIndex(opp.color, t.stepCount)
                    if (oppTrack == trackIndex) return true
                }
            }
        }
        return false
    }

    private fun getOpponentThreatValue(state: GameState, aiPlayer: Player, trackIndex: Int): Int {
        for (opp in state.players) {
            if (opp.color == aiPlayer.color) continue
            for (t in opp.tokens) {
                if (t.stepCount in 1..51) {
                    val oppTrack = LudoBoardCoordinates.getTrackIndex(opp.color, t.stepCount)
                    if (oppTrack == trackIndex) {
                        return t.stepCount // Higher step count means opponent was closer to winning
                    }
                }
            }
        }
        return 0
    }

    private fun isThreatenedByOpponents(state: GameState, aiPlayer: Player, targetTrackIndex: Int): Boolean {
        for (opp in state.players) {
            if (opp.color == aiPlayer.color) continue
            for (t in opp.tokens) {
                if (t.stepCount in 1..51) {
                    val oppTrack = LudoBoardCoordinates.getTrackIndex(opp.color, t.stepCount)
                    // Threat exists if opponent is 1..6 steps behind on the cyclic track
                    val dist = (targetTrackIndex - oppTrack + 52) % 52
                    if (dist in 1..6) return true
                }
            }
        }
        return false
    }
}
