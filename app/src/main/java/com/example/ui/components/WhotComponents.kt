package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.example.game.WhotCard
import com.example.game.WhotSuit
import kotlin.math.cos
import kotlin.math.sin

// --- SUIT STYLING COLORS (NEON CYBERPUNK THEME) ---
val ColorCircle = Color(0xFF00E5FF)    // Neon Blue
val ColorTriangle = Color(0xFF39FF14)  // Neon Green
val ColorCross = Color(0xFF00FFFF)     // Cyan
val ColorSquare = Color(0xFFFFD700)    // Hot Gold
val ColorStar = Color(0xFFFF1493)      // Neon Pink
val ColorWhotBg = Color(0xFF0F111A)    // Dark glassmorphism background color for WHOT

fun getSuitColor(suit: WhotSuit): Color = when (suit) {
    WhotSuit.CIRCLE -> ColorCircle
    WhotSuit.TRIANGLE -> ColorTriangle
    WhotSuit.CROSS -> ColorCross
    WhotSuit.SQUARE -> ColorSquare
    WhotSuit.STAR -> ColorStar
    WhotSuit.WHOT -> Color(0xFFBD00FF) // Neon Purple/Violet for wild card
}

// Custom Neon Glow drawing modifier
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 10.dp,
    intensity: Float = 1.0f,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
): Modifier = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    val rPx = radius.toPx()
    // Layer multiple semi-transparent rounded rectangles to construct a fuzzy outer glow
    for (i in 1..4) {
        val inset = (i * 1.5f).dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = (0.15f * intensity) / i),
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx + inset),
            style = Stroke(width = strokeWidth * 1.5f)
        )
    }
}

@Composable
fun CyberpunkBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF3E2723)) // Rich warm mahogany/espresso brown instead of dark slate/black!
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val lineColor = Color(0xFF5D4037).copy(alpha = 0.25f) // Warm cocoa grid lines
            val strokeWidth = 1.dp.toPx()

            // Vertical grid lines
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth
                )
                x += gridSpacing
            }

            // Horizontal grid lines
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
                y += gridSpacing
            }
        }
        content()
    }
}

// Draw a five-pointed star path
fun createStarPath(cx: Float, cy: Float, spikes: Int, outerRadius: Float, innerRadius: Float): Path {
    val path = Path()
    var rot = -Math.PI / 2
    val step = Math.PI / spikes

    path.moveTo(cx, (cy - outerRadius))
    for (i in 0 until spikes) {
        var x = cx + cos(rot).toFloat() * outerRadius
        var y = cy + sin(rot).toFloat() * outerRadius
        path.lineTo(x, y)
        rot += step

        x = cx + cos(rot).toFloat() * innerRadius
        y = cy + sin(rot).toFloat() * innerRadius
        path.lineTo(x, y)
        rot += step
    }
    path.close()
    return path
}

@Composable
fun WhotSymbol(
    suit: WhotSuit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    color: Color? = null
) {
    val suitColor = color ?: getSuitColor(suit)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val padding = w * 0.15f
        val r = (w - padding * 2) / 2f

        when (suit) {
            WhotSuit.CIRCLE -> {
                if (fill) {
                    drawCircle(color = suitColor, radius = r, center = Offset(cx, cy))
                } else {
                    drawCircle(color = suitColor, radius = r, center = Offset(cx, cy), style = Stroke(width = w * 0.12f))
                }
            }
            WhotSuit.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(cx, padding)
                    lineTo(w - padding, h - padding)
                    lineTo(padding, h - padding)
                    close()
                }
                if (fill) {
                    drawPath(path, color = suitColor)
                } else {
                    drawPath(path, color = suitColor, style = Stroke(width = w * 0.12f))
                }
            }
            WhotSuit.CROSS -> {
                val thickness = w * 0.22f
                val length = w - padding * 2
                // Draw horizontal bar
                drawRect(
                    color = suitColor,
                    topLeft = Offset(padding, cy - thickness / 2),
                    size = Size(length, thickness)
                )
                // Draw vertical bar
                drawRect(
                    color = suitColor,
                    topLeft = Offset(cx - thickness / 2, padding),
                    size = Size(thickness, length)
                )
            }
            WhotSuit.SQUARE -> {
                val side = r * 1.5f
                val topLeftX = cx - side / 2
                val topLeftY = cy - side / 2
                if (fill) {
                    drawRect(
                        color = suitColor,
                        topLeft = Offset(topLeftX, topLeftY),
                        size = Size(side, side)
                    )
                } else {
                    drawRect(
                        color = suitColor,
                        topLeft = Offset(topLeftX, topLeftY),
                        size = Size(side, side),
                        style = Stroke(width = w * 0.12f)
                    )
                }
            }
            WhotSuit.STAR -> {
                val starPath = createStarPath(
                    cx = cx,
                    cy = cy,
                    spikes = 5,
                    outerRadius = r * 1.1f,
                    innerRadius = r * 0.45f
                )
                if (fill) {
                    drawPath(starPath, color = suitColor)
                } else {
                    drawPath(starPath, color = suitColor, style = Stroke(width = w * 0.12f))
                }
            }
            WhotSuit.WHOT -> {
                // Wild Whot design: Custom rainbow circles or a colorful spiral
                val colors = listOf(ColorCircle, ColorTriangle, ColorCross, ColorSquare, ColorStar)
                val radialBrush = Brush.sweepGradient(colors, Offset(cx, cy))
                drawCircle(brush = radialBrush, radius = r, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = r * 0.45f, center = Offset(cx, cy))
                drawCircle(color = ColorWhotBg, radius = r * 0.25f, center = Offset(cx, cy))
            }
        }
    }
}

