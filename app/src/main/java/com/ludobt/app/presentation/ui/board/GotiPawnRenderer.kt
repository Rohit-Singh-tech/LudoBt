package com.ludobt.app.presentation.ui.board

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ludobt.app.R
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.LudoBlue
import com.ludobt.app.presentation.theme.LudoBlueLight
import com.ludobt.app.presentation.theme.LudoGreen
import com.ludobt.app.presentation.theme.LudoGreenLight
import com.ludobt.app.presentation.theme.LudoRed
import com.ludobt.app.presentation.theme.LudoRedLight
import com.ludobt.app.presentation.theme.LudoYellow
import com.ludobt.app.presentation.theme.LudoYellowLight
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache and provider for high-resolution 3D Goti bitmaps matching Image 1.
 */
object GotiBitmapProvider {
    private val cache = ConcurrentHashMap<PlayerColor, ImageBitmap>()

    fun preload(context: Context) {
        val appContext = context.applicationContext
        PlayerColor.entries.forEach { color ->
            if (!cache.containsKey(color)) {
                val resId = when (color) {
                    PlayerColor.RED -> R.drawable.goti_red
                    PlayerColor.GREEN -> R.drawable.goti_green
                    PlayerColor.YELLOW -> R.drawable.goti_yellow
                    PlayerColor.BLUE -> R.drawable.goti_blue
                }
                try {
                    val bmp = BitmapFactory.decodeResource(appContext.resources, resId)
                    if (bmp != null) {
                        cache[color] = bmp.asImageBitmap()
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun get(color: PlayerColor): ImageBitmap? = cache[color]
}

/**
 * Draw the authentic 3D Glossy Pawn Goti matching Image 1:
 * - Realistic ground contact shadow on the board cell
 * - Animated pulsing golden halo ring when movable
 * - Ultra-crisp 3D candy pawn bitmap with specular reflections, conical trunk, and beveled disc base
 * - High-fidelity 3D vector fallback if bitmap is loading
 */
fun DrawScope.draw3DGoti(
    center: Offset,
    size: Float,
    color: PlayerColor,
    isSelectable: Boolean = false,
    pulseScale: Float = 1.0f,
    bounceOffset: Float = 0.0f
) {
    // 1. Realistic ground shadow (stays on board tile even when goti bounces)
    val shadowW = size * 0.74f * (1.0f - (bounceOffset / (size * 2.5f)).coerceIn(0f, 0.35f))
    val shadowH = size * 0.26f * (1.0f - (bounceOffset / (size * 2.5f)).coerceIn(0f, 0.35f))
    val shadowY = center.y + (size * 0.40f)

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x99000000),
                Color(0x4D000000),
                Color.Transparent
            ),
            center = Offset(center.x, shadowY),
            radius = shadowW * 0.5f
        ),
        topLeft = Offset(center.x - (shadowW * 0.5f), shadowY - (shadowH * 0.5f)),
        size = Size(shadowW, shadowH)
    )

    // 2. Pulsing golden selection aura when selectable
    if (isSelectable) {
        val auraCenter = Offset(center.x, center.y - (size * 0.05f) - bounceOffset)
        val auraRadius = size * 0.58f * pulseScale

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AccentGold.copy(alpha = 0.45f),
                    AccentGold.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = auraCenter,
                radius = auraRadius
            ),
            radius = auraRadius,
            center = auraCenter
        )

        drawCircle(
            color = AccentGold,
            radius = size * 0.50f * pulseScale,
            center = auraCenter,
            style = Stroke(width = 3.0f)
        )
    }

    // 3. Render 3D Goti
    val cachedBitmap = GotiBitmapProvider.get(color)
    if (cachedBitmap != null) {
        // High-resolution Image 1 3D asset (aspect ratio 2:3)
        val gotiH = size * 1.20f
        val gotiW = gotiH * (256f / 384f)
        val left = (center.x - (gotiW * 0.5f)).toInt()
        val top = (center.y - (gotiH * 0.58f) - bounceOffset).toInt()

        drawImage(
            image = cachedBitmap,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(gotiW.toInt(), gotiH.toInt()),
            filterQuality = FilterQuality.High
        )
    } else {
        // High-Fidelity 3D Vector Fallback matching Image 1
        drawVector3DGotiFallback(
            center = Offset(center.x, center.y - bounceOffset),
            size = size,
            color = color
        )
    }
}

/**
 * Procedural 3D Vector pawn matching Image 1 anatomy:
 * Beveled cylindrical disc base -> Conical body -> Collar ring -> Spherical head with dual specular highlights.
 */
