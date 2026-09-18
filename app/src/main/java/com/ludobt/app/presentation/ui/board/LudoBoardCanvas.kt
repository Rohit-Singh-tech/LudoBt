package com.ludobt.app.presentation.ui.board

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.ludobt.app.domain.engine.GridPoint
import com.ludobt.app.domain.engine.LudoBoardCoordinates
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.model.Token
import com.ludobt.app.presentation.theme.LudoBlue
import com.ludobt.app.presentation.theme.LudoGreen
import com.ludobt.app.presentation.theme.LudoRed
import com.ludobt.app.presentation.theme.LudoYellow
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun LudoBoardCanvas(
    gameState: GameState,
    onTokenSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        GotiBitmapProvider.preload(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "board_anims")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSize = minOf(maxWidth, maxHeight)
        Canvas(
            modifier = Modifier
                .size(boardSize)
                .pointerInput(gameState.validTokenIds, gameState.currentPlayerIndex) {
                    detectTapGestures { tapOffset ->
                        val cellSize = size.width / 15f
                        val currentPl = gameState.currentPlayer ?: return@detectTapGestures
                        if (currentPl.type != PlayerType.HUMAN) return@detectTapGestures
                        val validIds = gameState.validTokenIds
                        if (validIds.isEmpty()) return@detectTapGestures

                        var selectedId: Int? = null
                        var minDistance = Float.MAX_VALUE

                        for (token in currentPl.tokens) {
                            if (!validIds.contains(token.id)) continue
                            val pt = LudoBoardCoordinates.getGridPoint(currentPl.color, token.id, token.stepCount)
                            val tokenCenter = Offset((pt.col + 0.5f) * cellSize, (pt.row + 0.5f) * cellSize)
                            val distance = hypot(tapOffset.x - tokenCenter.x, tapOffset.y - tokenCenter.y)
                            if (distance <= cellSize * 1.5f && distance < minDistance) {
                                minDistance = distance
                                selectedId = token.id
                            }
                        }

                        // If user tapped home yard when rolling 6, pick a base token
                        if (selectedId == null) {
                            val yardColRange = when (currentPl.color) {
                                PlayerColor.RED, PlayerColor.BLUE -> 0f..6f
                                PlayerColor.GREEN, PlayerColor.YELLOW -> 9f..15f
                            }
                            val yardRowRange = when (currentPl.color) {
                                PlayerColor.RED, PlayerColor.GREEN -> 0f..6f
                                PlayerColor.BLUE, PlayerColor.YELLOW -> 9f..15f
                            }
                            val tapCol = tapOffset.x / cellSize
                            val tapRow = tapOffset.y / cellSize

                            if (tapCol in yardColRange && tapRow in yardRowRange) {
                                val baseToken = currentPl.tokens.firstOrNull { validIds.contains(it.id) && it.stepCount == 0 }
                                selectedId = baseToken?.id
                            }
                        }

                        selectedId?.let { onTokenSelected(it) }
                    }
                }
        ) {
            val boardDimension = size.minDimension
            val cellSize = boardDimension / 15f

            // 1. Board Outer Border / Base
            drawRect(
                color = Color(0xFF0F1E4A),
                size = Size(boardDimension, boardDimension)
            )

            // 2. Four Colored Corner Home Yards
            val redPlayer = gameState.players.firstOrNull { it.color == PlayerColor.RED }
            val greenPlayer = gameState.players.firstOrNull { it.color == PlayerColor.GREEN }
            val yellowPlayer = gameState.players.firstOrNull { it.color == PlayerColor.YELLOW }
            val bluePlayer = gameState.players.firstOrNull { it.color == PlayerColor.BLUE }

            drawHomeYard(PlayerColor.RED, 0f, 0f, cellSize, player = redPlayer, defaultName = "Computer 1", isTopYard = true)
            drawHomeYard(PlayerColor.GREEN, 9f * cellSize, 0f, cellSize, player = greenPlayer, defaultName = "Computer 2", isTopYard = true)
            drawHomeYard(PlayerColor.YELLOW, 9f * cellSize, 9f * cellSize, cellSize, player = yellowPlayer, defaultName = "Computer 3", isTopYard = false)
            drawHomeYard(PlayerColor.BLUE, 0f, 9f * cellSize, cellSize, player = bluePlayer, defaultName = "You", isTopYard = false)

            // 3. Track Grid (52 White Tiles, Solid Start Cells, Solid Home Columns)
            drawTrackGrid(cellSize)

            // 4. Directional Entrance Arrows
            drawEntranceArrows(cellSize)

            // 5. Outlined Safe Stars on track safe cells
            drawSafeStars(cellSize)

            // 6. Center Victory Triangles (4 clean meeting triangles)
            drawCenterTriangle(cellSize)

            // 7. Render All 3D Map-Pin Gotis
            drawAll3DTokens(gameState, cellSize, pulseScale, bounceOffset)
        }
    }
}