@Composable
fun WhotCardWidget(
    card: WhotCard,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 120.dp,
    elevation: Dp = 4.dp,
    pulseScale: Float = 1f,
    glowIntensity: Float = 1f
) {
    val view = LocalView.current
    val isWhotCard = card.isWild
    val primaryColor = getSuitColor(card.suit)
    val WhotClassicRed = Color(0xFF6A0C0E)

    Card(
        modifier = modifier
            .width(width)
            .height(height)
            .neonGlow(color = primaryColor, radius = 10.dp, intensity = glowIntensity)
            .clickable(enabled = onClick != null) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick?.invoke()
            }
            .testTag("card_${card.suit}_${card.number}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFBF9F6) // Off-white cardstock background
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // Top Left Corner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
            ) {
                if (isWhotCard) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "20",
                            fontSize = (width.value * 0.18f).sp,
                            fontWeight = FontWeight.Bold,
                            color = WhotClassicRed,
                            fontFamily = FontFamily.Serif,
                            lineHeight = (width.value * 0.18f).sp
                        )
                        Text(
                            text = "w",
                            fontSize = (width.value * 0.14f).sp,
                            fontWeight = FontWeight.Normal,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = WhotClassicRed,
                            fontFamily = FontFamily.Serif,
                            lineHeight = (width.value * 0.12f).sp,
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                } else {
                    WhotSymbol(
                        suit = card.suit,
                        color = WhotClassicRed,
                        modifier = Modifier.size((width.value * 0.15f).dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = card.number.toString(),
                        fontSize = (width.value * 0.22f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WhotClassicRed,
                        fontFamily = FontFamily.Serif,
                        lineHeight = (width.value * 0.22f).sp
                    )
                }
            }

            // Big Central Symbol
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((width.value * 0.55f).dp),
                contentAlignment = Alignment.Center
            ) {
                if (isWhotCard) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Whot",
                            fontSize = (width.value * 0.28f).sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = WhotClassicRed,
                            lineHeight = (width.value * 0.25f).sp
                        )
                        Text(
                            text = "Whot",
                            fontSize = (width.value * 0.28f).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = WhotClassicRed,
                            lineHeight = (width.value * 0.25f).sp,
                            modifier = Modifier.offset(x = (width.value * 0.05f).dp, y = (-width.value * 0.03f).dp)
                        )
                    }
                } else {
                    WhotSymbol(
                        suit = card.suit,
                        color = WhotClassicRed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Right Corner (rotated 180 degrees)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .rotate(180f)
            ) {
                if (isWhotCard) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "20",
                            fontSize = (width.value * 0.18f).sp,
                            fontWeight = FontWeight.Bold,
                            color = WhotClassicRed,
                            fontFamily = FontFamily.Serif,
                            lineHeight = (width.value * 0.18f).sp
                        )
                        Text(
                            text = "w",
                            fontSize = (width.value * 0.14f).sp,
                            fontWeight = FontWeight.Normal,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = WhotClassicRed,
                            fontFamily = FontFamily.Serif,
                            lineHeight = (width.value * 0.12f).sp,
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                } else {
                    WhotSymbol(
                        suit = card.suit,
                        color = WhotClassicRed,
                        modifier = Modifier.size((width.value * 0.15f).dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = card.number.toString(),
                        fontSize = (width.value * 0.22f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WhotClassicRed,
                        fontFamily = FontFamily.Serif,
                        lineHeight = (width.value * 0.22f).sp
                    )
                }
            }
        }
    }
}

