package com.example.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameStats
import com.example.data.GameStatsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class GamePhase {
    START_SCREEN,
    PLAYING,
    GAME_OVER
}

data class GameUiState(
    val phase: GamePhase = GamePhase.START_SCREEN,
    val gameStarted: Boolean = false,
    val playerHand: List<WhotCard> = emptyList(),
    val opponentHandSize: Int = 0,
    val opponentHand: List<WhotCard> = emptyList(), // Hidden in normal gameplay
    val drawPileSize: Int = 0,
    val discardPile: List<WhotCard> = emptyList(),
    val isPlayerTurn: Boolean = true,
    val demandedSuit: WhotSuit? = null,
    val activePenalty: Int = 0,
    val activePenaltyCardType: Int? = null, // Can be 2 or 5
    val hasDrawnThisTurn: Boolean = false,
    val canPass: Boolean = false,
    val showSuitSelection: Boolean = false,
    val turnMessage: String = "Welcome to Whot! Tap Deal to start.",
    val showConfetti: Boolean = false,
    val playerWon: Boolean = false,
    val lastScore: Int = 0,
    val pendingWhotCard: WhotCard? = null, // Store the Whot card played that is waiting for suit selection
    val isAiMode: Boolean = true,
    val showPassDeviceOverlay: Boolean = false
) {
    val activeCard: WhotCard? get() = discardPile.lastOrNull()
}