/**
 * Draw corner home yard:
 * Solid colored 6x6 base, white rounded inner card, 4 circular spots (solid colored when token has left base),
 * and player name on top/bottom yard edge.
 */
private fun DrawScope.drawHomeYard(
    color: PlayerColor,
    left: Float,
    top: Float,
    cellSize: Float,
    player: Player?,
    defaultName: String,
    isTopYard: Boolean
) {
    val yardSize = 6f * cellSize
    val mainColor = getColorForPlayer(color)

    // Outer solid colored base
    drawRect(
        color = mainColor,
        topLeft = Offset(left, top),
        size = Size(yardSize, yardSize)
    )

    // Inner rounded white card
    val innerMargin = cellSize * 0.82f
    val innerSize = yardSize - (innerMargin * 2)
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + innerMargin, top + innerMargin),
        size = Size(innerSize, innerSize),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // 4 base token spots inside the white card
    val basePositions = LudoBoardCoordinates.HOME_BASE_POSITIONS[color] ?: emptyList()
    for (i in 0..3) {
        val pt = basePositions.getOrNull(i) ?: continue
        val center = Offset((pt.col + 0.5f) * cellSize, (pt.row + 0.5f) * cellSize)

        // Check if token `i` has left the home base
        val token = player?.tokens?.firstOrNull { it.id == i }
        val hasLeftBase = token != null && token.stepCount > 0

        if (hasLeftBase) {
            // When token has left the yard, the spot is a SOLID COLORED CIRCLE as in Image 2!
            drawCircle(
                color = mainColor,
                radius = cellSize * 0.35f,
                center = center
            )
            drawCircle(
                color = mainColor,
                radius = cellSize * 0.38f,
                center = center,
                style = Stroke(width = 2.5f)
            )
        } else {
            // Token is still in base (or empty court)
            drawCircle(
                color = mainColor.copy(alpha = 0.22f),
                radius = cellSize * 0.35f,
                center = center
            )
            drawCircle(
                color = mainColor,
                radius = cellSize * 0.38f,
                center = center,
                style = Stroke(width = 3.2f)
            )
        }
    }

    // Dynamic Player Name Label
    val playerName = player?.name ?: ""
    if (playerName.isNotBlank()) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = android.graphics.Color.WHITE
                this.textSize = cellSize * 0.44f
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                this.textAlign = Paint.Align.CENTER
                this.isAntiAlias = true
                this.setShadowLayer(4f, 0f, 2f, android.graphics.Color.parseColor("#99000000"))
            }

            val textX = left + (yardSize * 0.5f)
            val textY = if (isTopYard) {
                top + (cellSize * 0.58f)
            } else {
                top + yardSize - (cellSize * 0.22f)
            }
            canvas.nativeCanvas.drawText(playerName, textX, textY, paint)
        }
    }
}

/**
 * Draw 52 white track tiles, solid colored starting cells, and solid colored home columns.
 */
private fun DrawScope.drawTrackGrid(cellSize: Float) {
    val borderColor = Color(0xFFD0D6E2)

    // 1. Draw 52 white track cell tiles
    for (i in 0..51) {
        val pt = LudoBoardCoordinates.TRACK_GRID_POINTS[i]
        val cellTopLeft = Offset(pt.col * cellSize, pt.row * cellSize)
        drawRect(
            color = Color.White,
            topLeft = cellTopLeft,
            size = Size(cellSize, cellSize)
        )
        drawRect(
            color = borderColor,
            topLeft = cellTopLeft,
            size = Size(cellSize, cellSize),
            style = Stroke(width = 1f)
        )
    }

    // 2. Solid colored starting cells
    drawSolidTrackCell(PlayerColor.RED, 1f, 6f, cellSize)
    drawSolidTrackCell(PlayerColor.GREEN, 8f, 1f, cellSize)
    drawSolidTrackCell(PlayerColor.YELLOW, 13f, 8f, cellSize)
    drawSolidTrackCell(PlayerColor.BLUE, 6f, 13f, cellSize)

    // 3. Solid colored home columns (5 cells each leading to center)
    PlayerColor.entries.forEach { color ->
        val columns = LudoBoardCoordinates.HOME_COLUMNS[color] ?: emptyList()
        val c = getColorForPlayer(color)
        for (pt in columns) {
            drawRect(
                color = c,
                topLeft = Offset(pt.col * cellSize, pt.row * cellSize),
                size = Size(cellSize, cellSize)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(pt.col * cellSize, pt.row * cellSize),
                size = Size(cellSize, cellSize),
                style = Stroke(width = 1f)
            )
        }
    }
}