@Composable
fun CardBackWidget(
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 120.dp,
    glowColor: Color = Color(0xFF00E5FF) // Cyberpunk glowing cyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "drawGlow")
    val animatedIntensity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drawGlowIntensity"
    )
    val WhotClassicRed = Color(0xFF6A0C0E)
    val CardBackBg = Color(0xFFFBF9F6)

    Card(
        modifier = modifier
            .width(width)
            .height(height)
            .neonGlow(color = glowColor, radius = 10.dp, intensity = animatedIntensity)
            .border(1.5.dp, glowColor.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .background(CardBackBg)
        ) {
            // Geometrical elegant lines pattern on card back using classic red
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 15.dp.toPx()
                val lineStroke = Stroke(width = 1.dp.toPx())
                val redColor = WhotClassicRed.copy(alpha = 0.15f)

                // Horizontal lines
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = redColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = lineStroke.width
                    )
                    y += step
                }

                // Vertical lines
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = redColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineStroke.width
                    )
                    x += step
                }

                // Decorative center diamond/rect in classic red
                drawRect(
                    color = WhotClassicRed.copy(alpha = 0.3f),
                    topLeft = Offset(size.width * 0.3f, size.height * 0.4f),
                    size = Size(size.width * 0.4f, size.height * 0.2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Text(
                text = "WHOT!",
                color = WhotClassicRed,
                fontSize = (width.value * 0.16f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun PlayerHandRow(
    cards: List<WhotCard>,
    onCardClick: (WhotCard) -> Unit,
    playableCheck: (WhotCard) -> Boolean,
    isPlayerTurn: Boolean = true,
    viewAsColumns: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(175.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Empty Hand",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontFamily = FontFamily.Monospace
            )
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val glowIntensity by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowIntensity"
        )

        if (viewAsColumns) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp) // Large scrollable columns space
                    .testTag("player_hand_grid"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                    val isPlayable = playableCheck(card)
                    val hoverOffset = if (isPlayable) (-12).dp else 0.dp
                    val activePulse = if (isPlayable && isPlayerTurn) pulseScale else 1f
                    val activeGlow = if (isPlayable && isPlayerTurn) glowIntensity else 0.7f

                    WhotCardWidget(
                        card = card,
                        onClick = { onCardClick(card) },
                        width = 110.dp,
                        height = 160.dp,
                        elevation = if (isPlayable) 8.dp else 2.dp,
                        pulseScale = activePulse,
                        glowIntensity = activeGlow,
                        modifier = Modifier
                            .offset(y = hoverOffset)
                            .graphicsLayer {
                                scaleX = activePulse
                                scaleY = activePulse
                            }
                            .border(
                                width = if (isPlayable) 2.dp else 0.5.dp,
                                color = if (isPlayable) getSuitColor(card.suit) else getSuitColor(card.suit).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                }
            }
        } else {
            LazyRow(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("player_hand_row"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((-24).dp) // Elegant fan-out overlapping layout for larger cards
            ) {
                itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                    val isPlayable = playableCheck(card)
                    val hoverOffset = if (isPlayable) (-12).dp else 0.dp
                    val activePulse = if (isPlayable && isPlayerTurn) pulseScale else 1f
                    val activeGlow = if (isPlayable && isPlayerTurn) glowIntensity else 0.7f

                    WhotCardWidget(
                        card = card,
                        onClick = { onCardClick(card) },
                        width = 110.dp,
                        height = 160.dp,
                        elevation = if (isPlayable) 8.dp else 2.dp,
                        pulseScale = activePulse,
                        glowIntensity = activeGlow,
                        modifier = Modifier
                            .offset(y = hoverOffset) // Pop up playable cards dynamically
                            .graphicsLayer {
                                scaleX = activePulse
                                scaleY = activePulse
                            }
                            .border(
                                width = if (isPlayable) 2.dp else 0.5.dp,
                                color = if (isPlayable) getSuitColor(card.suit) else getSuitColor(card.suit).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun SuitSelectionDialog(
    onSuitSelected: (WhotSuit) -> Unit
) {
    Dialog(
        onDismissRequest = {}, // Force player to make a choice
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = Color(0xE61D0E0A),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .neonGlow(Color(0xFFBD00FF), radius = 12.dp)
                .border(1.5.dp, Color(0xFFBD00FF).copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .testTag("suit_selection_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WHOT! WILD CARD",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFBD00FF),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CHOOSE THE NEXT SUIT:",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Five suit select grid
                val suits = listOf(
                    WhotSuit.CIRCLE,
                    WhotSuit.TRIANGLE,
                    WhotSuit.CROSS,
                    WhotSuit.SQUARE,
                    WhotSuit.STAR
                )

                val view = LocalView.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    suits.forEach { suit ->
                        val suitColor = getSuitColor(suit)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0B0C10).copy(alpha = 0.6f))
                                .border(1.dp, suitColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSuitSelected(suit)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("select_${suit.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhotSymbol(
                                suit = suit,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = suit.getDisplayName().uppercase(),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Particle model for confetti
class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float
) {
    var rotation: Float = 0f
    fun update() {
        x += vx
        y += vy
        vy += 0.15f // gravity
        rotation += rotationSpeed
    }
}

@Composable
fun ConfettiScreen() {
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }

    LaunchedEffect(Unit) {
        val colors = listOf(ColorCircle, ColorTriangle, ColorCross, ColorSquare, ColorStar, Color.White, Color.Yellow)
        val list = mutableListOf<ConfettiParticle>()
        repeat(120) {
            list.add(
                ConfettiParticle(
                    x = (200..800).random().toFloat(),
                    y = -20f,
                    vx = (-4..4).random().toFloat(),
                    vy = (2..8).random().toFloat(),
                    size = (10..22).random().toFloat(),
                    color = colors.random(),
                    rotationSpeed = (-10..10).random().toFloat()
                )
            )
        }
        particles = list

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 5000) {
            particles.forEach { it.update() }
            particles = particles.filter { it.y < 2500 }
            delay(16)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            drawRect(
                color = p.color,
                topLeft = Offset(p.x, p.y),
                size = Size(p.size, p.size * 0.6f)
            )
        }
    }
}
