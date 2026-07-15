package com.example

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GameStatsRepository
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.GamePhase
import com.example.game.GameUiState
import com.example.game.WhotCard
import com.example.game.WhotSuit
import com.example.game.WhotViewModel
import com.example.game.WhotViewModelFactory
import com.example.ui.components.CardBackWidget
import com.example.ui.components.ConfettiScreen
import com.example.ui.components.CyberpunkBackground
import com.example.ui.components.PlayerHandRow
import com.example.ui.components.SuitSelectionDialog
import com.example.ui.components.WhotCardWidget
import com.example.ui.components.WhotSymbol
import com.example.ui.components.getSuitColor
import com.example.ui.components.neonGlow
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = GameStatsRepository(database.gameStatsDao())

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0)
                ) { innerPadding ->
                    val viewModel: WhotViewModel = viewModel(
                        factory = WhotViewModelFactory(repository)
                    )
                    WhotGameScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun WhotGameScreen(
    viewModel: WhotViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats by viewModel.gameStats.collectAsStateWithLifecycle()
    val view = LocalView.current

    CyberpunkBackground(
        modifier = modifier.fillMaxSize()
    ) {
        if (!state.gameStarted) {
            StartScreen(
                wins = stats?.wins ?: 0,
                losses = stats?.losses ?: 0,
                gamesPlayed = stats?.gamesPlayed ?: 0,
                bestScore = stats?.bestScore ?: 999,
                isAiMode = state.isAiMode,
                onModeChange = { isAi -> viewModel.toggleGameMode(isAi) },
                onDealClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.startNewGame()
                },
                onResetStats = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewModel.resetStats()
                }
            )
        } else {
            PlayingScreen(
                state = state,
                onModeChange = { isAi -> viewModel.toggleGameMode(isAi) },
                onCardClick = { card -> viewModel.playCard(card) },
                onDrawClick = { viewModel.drawCard() },
                onPassClick = { viewModel.passTurn() },
                onExitClick = { viewModel.quitToMenu() }
            )
        }

        // 1. Suit selection wild popup overlay
        if (state.showSuitSelection) {
            SuitSelectionDialog(
                onSuitSelected = { suit ->
                    viewModel.selectDemandedSuit(suit)
                }
            )
        }

        // 2. Victory Confetti Overlay
        if (state.showConfetti) {
            ConfettiScreen()
        }

        // 3. Game Over Popup
        if (state.phase == GamePhase.GAME_OVER) {
            GameOverDialog(
                playerWon = state.playerWon,
                isAiMode = state.isAiMode,
                score = state.lastScore,
                onPlayAgain = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.startNewGame()
                }
            )
        }

        // 4. Pass Device Overlay (Pass-and-Play mode)
        if (!state.isAiMode && state.showPassDeviceOverlay && state.phase == GamePhase.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFA1D0E0A))
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                val activePlayerNum = if (state.isPlayerTurn) "1" else "2"
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xD91D0E0A)),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .neonGlow(Color(0xFFFF1493), intensity = 0.6f)
                        .border(1.dp, Color(0xFFFF1493).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Pass Device",
                            tint = Color(0xFFFF1493), // Neon Pink
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "PLAYER $activePlayerNum'S TURN!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pass the device to Player $activePlayerNum. Hide your screen from other players!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = { viewModel.revealHand() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF), // Neon Cyan
                                contentColor = Color(0xFF0B0C10)    // Dark Navy
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .neonGlow(Color(0xFF00E5FF), radius = 8.dp)
                                .testTag("reveal_hand_button"),
                            elevation = ButtonDefaults.buttonElevation(4.dp)
                        ) {
                            Text(
                                text = "REVEAL MY HAND",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartScreen(
    wins: Int,
    losses: Int,
    gamesPlayed: Int,
    bestScore: Int,
    isAiMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onDealClick: () -> Unit,
    onResetStats: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowRadiusFloat by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )
    val glowRadius = glowRadiusFloat.dp
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Hero Branding
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xD91D0E0A)),
            modifier = Modifier
                .neonGlow(Color(0xFF00E5FF), intensity = 0.5f)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Suit Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhotSymbol(com.example.game.WhotSuit.CIRCLE, modifier = Modifier.size(24.dp))
                    WhotSymbol(com.example.game.WhotSuit.TRIANGLE, modifier = Modifier.size(24.dp))
                    WhotSymbol(com.example.game.WhotSuit.STAR, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "NIGERIAN WHOT!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00E5FF), // Cyber Neon Cyan
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.neonGlow(Color(0xFF00E5FF), radius = 8.dp)
                )
                Text(
                    text = "NIGERIA'S NO.1 CARD GAME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF1493), // Neon Pink accent
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Game Mode Selector
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1D0E0A))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val view = LocalView.current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isAiMode) Color(0xFF00E5FF) else Color.Transparent)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onModeChange(true)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("mode_computer_button")
            ) {
                Text(
                    text = "vs Computer",
                    color = if (isAiMode) Color(0xFF0B0C10) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (!isAiMode) Color(0xFF00E5FF) else Color.Transparent)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onModeChange(false)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("mode_local_button")
            ) {
                Text(
                    text = "Pass & Play",
                    color = if (!isAiMode) Color(0xFF0B0C10) else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xD91D0E0A)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIFETIME STATISTICS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onResetStats,
                        modifier = Modifier.size(24.dp).testTag("reset_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Reset stats",
                            tint = Color(0xFFFF1493).copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatMetric(label = "PLAYED", value = gamesPlayed.toString())
                    StatMetric(label = "WINS", value = wins.toString(), color = Color(0xFF39FF14)) // Neon Green
                    StatMetric(label = "LOSSES", value = losses.toString(), color = Color(0xFFFF1493)) // Neon Pink
                    StatMetric(
                        label = "BEST",
                        value = if (bestScore == 999) "-" else bestScore.toString(),
                        color = Color(0xFFFFD700) // Hot Gold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Big Deal Button
        Button(
            onClick = onDealClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E5FF), // Cyber Neon Cyan
                contentColor = Color(0xFF0B0C10)    // Dark Navy
            ),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier
                .widthIn(min = 200.dp)
                .height(56.dp)
                .scale(scale)
                .neonGlow(Color(0xFF00E5FF), radius = glowRadius)
                .testTag("deal_button"),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY GAME",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun StatMetric(
    label: String,
    value: String,
    color: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PlayingScreen(
    state: GameUiState,
    onModeChange: (Boolean) -> Unit,
    onCardClick: (WhotCard) -> Unit,
    onDrawClick: () -> Unit,
    onPassClick: () -> Unit,
    onExitClick: () -> Unit
) {
    var viewAsColumns by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Game Mode Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExitClick,
                    modifier = Modifier.size(28.dp).testTag("quit_to_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Quit to Menu",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GAME MODE:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            
            // Miniature elegant Mode Toggle Switch
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1D0E0A))
                    .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.isAiMode) Color(0xFF00E5FF) else Color.Transparent)
                        .clickable { onModeChange(true) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("playing_mode_computer_button")
                ) {
                    Text(
                        text = "VS COMP",
                        color = if (state.isAiMode) Color(0xFF0B0C10) else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!state.isAiMode) Color(0xFF00E5FF) else Color.Transparent)
                        .clickable { onModeChange(false) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("playing_mode_local_button")
                ) {
                    Text(
                        text = "PASS & PLAY",
                        color = if (!state.isAiMode) Color(0xFF0B0C10) else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 1. TOP BAR: Player 2 (AI) status and card count
        OpponentStatusBar(
            cardCount = state.opponentHandSize,
            isThinking = !state.isPlayerTurn,
            isAiMode = state.isAiMode,
            isPlayerTurn = state.isPlayerTurn
        )

        // 2. CENTRAL GAME BOARD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // State penalty badge or active demanded suit glow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                // Demand Glow
                if (state.demandedSuit != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(getSuitColor(state.demandedSuit).copy(alpha = 0.15f))
                            .border(1.dp, getSuitColor(state.demandedSuit), RoundedCornerShape(50.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WhotSymbol(suit = state.demandedSuit, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WANTED: ${state.demandedSuit.getDisplayName().uppercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Penalty Badge
                if (state.activePenalty > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFFFF1493).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFF1493), RoundedCornerShape(50.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PENALTY: DRAW +${state.activePenalty}!",
                            color = Color(0xFFFF1493),
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Draw and Discard pile row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DRAW PILE (Face down)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onDrawClick() }.testTag("draw_pile")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CardBackWidget(width = 105.dp, height = 155.dp)
                        // Deck Count indicator badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(28.dp)
                                .background(Color(0xFF00E5FF), CircleShape)
                                .border(1.5.dp, Color(0xFF0B0C10), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.drawPileSize.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0B0C10),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "DRAW PILE",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(36.dp))

                // DISCARD PILE (Face up showing active card)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        state.activeCard?.let { active ->
                            WhotCardWidget(
                                card = active,
                                onClick = null, // Static face up card
                                width = 105.dp,
                                height = 155.dp,
                                elevation = 8.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "DISCARD PILE",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Status message card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xD91D0E0A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (state.isPlayerTurn) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = state.turnMessage,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Decorative horizontal glowing divider line above bottom player area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
        )

        // 3. BOTTOM PLAYER AREA: Player 1's hand
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE61D0E0A))
                .padding(vertical = 12.dp)
        ) {
            // Action Panel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (state.isPlayerTurn) Color(0xFF39FF14) else Color(0xFFFF1493),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isAiMode) {
                            if (state.isPlayerTurn) "YOUR TURN" else "AI TURN"
                        } else {
                            if (state.isPlayerTurn) "PLAYER 1'S TURN" else "PLAYER 2'S TURN"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Control buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Toggle layout view (Row vs Columns grid)
                    IconButton(
                        onClick = { viewAsColumns = !viewAsColumns },
                        modifier = Modifier.size(36.dp).testTag("toggle_layout_button")
                    ) {
                        Icon(
                            imageVector = if (viewAsColumns) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "Toggle hand layout",
                            tint = Color(0xFF00E5FF)
                        )
                    }

                    // Pass Button
                    if (state.canPass) {
                        Button(
                            onClick = onPassClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF1493), // Neon Pink
                                contentColor = Color(0xFF0B0C10)    // Dark Navy
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .neonGlow(Color(0xFFFF1493), radius = 6.dp)
                                .testTag("pass_button")
                        ) {
                            Text(
                                "PASS", 
                                fontSize = 12.sp, 
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Leave/Restart Button
                    IconButton(
                        onClick = onExitClick,
                        modifier = Modifier.size(36.dp).testTag("exit_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart match",
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Determine active hand to display
            val currentHand = if (state.isAiMode) {
                state.playerHand
            } else {
                if (state.isPlayerTurn) state.playerHand else state.opponentHand
            }

            // Player Hand Horizontal Row or Grid Columns
            PlayerHandRow(
                cards = currentHand,
                onCardClick = onCardClick,
                playableCheck = { card ->
                    // Helper logic to outline playable cards inside the hand
                    val activeCard = state.activeCard ?: return@PlayerHandRow true
                    if (state.activePenalty > 0) {
                        card.number == state.activePenaltyCardType
                    } else if (state.demandedSuit != null) {
                        card.suit == state.demandedSuit || card.isWild
                    } else {
                        card.suit == activeCard.suit || card.number == activeCard.number || card.isWild
                    }
                },
                isPlayerTurn = state.isPlayerTurn,
                viewAsColumns = viewAsColumns
            )
        }
    }
}

@Composable
fun OpponentStatusBar(
    cardCount: Int,
    isThinking: Boolean,
    isAiMode: Boolean,
    isPlayerTurn: Boolean
) {
    Surface(
        color = Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Status details
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAiMode) "AI" else if (isPlayerTurn) "P2" else "P1",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isAiMode) "Player 2 (AI)" else if (isPlayerTurn) "Player 2" else "Player 1",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isAiMode) {
                            if (isThinking) "Thinking..." else "Waiting"
                        } else {
                            if (isThinking) "Waiting for turn" else "Your turn"
                        },
                        color = if (isAiMode && isThinking) Color(0xFFFF9800) else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right: Hand count indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Miniature card icons matching count
                repeat(minOf(cardCount, 5)) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF003E19))
                            .border(0.5.dp, Color(0xFFFFD700), RoundedCornerShape(2.dp))
                    )
                }
                if (cardCount > 5) {
                    Text(
                        text = "+${cardCount - 5}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Numerical Counter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$cardCount cards",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GameOverDialog(
    playerWon: Boolean,
    isAiMode: Boolean,
    score: Int,
    onPlayAgain: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        val themeColor = if (isAiMode) {
            if (playerWon) Color(0xFF39FF14) else Color(0xFFFF1493)
        } else {
            Color(0xFF39FF14) // Always bright green for local multiplayer wins!
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 10.dp,
            color = Color(0xE61D0E0A),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .neonGlow(themeColor, radius = 12.dp)
                .border(1.5.dp, themeColor.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .testTag("game_over_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Trophy/Game Symbol
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = themeColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isAiMode) {
                        if (playerWon) "YOU WON!" else "GAME OVER"
                    } else {
                        if (playerWon) "PLAYER 1 WINS!" else "PLAYER 2 WINS!"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = themeColor,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isAiMode) {
                        if (playerWon) "Fantastic play! You cleared your hand." else "The AI system won this round."
                    } else {
                        if (playerWon) "Fantastic play! Player 1 cleared their hand." else "Fantastic play! Player 2 cleared their hand."
                    },
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Score metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B0C10).copy(alpha = 0.6f))
                        .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HAND PENALTY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = score.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (playerWon) Color(0xFF39FF14) else Color(0xFFFFD700)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Play Again Button
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF), // Cyber Neon Cyan
                        contentColor = Color(0xFF0B0C10)    // Dark Navy
                    ),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .neonGlow(Color(0xFF00E5FF), radius = 8.dp)
                        .testTag("play_again_button")
                ) {
                    Text(
                        text = "PLAY AGAIN",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