private fun DrawScope.drawSolidTrackCell(color: PlayerColor, col: Float, row: Float, cellSize: Float) {
    drawRect(
        color = getColorForPlayer(color),
        topLeft = Offset(col * cellSize, row * cellSize),
        size = Size(cellSize, cellSize)
    )
    drawRect(
        color = Color(0xFFCBD5E1),
        topLeft = Offset(col * cellSize, row * cellSize),
        size = Size(cellSize, cellSize),
        style = Stroke(width = 1f)
    )
}

/**
 * Draw directional entrance arrows pointing into the home columns.
 */
private fun DrawScope.drawEntranceArrows(cellSize: Float) {
    // Red Arrow pointing Right into Red home row (cell 0, 7)
    drawArrow(
        center = Offset(0.5f * cellSize, 7.5f * cellSize),
        direction = 0,
        color = LudoRed,
        size = cellSize * 0.52f
    )

    // Green Arrow pointing Down into Green home column (cell 7, 0)
    drawArrow(
        center = Offset(7.5f * cellSize, 0.5f * cellSize),
        direction = 1,
        color = LudoGreen,
        size = cellSize * 0.52f
    )

    // Yellow Arrow pointing Left into Yellow home row (cell 14, 7)
    drawArrow(
        center = Offset(14.5f * cellSize, 7.5f * cellSize),
        direction = 2,
        color = LudoYellow,
        size = cellSize * 0.52f
    )

    // Blue Arrow pointing Up into Blue home column (cell 7, 14)
    drawArrow(
        center = Offset(7.5f * cellSize, 14.5f * cellSize),
        direction = 3,
        color = LudoBlue,
        size = cellSize * 0.52f
    )
}

