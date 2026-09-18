package com.ludobt.app.domain.engine

import com.ludobt.app.domain.model.PlayerColor

/**
 * Grid coordinates (col, row) on a standard 15x15 Ludo board.
 * Both col and row are in 0..14.
 */
data class GridPoint(val col: Float, val row: Float)

object LudoBoardCoordinates {

    // Starting track index (0..51) for each color
    val START_TRACK_INDEX = mapOf(
        PlayerColor.RED to 0,
        PlayerColor.GREEN to 13,
        PlayerColor.YELLOW to 26,
        PlayerColor.BLUE to 39
    )

    // Safe track cells (0..51) where tokens cannot be captured
    // 4 start positions + 4 star safe cells
    val SAFE_TRACK_CELLS = setOf(
        0, 8,    // Red start & safe star
        13, 21,  // Green start & safe star
        26, 34,  // Yellow start & safe star
        39, 47   // Blue start & safe star
    )

    /**
     * Map of 52 common track indices (0..51) to 15x15 grid points (col, row).
     * Follows standard clockwise path starting from Red's start at (1, 6).
     */
    val TRACK_GRID_POINTS: List<GridPoint> = listOf(
        // Red track (0..4 heading right to bottom of top arm)
        GridPoint(1f, 6f),  // 0: Red Start (Safe)
        GridPoint(2f, 6f),  // 1
        GridPoint(3f, 6f),  // 2
        GridPoint(4f, 6f),  // 3
        GridPoint(5f, 6f),  // 4
        // Entering top arm heading up
        GridPoint(6f, 5f),  // 5
        GridPoint(6f, 4f),  // 6
        GridPoint(6f, 3f),  // 7
        GridPoint(6f, 2f),  // 8: Star Safe
        GridPoint(6f, 1f),  // 9
        GridPoint(6f, 0f),  // 10
        // Across top arm
        GridPoint(7f, 0f),  // 11
        GridPoint(8f, 0f),  // 12
        // Green start & heading down
        GridPoint(8f, 1f),  // 13: Green Start (Safe)
        GridPoint(8f, 2f),  // 14
        GridPoint(8f, 3f),  // 15
        GridPoint(8f, 4f),  // 16
        GridPoint(8f, 5f),  // 17
        // Entering right arm heading right
        GridPoint(9f, 6f),  // 18
        GridPoint(10f, 6f), // 19
        GridPoint(11f, 6f), // 20
        GridPoint(12f, 6f), // 21: Star Safe
        GridPoint(13f, 6f), // 22
        GridPoint(14f, 6f), // 23
        // Across right arm
        GridPoint(14f, 7f), // 24
        GridPoint(14f, 8f), // 25
        // Yellow start & heading left
        GridPoint(13f, 8f), // 26: Yellow Start (Safe)
        GridPoint(12f, 8f), // 27
        GridPoint(11f, 8f), // 28
        GridPoint(10f, 8f), // 29
        GridPoint(9f, 8f),  // 30
        // Entering bottom arm heading down
        GridPoint(8f, 9f),  // 31
        GridPoint(8f, 10f), // 32
        GridPoint(8f, 11f), // 33
        GridPoint(8f, 12f), // 34: Star Safe
        GridPoint(8f, 13f), // 35
        GridPoint(8f, 14f), // 36
        // Across bottom arm
        GridPoint(7f, 14f), // 37
        GridPoint(6f, 14f), // 38
        // Blue start & heading up
        GridPoint(6f, 13f), // 39: Blue Start (Safe)
        GridPoint(6f, 12f), // 40
        GridPoint(6f, 11f), // 41
        GridPoint(6f, 10f), // 42
        GridPoint(6f, 9f),  // 43
        // Entering left arm heading left
        GridPoint(5f, 8f),  // 44
        GridPoint(4f, 8f),  // 45
        GridPoint(3f, 8f),  // 46
        GridPoint(2f, 8f),  // 47: Star Safe
        GridPoint(1f, 8f),  // 48
        GridPoint(0f, 8f),  // 49
        // Across left arm
        GridPoint(0f, 7f),  // 50
        GridPoint(0f, 6f)   // 51
    )