class WhotViewModel(private val repository: GameStatsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Expose statistics reactively from local database
    val gameStats: StateFlow<GameStats?> = repository.stats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private var deck: MutableList<WhotCard> = mutableListOf()

    fun toggleGameMode(isAi: Boolean) {
        _uiState.update {
            it.copy(
                isAiMode = isAi,
                showPassDeviceOverlay = !isAi
            )
        }
    }

    fun revealHand() {
        _uiState.update {
            it.copy(showPassDeviceOverlay = false)
        }
    }

    fun quitToMenu() {
        _uiState.update {
            it.copy(
                gameStarted = false,
                phase = GamePhase.START_SCREEN,
                playerHand = emptyList(),
                opponentHand = emptyList(),
                opponentHandSize = 0,
                discardPile = emptyList(),
                demandedSuit = null,
                activePenalty = 0,
                activePenaltyCardType = null,
                pendingWhotCard = null
            )
        }
    }

    fun startNewGame() {
        viewModelScope.launch {
            // Generate and shuffle the deck
            val rawDeck = createStandardDeck().shuffled().toMutableList()

            // Deal exactly 5 cards to player and AI / Player 2
            val pHand = mutableListOf<WhotCard>()
            val oHand = mutableListOf<WhotCard>()
            repeat(5) {
                if (rawDeck.isNotEmpty()) pHand.add(rawDeck.removeAt(0))
                if (rawDeck.isNotEmpty()) oHand.add(rawDeck.removeAt(0))
            }

            // Find a valid initial face-up card (must not be a special or wild card)
            var initialCardIndex = -1
            for (i in rawDeck.indices) {
                val card = rawDeck[i]
                if (!card.isSpecial && !card.isWild) {
                    initialCardIndex = i
                    break
                }
            }

            // Fallback if somehow all cards are special, just pick index 0
            val topCard = if (initialCardIndex != -1) {
                rawDeck.removeAt(initialCardIndex)
            } else {
                rawDeck.removeAt(0)
            }

            deck = rawDeck

            _uiState.update {
                it.copy(
                    phase = GamePhase.PLAYING,
                    gameStarted = true,
                    playerHand = pHand,
                    opponentHand = oHand,
                    opponentHandSize = oHand.size,
                    drawPileSize = deck.size,
                    discardPile = listOf(topCard),
                    isPlayerTurn = true,
                    demandedSuit = null,
                    activePenalty = 0,
                    activePenaltyCardType = null,
                    hasDrawnThisTurn = false,
                    canPass = false,
                    showSuitSelection = false,
                    turnMessage = if (it.isAiMode) "Match started! Your turn. Play a matching Card or Draw." else "Match started! Player 1's turn. Play a matching Card or Draw.",
                    showConfetti = false,
                    playerWon = false,
                    pendingWhotCard = null,
                    showPassDeviceOverlay = !it.isAiMode
                )
            }
        }
    }

    fun playCard(card: WhotCard) {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING) return
        if (state.isAiMode && !state.isPlayerTurn) return
        if (!state.isAiMode && state.showPassDeviceOverlay) return

        // 1. Validate if playable
        if (!isCardPlayable(card)) {
            _uiState.update { it.copy(turnMessage = "Invalid card! Must match suit, number, or be a Whot! (20).") }
            return
        }

        // 2. Play the card
        if (card.isWild) {
            // Pause turn for suit selection
            _uiState.update {
                it.copy(
                    showSuitSelection = true,
                    pendingWhotCard = card
                )
            }
        } else {
            executePlay(card, isPlayer = state.isPlayerTurn)
        }
    }

    fun selectDemandedSuit(suit: WhotSuit) {
        val state = _uiState.value
        val whotCard = state.pendingWhotCard ?: return

        _uiState.update {
            it.copy(
                showSuitSelection = false,
                demandedSuit = suit,
                pendingWhotCard = null
            )
        }

        executePlay(whotCard, isPlayer = state.isPlayerTurn, demandedSuitOverride = suit)
    }

    private fun executePlay(card: WhotCard, isPlayer: Boolean, demandedSuitOverride: WhotSuit? = null) {
        val state = _uiState.value
        val currentHand = if (isPlayer) state.playerHand else state.opponentHand
        val newHand = currentHand.filter { it.id != card.id }

        // Update piles
        val newDiscard = state.discardPile + card

        // Calculate card penalty if any
        var newPenalty = state.activePenalty
        var penaltyType = state.activePenaltyCardType

        if (card.number == 2) {
            newPenalty += 2
            penaltyType = 2
        }

        // Determine next state
        val nextTurnMessage: String
        var nextIsPlayerTurn = !isPlayer

        // Establish hypothetical next hands to inspect if next player has moves
        val playerHandToUse = if (isPlayer) newHand else state.playerHand
        val opponentHandToUse = if (!isPlayer) newHand else state.opponentHand

        // Check special card effects (1 = Hold On, 8 = Suspension)
        val isHoldOnOrSuspension = card.number == 1 || card.number == 8
        if (isHoldOnOrSuspension) {
            // Next turn stays with the same player
            nextIsPlayerTurn = isPlayer
            
            val pHand = newHand
            val hasPlayable = pHand.any { hCard -> 
                if (newPenalty > 0) {
                    hCard.number == penaltyType
                } else if (state.demandedSuit != null) {
                    hCard.suit == state.demandedSuit || hCard.isWild
                } else {
                    hCard.suit == card.suit || hCard.number == card.number || hCard.isWild
                }
            }

            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    if (hasPlayable) "You played Hold On (1)! Play another matching card." else "You played Hold On (1)! No playable card, Go Market."
                } else {
                    "AI played Hold On (1) and gets another turn!"
                }
            } else {
                val pName = if (isPlayer) "Player 1" else "Player 2"
                if (hasPlayable) "$pName played Hold On (1)! Play another matching card." else "$pName played Hold On (1)! No playable card, Go Market."
            }
        } else if (card.number == 14) {
            // General Market: opponent immediately draws 1 card
            viewModelScope.launch {
                drawMarketCard(forPlayer = !isPlayer)
            }
            
            val nextPlayerName = if (isPlayer) "Player 2" else "Player 1"
            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    "General Market! AI draws 1 card. AI's turn."
                } else {
                    "AI played General Market! You draw 1 card. Your turn."
                }
            } else {
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                "General Market! $oppName draws 1 card. It is now $oppName's turn."
            }
            nextIsPlayerTurn = !isPlayer
        } else if (card.isWild) {
            val suitName = (demandedSuitOverride ?: state.demandedSuit)?.getDisplayName() ?: ""
            val nextHand = if (nextIsPlayerTurn) playerHandToUse else opponentHandToUse
            val demandSuit = demandedSuitOverride ?: state.demandedSuit
            val hasPlayable = nextHand.any { hCard -> demandSuit == null || hCard.suit == demandSuit || hCard.isWild }

            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    "You played Whot! 20 and requested: $suitName. AI's turn."
                } else {
                    if (hasPlayable) "AI played Whot! 20 and requested: $suitName. Your turn." else "AI played Whot! 20 and requested: $suitName. Your turn (Go Market)."
                }
            } else {
                val pName = if (isPlayer) "Player 1" else "Player 2"
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                if (hasPlayable) "$pName played Whot! 20 and requested: $suitName. $oppName's turn." else "$pName played Whot! 20 and requested: $suitName. $oppName's turn (Go Market)."
            }
        } else {
            val nextHand = if (nextIsPlayerTurn) playerHandToUse else opponentHandToUse
            val hasPlayable = nextHand.any { hCard -> 
                if (newPenalty > 0) {
                    hCard.number == penaltyType
                } else {
                    hCard.suit == card.suit || hCard.number == card.number || hCard.isWild
                }
            }

            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    "AI's turn."
                } else {
                    if (hasPlayable) "Your turn." else "Your turn (No playable card, Go Market)."
                }
            } else {
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                if (hasPlayable) "$oppName's turn." else "$oppName's turn (No playable card, Go Market)."
            }
        }

        // Update game state
        _uiState.update {
            val nextPassDevice = if (!it.isAiMode && nextIsPlayerTurn != isPlayer) true else it.showPassDeviceOverlay

            if (isPlayer) {
                it.copy(
                    playerHand = newHand,
                    discardPile = newDiscard,
                    activePenalty = newPenalty,
                    activePenaltyCardType = penaltyType,
                    demandedSuit = demandedSuitOverride ?: if (!card.isWild) null else it.demandedSuit,
                    isPlayerTurn = nextIsPlayerTurn,
                    hasDrawnThisTurn = false,
                    canPass = false,
                    turnMessage = nextTurnMessage,
                    showPassDeviceOverlay = nextPassDevice
                )
            } else {
                it.copy(
                    opponentHand = newHand,
                    opponentHandSize = newHand.size,
                    discardPile = newDiscard,
                    activePenalty = newPenalty,
                    activePenaltyCardType = penaltyType,
                    demandedSuit = demandedSuitOverride ?: if (!card.isWild) null else it.demandedSuit,
                    isPlayerTurn = nextIsPlayerTurn,
                    hasDrawnThisTurn = false,
                    canPass = false,
                    turnMessage = nextTurnMessage,
                    showPassDeviceOverlay = nextPassDevice
                )
            }
        }

        // Check win condition
        if (newHand.isEmpty()) {
            endGame(playerWon = isPlayer)
        } else {
            // Trigger AI if it's AI's turn
            if (state.isAiMode && !nextIsPlayerTurn) {
                triggerAiTurn()
            }
        }
    }

    private suspend fun drawMarketCard(forPlayer: Boolean) {
        ensureDeckNotEmpty()
        if (deck.isNotEmpty()) {
            val card = deck.removeAt(0)
            _uiState.update {
                if (forPlayer) {
                    it.copy(
                        playerHand = it.playerHand + card,
                        drawPileSize = deck.size
                    )
                } else {
                    val newOpponentHand = it.opponentHand + card
                    it.copy(
                        opponentHand = newOpponentHand,
                        opponentHandSize = newOpponentHand.size,
                        drawPileSize = deck.size
                    )
                }
            }
        }
    }

    fun drawCard() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING) return
        if (state.isAiMode && !state.isPlayerTurn) return
        if (!state.isAiMode && state.showPassDeviceOverlay) return

        viewModelScope.launch {
            if (state.activePenalty > 0) {
                // Draw multiple cards due to penalty
                val drawnCards = mutableListOf<WhotCard>()
                repeat(state.activePenalty) {
                    ensureDeckNotEmpty()
                    if (deck.isNotEmpty()) {
                        drawnCards.add(deck.removeAt(0))
                    }
                }

                _uiState.update {
                    val nextIsPlayerTurn = !state.isPlayerTurn
                    val nextPassDevice = if (!it.isAiMode) true else it.showPassDeviceOverlay
                    val nextTurnMessage = if (it.isAiMode) {
                        "Accepted penalty of ${drawnCards.size} cards! AI's turn."
                    } else {
                        val receiverName = if (state.isPlayerTurn) "Player 1" else "Player 2"
                        val nextPlayerName = if (state.isPlayerTurn) "Player 2" else "Player 1"
                        "$receiverName accepted penalty of ${drawnCards.size} cards! It's $nextPlayerName's turn."
                    }

                    if (state.isPlayerTurn) {
                        it.copy(
                            playerHand = it.playerHand + drawnCards,
                            drawPileSize = deck.size,
                            activePenalty = 0,
                            activePenaltyCardType = null,
                            isPlayerTurn = nextIsPlayerTurn, // Penalty accepting skips player's turn
                            hasDrawnThisTurn = false,
                            canPass = false,
                            turnMessage = nextTurnMessage,
                            showPassDeviceOverlay = nextPassDevice
                        )
                    } else {
                        val newOpponentHand = it.opponentHand + drawnCards
                        it.copy(
                            opponentHand = newOpponentHand,
                            opponentHandSize = newOpponentHand.size,
                            drawPileSize = deck.size,
                            activePenalty = 0,
                            activePenaltyCardType = null,
                            isPlayerTurn = nextIsPlayerTurn, // Penalty accepting skips player's turn
                            hasDrawnThisTurn = false,
                            canPass = false,
                            turnMessage = nextTurnMessage,
                            showPassDeviceOverlay = nextPassDevice
                        )
                    }
                }
                if (state.isAiMode) {
                    triggerAiTurn()
                }
            } else {
                // Normal draw: allowed to draw as many times as desired
                ensureDeckNotEmpty()
                if (deck.isNotEmpty()) {
                    val card = deck.removeAt(0)
                    val activeHand = if (state.isPlayerTurn) state.playerHand else state.opponentHand
                    val updatedHand = activeHand + card

                    // Check if drawn card is playable
                    val hasPlayableCard = updatedHand.any { isCardPlayable(it) }

                    _uiState.update {
                        val cardName = "${card.suit.getDisplayName()} ${card.number}"
                        val turnMessageStr = if (hasPlayableCard) {
                            "You drew: $cardName. You can play it, pass, or draw again!"
                        } else {
                            "You drew: $cardName. Tap Pass, or draw again!"
                        }

                        if (state.isPlayerTurn) {
                            it.copy(
                                playerHand = updatedHand,
                                drawPileSize = deck.size,
                                hasDrawnThisTurn = true,
                                canPass = true, // Allowed to pass after drawing at least once!
                                turnMessage = turnMessageStr
                            )
                        } else {
                            it.copy(
                                opponentHand = updatedHand,
                                opponentHandSize = updatedHand.size,
                                drawPileSize = deck.size,
                                hasDrawnThisTurn = true,
                                canPass = true, // Allowed to pass after drawing at least once!
                                turnMessage = turnMessageStr
                            )
                        }
                    }
                }
            }
        }
    }

    fun passTurn() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING) return
        if (state.isAiMode && !state.isPlayerTurn) return
        if (!state.isAiMode && (state.showPassDeviceOverlay || !state.canPass)) return

        _uiState.update {
            val nextIsPlayerTurn = !state.isPlayerTurn
            val nextPassDevice = if (!it.isAiMode) true else it.showPassDeviceOverlay
            val nextTurnMessage = if (it.isAiMode) {
                "You passed. AI is thinking..."
            } else {
                val pName = if (state.isPlayerTurn) "Player 1" else "Player 2"
                val oppName = if (state.isPlayerTurn) "Player 2" else "Player 1"
                "$pName passed. $oppName's turn."
            }

            it.copy(
                isPlayerTurn = nextIsPlayerTurn,
                hasDrawnThisTurn = false,
                canPass = false,
                turnMessage = nextTurnMessage,
                showPassDeviceOverlay = nextPassDevice
            )
        }
        if (state.isAiMode) {
            triggerAiTurn()
        }
    }

    private fun triggerAiTurn() {
        viewModelScope.launch {
            delay(1200) // Realistic AI thinking delay
            executeAiTurn()
        }
    }

    private suspend fun executeAiTurn() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || state.isPlayerTurn) return

        // 1. AI is under penalty
        if (state.activePenalty > 0) {
            val targetNumber = state.activePenaltyCardType
            val defenderCard = state.opponentHand.firstOrNull { it.number == targetNumber }

            if (defenderCard != null) {
                // AI can defend by stacking!
                executePlay(defenderCard, isPlayer = false)
            } else {
                // AI must accept penalty
                val drawnCards = mutableListOf<WhotCard>()
                repeat(state.activePenalty) {
                    ensureDeckNotEmpty()
                    if (deck.isNotEmpty()) {
                        drawnCards.add(deck.removeAt(0))
                    }
                }

                val newHand = state.opponentHand + drawnCards
                _uiState.update {
                    it.copy(
                        opponentHand = newHand,
                        opponentHandSize = newHand.size,
                        drawPileSize = deck.size,
                        activePenalty = 0,
                        activePenaltyCardType = null,
                        isPlayerTurn = true, // Play moves to player
                        turnMessage = "AI couldn't defend and drew ${drawnCards.size} cards! Your turn."
                    )
                }
            }
            return
        }

        // 2. Normal AI turn - scan hand for playable cards
        val playableCards = state.opponentHand.filter { isCardPlayable(it) }

        if (playableCards.isNotEmpty()) {
            // Smart AI choice: prioritize playing action cards or non-wilds first
            val selectedCard = playableCards.firstOrNull { !it.isWild } ?: playableCards.first()

            if (selectedCard.isWild) {
                // AI selects its most common suit in hand
                val nonWildSuits = state.opponentHand
                    .filter { !it.isWild }
                    .groupBy { it.suit }
                    .maxByOrNull { it.value.size }?.key ?: WhotSuit.CIRCLE

                _uiState.update { it.copy(demandedSuit = nonWildSuits) }
                executePlay(selectedCard, isPlayer = false, demandedSuitOverride = nonWildSuits)
            } else {
                executePlay(selectedCard, isPlayer = false)
            }
        } else {
            // AI must draw
            ensureDeckNotEmpty()
            if (deck.isNotEmpty()) {
                val card = deck.removeAt(0)
                val newHand = state.opponentHand + card

                _uiState.update {
                    it.copy(
                        opponentHand = newHand,
                        opponentHandSize = newHand.size,
                        drawPileSize = deck.size,
                        turnMessage = "AI drew a card."
                    )
                }

                // Can AI play the drawn card immediately?
                delay(800)
                if (isCardPlayable(card)) {
                    if (card.isWild) {
                        val nonWildSuits = newHand
                            .filter { !it.isWild }
                            .groupBy { it.suit }
                            .maxByOrNull { it.value.size }?.key ?: WhotSuit.CIRCLE

                        _uiState.update { it.copy(demandedSuit = nonWildSuits) }
                        executePlay(card, isPlayer = false, demandedSuitOverride = nonWildSuits)
                    } else {
                        executePlay(card, isPlayer = false)
                    }
                } else {
                    // AI passes
                    _uiState.update {
                        it.copy(
                            isPlayerTurn = true,
                            turnMessage = "AI passed. Your turn!"
                        )
                    }
                }
            } else {
                // Deck is dry and AI cannot play, pass turn
                _uiState.update {
                    it.copy(
                        isPlayerTurn = true,
                        turnMessage = "AI passed. Your turn!"
                    )
                }
            }
        }
    }

    private fun isCardPlayable(card: WhotCard): Boolean {
        val state = _uiState.value
        val activeCard = state.activeCard ?: return true

        // Whot! wild card can always be played (unless defending a penalty with a specific 2 or 5)
        if (state.activePenalty > 0) {
            // If penalty is active, you must match the penalty number to defend
            return card.number == state.activePenaltyCardType
        }

        if (card.isWild) return true

        // If Whot requested a suit
        if (state.demandedSuit != null) {
            return card.suit == state.demandedSuit
        }

        // Normal play
        return card.suit == activeCard.suit || card.number == activeCard.number
    }

    private fun ensureDeckNotEmpty() {
        if (deck.isEmpty()) {
            val state = _uiState.value
            // Reshuffle discard pile back into deck, leaving the top card
            val activeCard = state.activeCard
            val previousDiscards = state.discardPile.filter { it.id != activeCard?.id }

            if (previousDiscards.isNotEmpty()) {
                deck.addAll(previousDiscards.shuffled())
                _uiState.update {
                    it.copy(
                        discardPile = if (activeCard != null) listOf(activeCard) else emptyList(),
                        drawPileSize = deck.size,
                        turnMessage = "Reshuffled discard pile back into Draw Pile!"
                    )
                }
            }
        }
    }

    private fun endGame(playerWon: Boolean) {
        val state = _uiState.value
        val score = if (playerWon) {
            // Winner gets 0, score is opponent's hand penalty
            state.opponentHand.sumOf { it.scoreValue }
        } else {
            // Player lost, score is their hand penalty
            state.playerHand.sumOf { it.scoreValue }
        }

        _uiState.update {
            it.copy(
                phase = GamePhase.GAME_OVER,
                playerWon = playerWon,
                lastScore = score,
                showConfetti = if (it.isAiMode) playerWon else true, // Always show confetti in local 2-player mode!
                turnMessage = if (it.isAiMode) {
                    if (playerWon) "Congratulations! You won the match! Score: $score" else "AI won this match. Score: $score"
                } else {
                    if (playerWon) "Player 1 won the match! Score: $score" else "Player 2 won the match! Score: $score"
                }
            )
        }

        // Save statistics to database
        viewModelScope.launch {
            val currentStats = gameStats.value ?: GameStats()
            val newWins = if (playerWon) currentStats.wins + 1 else currentStats.wins
            val newLosses = if (!playerWon) currentStats.losses + 1 else currentStats.losses
            val newGamesPlayed = currentStats.gamesPlayed + 1
            // In Whot!, a lower hand score at end of game is better.
            // If player won, score is 0. If they lost, it's their hand total.
            val finalScoreToRecord = if (playerWon) 0 else score
            val newBestScore = if (finalScoreToRecord < currentStats.bestScore) finalScoreToRecord else currentStats.bestScore

            val updatedStats = GameStats(
                id = 1,
                wins = newWins,
                losses = newLosses,
                gamesPlayed = newGamesPlayed,
                bestScore = newBestScore
            )
            repository.saveStats(updatedStats)
        }
    }

    fun resetStats() {
        viewModelScope.launch {
            repository.saveStats(GameStats())
        }
    }

    private fun createStandardDeck(): List<WhotCard> {
        val cards = mutableListOf<WhotCard>()
        var idCounter = 1

        // Circle: 1-14 except 6, 9
        val circleNumbers = listOf(1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14)
        for (num in circleNumbers) {
            cards.add(WhotCard("C_${idCounter++}_$num", WhotSuit.CIRCLE, num))
        }

        // Triangle: 1-14 except 6, 9
        val triangleNumbers = listOf(1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14)
        for (num in triangleNumbers) {
            cards.add(WhotCard("T_${idCounter++}_$num", WhotSuit.TRIANGLE, num))
        }

        // Cross: 1, 2, 3, 5, 7, 10, 11, 13, 14
        val crossNumbers = listOf(1, 2, 3, 5, 7, 10, 11, 13, 14)
        for (num in crossNumbers) {
            cards.add(WhotCard("Cr_${idCounter++}_$num", WhotSuit.CROSS, num))
        }

        // Square: 1, 2, 3, 5, 7, 10, 11, 13, 14
        val squareNumbers = listOf(1, 2, 3, 5, 7, 10, 11, 13, 14)
        for (num in squareNumbers) {
            cards.add(WhotCard("S_${idCounter++}_$num", WhotSuit.SQUARE, num))
        }

        // Star: 1, 2, 3, 4, 5, 7, 8
        val starNumbers = listOf(1, 2, 3, 4, 5, 7, 8)
        for (num in starNumbers) {
            cards.add(WhotCard("St_${idCounter++}_$num", WhotSuit.STAR, num))
        }

        // Whot! (20) - 5 wild cards
        repeat(5) {
            cards.add(WhotCard("W_${idCounter++}_20", WhotSuit.WHOT, 20))
        }

        return cards
    }
}
