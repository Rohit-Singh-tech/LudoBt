package com.ludobt.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Authentic royal blue background matching Image 2:
 * Large diamond tiled checkerboard with subtle dice pips.
 */
@Composable
fun LudoScreenBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Base Radial Blue Gradient (Bright royal blue in center, deeper navy at edges)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0F47AC),
                    Color(0xFF0B3380),
                    Color(0xFF051740)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.48f),
                radius = size.maxDimension * 0.70f
            )
        )

        // 2. Large Tiled Diamond Pattern (~105px)
        val tileSize = 105f
        val cols = (size.width / tileSize).toInt() + 4
        val rows = (size.height / tileSize).toInt() + 4

        for (r in -1..rows) {
            for (c in -1..cols) {
                val cx = c * tileSize + if (r % 2 == 0) 0f else tileSize * 0.5f
                val cy = r * tileSize

                rotate(degrees = 45f, pivot = Offset(cx, cy)) {
                    // Soft alternating diamond tiles
                    val isAlt = (r + c) % 2 == 0
                    if (isAlt) {
                        drawRoundRect(
                            color = Color(0xFF1E40AF).copy(alpha = 0.18f),
                            topLeft = Offset(cx - tileSize * 0.48f, cy - tileSize * 0.48f),
                            size = Size(tileSize * 0.96f, tileSize * 0.96f)
                        )
                    }

                    // Diamond outline
                    drawRoundRect(
                        color = Color(0xFF60A5FA).copy(alpha = 0.08f),
                        topLeft = Offset(cx - tileSize * 0.48f, cy - tileSize * 0.48f),
                        size = Size(tileSize * 0.96f, tileSize * 0.96f),
                        style = Stroke(width = 1.2f)
                    )

                    // Faint dice pips inside diamonds (varying by row and column)
                    val diceNum = ((r * 3 + c) % 6) + 1
                    val pipColor = Color(0xFF93C5FD).copy(alpha = 0.065f)
                    val pipR = tileSize * 0.045f
                    val d = tileSize * 0.18f

                    when (diceNum) {
                        1 -> {
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx, cy))
                        }
                        2 -> {
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy + d))
                        }
                        3 -> {
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx, cy))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy + d))
                        }
                        4 -> {
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy + d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy + d))
                        }
                        5 -> {
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx, cy))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy + d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy + d))
                        }
                        else -> { // 6
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx - d, cy + d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy - d))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy))
                            drawCircle(color = pipColor, radius = pipR, center = Offset(cx + d, cy + d))
                        }
                    }
                }
            }
        }
    }
}
