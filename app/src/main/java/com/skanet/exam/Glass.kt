package com.skanet.exam

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ui {
    val Bg1 = Color(0xFFFFFFFF)
    val Bg2 = Color(0xFFFFF4EA)
    val Orange = Color(0xFFFF7A1A)
    val Ember = Color(0xFFF2542D)
    val Red = Color(0xFFE5383B)
    val Ink = Color(0xFF1D1B20)
    val Muted = Color(0xFF6B6F7B)
}

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "bg")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "shift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ui.Bg1, Ui.Bg2)))
            .drawBehind {
                val r = size.width * 0.7f
                fun blob(color: Color, alpha: Float, center: Offset) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center
                    )
                }
                blob(Ui.Orange, 0.22f, Offset(size.width * (0.95f - 0.25f * shift), size.height * 0.10f))
                blob(Ui.Orange, 0.12f, Offset(size.width * (0.05f + 0.20f * shift), size.height * 0.55f))
                blob(Ui.Red, 0.09f, Offset(size.width * (0.20f + 0.30f * shift), size.height * 1.00f))
            }
    ) {
        CompositionLocalProvider(LocalContentColor provides Ui.Ink) {
            ProvideTextStyle(TextStyle(fontFamily = Ubuntu)) {
                content()
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.45f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White, Ui.Orange.copy(alpha = 0.30f))
                ),
                shape
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Ui.Orange, Ui.Ember))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFEAE7E3), Color(0xFFEAE7E3)))
    }
    val glow: Dp = if (enabled) 10.dp else 0.dp

    Box(
        modifier = modifier
            .height(58.dp)
            .shadow(glow, shape, ambientColor = Ui.Orange, spotColor = Ui.Orange)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Ui.Muted,
            fontFamily = Ubuntu,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun PulseDot(color: Color = Ui.Orange) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}