    /**
     * Colored home columns (5 cells each) before reaching the center triangle.
     */
    val HOME_COLUMNS: Map<PlayerColor, List<GridPoint>> = mapOf(
        PlayerColor.RED to listOf(
            GridPoint(1f, 7f), GridPoint(2f, 7f), GridPoint(3f, 7f), GridPoint(4f, 7f), GridPoint(5f, 7f)
        ),
        PlayerColor.GREEN to listOf(
            GridPoint(7f, 1f), GridPoint(7f, 2f), GridPoint(7f, 3f), GridPoint(7f, 4f), GridPoint(7f, 5f)
        ),
        PlayerColor.YELLOW to listOf(
            GridPoint(13f, 7f), GridPoint(12f, 7f), GridPoint(11f, 7f), GridPoint(10f, 7f), GridPoint(9f, 7f)
        ),
        PlayerColor.BLUE to listOf(
            GridPoint(7f, 13f), GridPoint(7f, 12f), GridPoint(7f, 11f), GridPoint(7f, 10f), GridPoint(7f, 9f)
        )
    )

    /**
     * Center finish positions (center of the board 7, 7)
     */
    val CENTER_FINISH_POINTS: Map<PlayerColor, GridPoint> = mapOf(
        PlayerColor.RED to GridPoint(6.6f, 7f),
        PlayerColor.GREEN to GridPoint(7f, 6.6f),
        PlayerColor.YELLOW to GridPoint(7.4f, 7f),
        PlayerColor.BLUE to GridPoint(7f, 7.4f)
    )

    /**
     * Home yard base token circles (4 tokens per color inside their base camp)
     */
    val HOME_BASE_POSITIONS: Map<PlayerColor, List<GridPoint>> = mapOf(
        PlayerColor.RED to listOf(
            GridPoint(1.8f, 1.8f), GridPoint(3.8f, 1.8f),
            GridPoint(1.8f, 3.8f), GridPoint(3.8f, 3.8f)
        ),
        PlayerColor.GREEN to listOf(
            GridPoint(10.8f, 1.8f), GridPoint(12.8f, 1.8f),
            GridPoint(10.8f, 3.8f), GridPoint(12.8f, 3.8f)
        ),
        PlayerColor.YELLOW to listOf(
            GridPoint(10.8f, 10.8f), GridPoint(12.8f, 10.8f),
            GridPoint(10.8f, 12.8f), GridPoint(12.8f, 12.8f)
        ),
        PlayerColor.BLUE to listOf(
            GridPoint(1.8f, 10.8f), GridPoint(3.8f, 10.8f),
            GridPoint(1.8f, 12.8f), GridPoint(3.8f, 12.8f)
        )
    )

    /**
     * Convert player's stepCount (1..51) to universal track index (0..51).
     * Returns -1 if stepCount is 0 (base) or >= 52 (in home column or finished).
     */
    fun getTrackIndex(color: PlayerColor, stepCount: Int): Int {
        if (stepCount !in 1..51) return -1
        val startIndex = START_TRACK_INDEX[color] ?: 0
        return (startIndex + (stepCount - 1)) % 52
    }

    /**
     * Check if a universal track index is a safe zone.
     */
    fun isSafeCell(trackIndex: Int): Boolean {
        return trackIndex in SAFE_TRACK_CELLS
    }

    /**
     * Calculate 2D grid point for any token based on its state and stepCount.
     */
    fun getGridPoint(color: PlayerColor, tokenId: Int, stepCount: Int): GridPoint {
        return when {
            stepCount == 0 -> {
                HOME_BASE_POSITIONS[color]?.getOrNull(tokenId % 4) ?: GridPoint(2f, 2f)
            }
            stepCount in 1..51 -> {
                val trackIdx = getTrackIndex(color, stepCount)
                TRACK_GRID_POINTS.getOrElse(trackIdx) { GridPoint(7f, 7f) }
            }
            stepCount in 52..56 -> {
                val colIdx = stepCount - 52
                HOME_COLUMNS[color]?.getOrNull(colIdx) ?: GridPoint(7f, 7f)
            }
            else -> {
                CENTER_FINISH_POINTS[color] ?: GridPoint(7f, 7f)
            }
        }
    }
}