private fun DrawScope.drawArrow(center: Offset, direction: Int, color: Color, size: Float) {
    val path = Path()
    val stemHalf = size * 0.45f
    val wing = size * 0.28f

    when (direction) {
        0 -> { // Right (Red)
            // Stem
            path.moveTo(center.x - stemHalf, center.y)
            path.lineTo(center.x + stemHalf, center.y)
            // Arrowhead
            path.moveTo(center.x + stemHalf - wing, center.y - wing)
            path.lineTo(center.x + stemHalf, center.y)
            path.lineTo(center.x + stemHalf - wing, center.y + wing)
        }
        1 -> { // Down (Green)
            // Stem
            path.moveTo(center.x, center.y - stemHalf)
            path.lineTo(center.x, center.y + stemHalf)
            // Arrowhead
            path.moveTo(center.x - wing, center.y + stemHalf - wing)
            path.lineTo(center.x, center.y + stemHalf)
            path.lineTo(center.x + wing, center.y + stemHalf - wing)
        }
        2 -> { // Left (Yellow)
            // Stem
            path.moveTo(center.x + stemHalf, center.y)
            path.lineTo(center.x - stemHalf, center.y)
            // Arrowhead
            path.moveTo(center.x - stemHalf + wing, center.y - wing)
            path.lineTo(center.x - stemHalf, center.y)
            path.lineTo(center.x - stemHalf + wing, center.y + wing)
        }
        3 -> { // Up (Blue)
            // Stem
            path.moveTo(center.x, center.y + stemHalf)
            path.lineTo(center.x, center.y - stemHalf)
            // Arrowhead
            path.moveTo(center.x - wing, center.y - stemHalf + wing)
            path.lineTo(center.x, center.y - stemHalf)
            path.lineTo(center.x + wing, center.y - stemHalf + wing)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 3.0f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

/**
 * Draw 4 outlined safe stars on the standard safe cells.
 */
private fun DrawScope.drawSafeStars(cellSize: Float) {
    val starPoints = listOf(
        GridPoint(6f, 2f),
        GridPoint(12f, 6f),
        GridPoint(8f, 12f),
        GridPoint(2f, 8f)
    )

    for (pt in starPoints) {
        val center = Offset((pt.col + 0.5f) * cellSize, (pt.row + 0.5f) * cellSize)
        drawStarOutline(
            center = center,
            outerRadius = cellSize * 0.32f,
            innerRadius = cellSize * 0.15f,
            color = Color(0xFF94A3B8)
        )
    }
}

private fun DrawScope.drawStarOutline(center: Offset, outerRadius: Float, innerRadius: Float, color: Color) {
    val path = Path()
    val points = 5
    var angle = -Math.PI / 2
    val step = Math.PI / points

    for (i in 0 until (points * 2)) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        angle += step
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = 1.8f))
}

/**
 * Draw the 4 sharp center triangles meeting at (7.5, 7.5).
 */
private fun DrawScope.drawCenterTriangle(cellSize: Float) {
    val centerLeft = 6f * cellSize
    val centerTop = 6f * cellSize
    val centerRight = 9f * cellSize
    val centerBottom = 9f * cellSize
    val mid = Offset(7.5f * cellSize, 7.5f * cellSize)

    // Red triangle (Left)
    drawPath(
        path = Path().apply {
            moveTo(centerLeft, centerTop)
            lineTo(centerLeft, centerBottom)
            lineTo(mid.x, mid.y)
            close()
        },
        color = LudoRed
    )

    // Green triangle (Top)
    drawPath(
        path = Path().apply {
            moveTo(centerLeft, centerTop)
            lineTo(centerRight, centerTop)
            lineTo(mid.x, mid.y)
            close()
        },
        color = LudoGreen
    )

    // Yellow triangle (Right)
    drawPath(
        path = Path().apply {
            moveTo(centerRight, centerTop)
            lineTo(centerRight, centerBottom)
            lineTo(mid.x, mid.y)
            close()
        },
        color = LudoYellow
    )

    // Blue triangle (Bottom)
    drawPath(
        path = Path().apply {
            moveTo(centerLeft, centerBottom)
            lineTo(centerRight, centerBottom)
            lineTo(mid.x, mid.y)
            close()
        },
        color = LudoBlue
    )
}

/**
 * Draw all tokens as 3D Map-Pin Gotis matching Image 2.
 */
private fun DrawScope.drawAll3DTokens(
    gameState: GameState,
    cellSize: Float,
    pulseScale: Float,
    bounceOffset: Float
) {
    val currentPl = gameState.currentPlayer
    val validTokenIds = gameState.validTokenIds

    data class TokenPos(
        val playerColor: PlayerColor,
        val token: Token,
        val isSelectable: Boolean,
        val gridPoint: GridPoint
    )

    val tokensToDraw = mutableListOf<TokenPos>()

    for (player in gameState.players) {
        val isCurrentTurn = player.id == currentPl?.id
        for (token in player.tokens) {
            val isSelectable = isCurrentTurn && player.type == PlayerType.HUMAN && validTokenIds.contains(token.id)
            val pt = LudoBoardCoordinates.getGridPoint(player.color, token.id, token.stepCount)
            tokensToDraw.add(TokenPos(player.color, token, isSelectable, pt))
        }
    }

    val grouped = tokensToDraw.groupBy {
        if (it.token.stepCount == 0) {
            "${it.playerColor}_base_${it.token.id}"
        } else {
            "${it.gridPoint.col.toInt()}_${it.gridPoint.row.toInt()}"
        }
    }

    for ((_, tokensInCell) in grouped) {
        val count = tokensInCell.size
        for (idx in tokensInCell.indices) {
            val item = tokensInCell[idx]
            val pt = item.gridPoint
            val baseCenter = Offset((pt.col + 0.5f) * cellSize, (pt.row + 0.5f) * cellSize)

            val (offsetX, offsetY, scale) = when {
                count == 1 -> Triple(0f, 0f, 1.0f)
                count == 2 -> {
                    val dx = if (idx == 0) -cellSize * 0.14f else cellSize * 0.14f
                    val dy = if (idx == 0) -cellSize * 0.10f else cellSize * 0.10f
                    Triple(dx, dy, 0.88f)
                }
                count == 3 -> {
                    val angles = listOf(-Math.PI / 2, Math.PI / 6, 5 * Math.PI / 6)
                    val dx = (cellSize * 0.16f * cos(angles[idx])).toFloat()
                    val dy = (cellSize * 0.16f * sin(angles[idx])).toFloat()
                    Triple(dx, dy, 0.78f)
                }
                else -> {
                    val dx = if (idx % 2 == 0) -cellSize * 0.16f else cellSize * 0.16f
                    val dy = if (idx < 2) -cellSize * 0.16f else cellSize * 0.16f
                    Triple(dx, dy, 0.72f)
                }
            }

            val gotiCenter = Offset(baseCenter.x + offsetX, baseCenter.y + offsetY)
            val gotiSize = cellSize * 0.95f * scale

            draw3DGoti(
                center = gotiCenter,
                size = gotiSize,
                color = item.playerColor,
                isSelectable = item.isSelectable,
                pulseScale = pulseScale,
                bounceOffset = if (item.isSelectable) bounceOffset else 0f
            )
        }
    }
}
