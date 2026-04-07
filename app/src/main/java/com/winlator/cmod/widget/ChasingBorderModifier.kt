package com.winlator.cmod.widget

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.toArgb
import com.winlator.cmod.core.ThemeManager

/**
 * Compose modifier that draws an animated chasing border using a rotating SweepGradient.
 * Uses Compose's [rememberInfiniteTransition] for smooth, frame-accurate animation.
 */
fun Modifier.chasingBorder(
    isFocused: Boolean = true,
    paused: Boolean = false,
    cornerRadius: Dp = 8.dp,
    borderWidth: Dp = 4.dp,
    animationDurationMs: Int = 5000
): Modifier = composed {
    if (!isFocused) return@composed this

    val theme = ThemeManager.getComposeColorScheme()
    val accentColor = theme.primary
    val surfaceColor = theme.surface
    val outlineColor = theme.outline

    val density = LocalDensity.current.density
    val cornerRadiusPx = cornerRadius.value * density
    val borderWidthPx = borderWidth.value * density

    val infiniteTransition = rememberInfiniteTransition(label = "chasingBorder")
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )
    val rotationDegrees = if (paused) 0f else animatedRotation

    val gradientColors = remember(accentColor) {
        intArrayOf(
            accentColor.toArgb(),
            accentColor.copy(alpha = 0.6f).toArgb(),
            outlineColor.toArgb(),
            accentColor.copy(alpha = 0.6f).toArgb(),
            accentColor.toArgb()
        )
    }
    val gradientStops = remember { floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f) }

    val drawState = remember {
        ChasingBorderDrawState(
            paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidthPx
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        )
    }

    this.drawWithContent {
        drawContent()

        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@drawWithContent

        // Rebuild path and shader when size changes
        if (drawState.lastWidth != w || drawState.lastHeight != h) {
            drawState.lastWidth = w
            drawState.lastHeight = h
            val inset = borderWidthPx / 2f
            drawState.rect.set(inset, inset, w - inset, h - inset)
            drawState.path.reset()
            drawState.path.addRoundRect(
                drawState.rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW
            )
            drawState.paint.shader = SweepGradient(
                w / 2f, h / 2f, gradientColors, gradientStops
            )
        }

        // Rotate the sweep gradient
        drawState.matrix.setRotate(rotationDegrees, w / 2f, h / 2f)
        drawState.paint.shader?.setLocalMatrix(drawState.matrix)

        drawContext.canvas.nativeCanvas.drawPath(drawState.path, drawState.paint)
    }
}

private class ChasingBorderDrawState(
    val paint: Paint,
    val path: Path = Path(),
    val rect: RectF = RectF(),
    val matrix: Matrix = Matrix(),
    var lastWidth: Float = -1f,
    var lastHeight: Float = -1f
)