private fun DrawScope.drawVector3DGotiFallback(
    center: Offset,
    size: Float,
    color: PlayerColor
) {
    val mainColor = getColorForPlayer(color)
    val lightColor = getLightColorForPlayer(color)
    val darkColor = getDarkColorForPlayer(color)

    val w = size * 0.70f
    val h = size * 1.15f

    val baseY = center.y + (h * 0.38f)
    val baseW = w * 0.90f
    val baseH = h * 0.22f

    // 1. Cylindrical Pedestal Disc Base (Image 1)
    // Base lower rim
    drawOval(
        brush = Brush.horizontalGradient(
            listOf(darkColor, mainColor, darkColor),
            startX = center.x - baseW * 0.5f,
            endX = center.x + baseW * 0.5f
        ),
        topLeft = Offset(center.x - baseW * 0.5f, baseY - baseH * 0.2f),
        size = Size(baseW, baseH)
    )

    // Base upper beveled rim highlight
    drawOval(
        brush = Brush.radialGradient(
            listOf(lightColor, mainColor, darkColor),
            center = Offset(center.x - baseW * 0.15f, baseY - baseH * 0.35f),
            radius = baseW * 0.55f
        ),
        topLeft = Offset(center.x - baseW * 0.46f, baseY - baseH * 0.55f),
        size = Size(baseW * 0.92f, baseH * 0.85f)
    )

    // 2. Conical Body Trunk
    val neckY = center.y - (h * 0.12f)
    val neckW = w * 0.32f
    val coneBaseW = baseW * 0.78f
    val coneBaseY = baseY - (baseH * 0.35f)

    val conePath = Path().apply {
        moveTo(center.x - neckW * 0.5f, neckY)
        lineTo(center.x + neckW * 0.5f, neckY)
        lineTo(center.x + coneBaseW * 0.5f, coneBaseY)
        lineTo(center.x - coneBaseW * 0.5f, coneBaseY)
        close()
    }

    drawPath(
        path = conePath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                darkColor,
                lightColor,
                mainColor,
                darkColor
            ),
            startX = center.x - coneBaseW * 0.5f,
            endX = center.x + coneBaseW * 0.5f
        )
    )

    // 3. Collar Ring
    val collarH = h * 0.08f
    drawOval(
        brush = Brush.horizontalGradient(
            listOf(darkColor, lightColor, darkColor),
            startX = center.x - neckW * 0.6f,
            endX = center.x + neckW * 0.6f
        ),
        topLeft = Offset(center.x - neckW * 0.6f, neckY - collarH * 0.5f),
        size = Size(neckW * 1.2f, collarH)
    )

    // 4. Spherical Head (Ball at top)
    val headR = w * 0.34f
    val headCenter = Offset(center.x, neckY - headR * 0.9f)

    // 3D Spherical Head Fill
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lightColor,
                mainColor,
                darkColor
            ),
            center = Offset(headCenter.x - headR * 0.28f, headCenter.y - headR * 0.28f),
            radius = headR * 1.2f
        ),
        radius = headR,
        center = headCenter
    )

    // Specular Highlight (Primary bright glare matching Image 1)
    drawOval(
        color = Color.White.copy(alpha = 0.95f),
        topLeft = Offset(headCenter.x - headR * 0.62f, headCenter.y - headR * 0.68f),
        size = Size(headR * 0.55f, headR * 0.40f)
    )

    // Secondary rim reflection
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = headR * 0.12f,
        center = Offset(headCenter.x + headR * 0.45f, headCenter.y + headR * 0.30f)
    )
}

fun getColorForPlayer(color: PlayerColor): Color {
    return when (color) {
        PlayerColor.RED -> LudoRed
        PlayerColor.GREEN -> LudoGreen
        PlayerColor.YELLOW -> LudoYellow
        PlayerColor.BLUE -> LudoBlue
    }
}

fun getLightColorForPlayer(color: PlayerColor): Color {
    return when (color) {
        PlayerColor.RED -> LudoRedLight
        PlayerColor.GREEN -> LudoGreenLight
        PlayerColor.YELLOW -> LudoYellowLight
        PlayerColor.BLUE -> LudoBlueLight
    }
}

fun getDarkColorForPlayer(color: PlayerColor): Color {
    return when (color) {
        PlayerColor.RED -> Color(0xFF991B1B)
        PlayerColor.GREEN -> Color(0xFF065F46)
        PlayerColor.YELLOW -> Color(0xFFB45309)
        PlayerColor.BLUE -> Color(0xFF1E40AF)
    }
}
