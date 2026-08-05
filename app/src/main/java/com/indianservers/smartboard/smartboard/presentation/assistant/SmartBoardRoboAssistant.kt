package com.indianservers.smartboard.smartboard.presentation.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

enum class RoboAssistantMood {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    NEEDS_ATTENTION,
}

/**
 * Reusable vector-only robot face for the board assistant.
 *
 * It has no recognition or cloud behavior. Phase 1 provides the visual shell;
 * later phases connect it to board state behind feature flags.
 */
@Composable
fun SmartBoardRoboAssistantFace(
    mood: RoboAssistantMood,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    reducedMotion: Boolean = false,
    contentDescription: String = "SMART Board assistant",
) {
    val transition = rememberInfiniteTransition(label = "robo-breath")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "robo-breath-progress",
    )
    val phase = if (reducedMotion) .5f else breath
    val glow = (.16f + .16f * sin(phase * Math.PI).toFloat()).coerceIn(.12f, .34f)
    val accent = when (mood) {
        RoboAssistantMood.IDLE -> Color(0xFF56D6FF)
        RoboAssistantMood.LISTENING -> Color(0xFF61E7B6)
        RoboAssistantMood.THINKING -> Color(0xFF9A8CFF)
        RoboAssistantMood.SPEAKING -> Color(0xFFFFC75B)
        RoboAssistantMood.NEEDS_ATTENTION -> Color(0xFFFF718B)
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val unit = this.size.minDimension / 100f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(accent.copy(alpha = glow), 48f * unit, center)
            drawCircle(Color(0xFF111B31), 40f * unit, center)
            drawCircle(accent.copy(alpha = .78f), 40f * unit, center, style = Stroke(2f * unit))

            drawLine(
                color = accent,
                start = Offset(center.x, 13f * unit),
                end = Offset(center.x, 5f * unit),
                strokeWidth = 2.6f * unit,
                cap = StrokeCap.Round,
            )
            drawCircle(accent, 3.5f * unit, Offset(center.x, 4f * unit))

            val face = Rect(
                left = 20f * unit,
                top = 27f * unit,
                right = 80f * unit,
                bottom = 73f * unit,
            )
            drawRoundRect(
                color = Color(0xFF1D2B49),
                topLeft = face.topLeft,
                size = face.size,
                cornerRadius = CornerRadius(15f * unit),
            )
            drawRoundRect(
                color = accent.copy(alpha = .9f),
                topLeft = face.topLeft,
                size = face.size,
                cornerRadius = CornerRadius(15f * unit),
                style = Stroke(2f * unit),
            )

            val blink = mood == RoboAssistantMood.THINKING && phase > .88f
            val eyeY = 47f * unit
            if (blink) {
                drawLine(accent, Offset(33f * unit, eyeY), Offset(43f * unit, eyeY), 2.8f * unit, StrokeCap.Round)
                drawLine(accent, Offset(57f * unit, eyeY), Offset(67f * unit, eyeY), 2.8f * unit, StrokeCap.Round)
            } else {
                val eyeRadius = if (mood == RoboAssistantMood.LISTENING) 5f else 4f
                drawCircle(accent, eyeRadius * unit, Offset(38f * unit, eyeY))
                drawCircle(accent, eyeRadius * unit, Offset(62f * unit, eyeY))
                drawCircle(Color.White.copy(alpha = .75f), 1.3f * unit, Offset(36.7f * unit, 45.7f * unit))
                drawCircle(Color.White.copy(alpha = .75f), 1.3f * unit, Offset(60.7f * unit, 45.7f * unit))
            }

            val mouth = Path().apply {
                when (mood) {
                    RoboAssistantMood.SPEAKING -> {
                        moveTo(42f * unit, 62f * unit)
                        quadraticTo(50f * unit, (67f + 2f * phase) * unit, 58f * unit, 62f * unit)
                    }
                    RoboAssistantMood.NEEDS_ATTENTION -> {
                        moveTo(43f * unit, 65f * unit)
                        quadraticTo(50f * unit, 59f * unit, 57f * unit, 65f * unit)
                    }
                    else -> {
                        moveTo(43f * unit, 61f * unit)
                        quadraticTo(50f * unit, 66f * unit, 57f * unit, 61f * unit)
                    }
                }
            }
            drawPath(mouth, accent, style = Stroke(2.5f * unit, cap = StrokeCap.Round))

            drawRoundRect(
                color = accent.copy(alpha = .45f),
                topLeft = Offset(8f * unit, 43f * unit),
                size = Size(8f * unit, 18f * unit),
                cornerRadius = CornerRadius(4f * unit),
            )
            drawRoundRect(
                color = accent.copy(alpha = .45f),
                topLeft = Offset(84f * unit, 43f * unit),
                size = Size(8f * unit, 18f * unit),
                cornerRadius = CornerRadius(4f * unit),
            )
        }
    }
}
