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
    val showPassDeviceOverlay: Boolean = false,
    val isOnlineMode: Boolean = false,
    val roomCode: String? = null,
    val isHost: Boolean = true,
    val roomStatus: String = "idle", // "idle", "creating", "waiting", "joining", "playing"
    val roomErrorMessage: String? = null
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

        // Determine next state
        val nextTurnMessage: String
        var nextIsPlayerTurn = !isPlayer

        // Establish hypothetical next hands to inspect if next player has moves
        val playerHandToUse = if (isPlayer) newHand else state.playerHand
        val opponentHandToUse = if (!isPlayer) newHand else state.opponentHand

        // Check special card effects (1 = Hold On, 8 = Suspension, 2 = Pick Two, 5 = Pick Three, 14 = General Market)
        val isExtraTurnCard = card.number == 1 || card.number == 8 || card.number == 2 || card.number == 5 || card.number == 14

        if (isExtraTurnCard) {
            nextIsPlayerTurn = isPlayer

            // Trigger immediate drawing for opponent on penalty cards (2, 5, 14)
            if (card.number == 2) {
                viewModelScope.launch {
                    repeat(2) { drawMarketCard(forPlayer = !isPlayer) }
                }
            } else if (card.number == 5) {
                viewModelScope.launch {
                    repeat(3) { drawMarketCard(forPlayer = !isPlayer) }
                }
            } else if (card.number == 14) {
                viewModelScope.launch {
                    drawMarketCard(forPlayer = !isPlayer)
                }
            }

            val pHand = newHand
            val hasPlayable = pHand.any { hCard -> 
                if (state.demandedSuit != null) {
                    hCard.suit == state.demandedSuit || hCard.isWild
                } else {
                    hCard.suit == card.suit || hCard.number == card.number || hCard.isWild
                }
            }

            val cardActionName = when (card.number) {
                1 -> "Hold On (1)"
                8 -> "Suspension (8)"
                2 -> "Pick Two (2)"
                5 -> "Pick Three (5)"
                14 -> "General Market (14)"
                else -> "Special Card"
            }

            val cardNameStr = "${card.suit.getDisplayName()} ${card.number}"
            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    when (card.number) {
                        2 -> if (hasPlayable) "You played Pick Two (2)! AI draws 2 cards. Play another card." else "You played Pick Two (2)! AI draws 2 cards. No matching card, Go Market."
                        5 -> if (hasPlayable) "You played Pick Three (5)! AI draws 3 cards. Play another card." else "You played Pick Three (5)! AI draws 3 cards. No matching card, Go Market."
                        14 -> if (hasPlayable) "You played General Market (14)! AI draws 1 card. Play another card." else "You played General Market (14)! AI draws 1 card. No matching card, Go Market."
                        else -> if (hasPlayable) "You played $cardActionName! Play another card." else "You played $cardActionName! No matching card, Go Market."
                    }
                } else {
                    when (card.number) {
                        2 -> "AI played Pick Two (2)! You draw 2 cards. AI gets another turn!"
                        5 -> "AI played Pick Three (5)! You draw 3 cards. AI gets another turn!"
                        14 -> "AI played General Market (14)! You draw 1 card. AI gets another turn!"
                        else -> "AI played $cardActionName and gets another turn!"
                    }
                }
            } else {
                val pName = if (isPlayer) "Player 1" else "Player 2"
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                when (card.number) {
                    2 -> if (hasPlayable) "Pick Two! $oppName draws 2 cards. $pName plays another card." else "Pick Two! $oppName draws 2 cards. No matching card, Go Market."
                    5 -> if (hasPlayable) "Pick Three! $oppName draws 3 cards. $pName plays another card." else "Pick Three! $oppName draws 3 cards. No matching card, Go Market."
                    14 -> if (hasPlayable) "General Market! $oppName draws 1 card. $pName plays another card." else "General Market! $oppName draws 1 card. No matching card, Go Market."
                    else -> if (hasPlayable) "$pName played $cardActionName! Play another card." else "$pName played $cardActionName! No matching card, Go Market."
                }
            }
        } else if (card.isWild) {
            val suitName = (demandedSuitOverride ?: state.demandedSuit)?.getDisplayName() ?: ""
            val nextHand = if (nextIsPlayerTurn) playerHandToUse else opponentHandToUse
            val demandSuit = demandedSuitOverride ?: state.demandedSuit
            val hasPlayable = nextHand.any { hCard -> demandSuit == null || hCard.suit == demandSuit || hCard.isWild }

            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    "You played Whot! 20 and requested $suitName. AI's turn."
                } else {
                    if (hasPlayable) "AI played Whot! 20 and requested $suitName. Your turn!" else "AI played Whot! 20 and requested $suitName. Your turn (Go Market)."
                }
            } else {
                val pName = if (isPlayer) "Player 1" else "Player 2"
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                if (hasPlayable) "$pName played Whot! 20 and requested $suitName. $oppName's turn." else "$pName played Whot! 20 and requested $suitName. $oppName's turn (Go Market)."
            }
        } else {
            val nextHand = if (nextIsPlayerTurn) playerHandToUse else opponentHandToUse
            val hasPlayable = nextHand.any { hCard -> 
                hCard.suit == card.suit || hCard.number == card.number || hCard.isWild
            }

            val cardNameStr = "${card.suit.getDisplayName()} ${card.number}"
            nextTurnMessage = if (state.isAiMode) {
                if (isPlayer) {
                    "You played $cardNameStr. AI's turn..."
                } else {
                    if (hasPlayable) "AI played $cardNameStr. Your turn!" else "AI played $cardNameStr. Your turn (No matching card, Go Market)."
                }
            } else {
                val pName = if (isPlayer) "Player 1" else "Player 2"
                val oppName = if (isPlayer) "Player 2" else "Player 1"
                if (hasPlayable) "$pName played $cardNameStr. $oppName's turn." else "$pName played $cardNameStr. $oppName's turn (Go Market)."
            }
        }

        // Update game state
        _uiState.update {
            val nextPassDevice = if (!it.isAiMode && nextIsPlayerTurn != isPlayer) true else it.showPassDeviceOverlay

            if (isPlayer) {
                it.copy(
                    playerHand = newHand,
                    discardPile = newDiscard,
                    activePenalty = 0,
                    activePenaltyCardType = null,
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
                    activePenalty = 0,
                    activePenaltyCardType = null,
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
            ensureDeckNotEmpty()
            if (deck.isNotEmpty()) {
                val card = deck.removeAt(0)
                val activeHand = if (state.isPlayerTurn) state.playerHand else state.opponentHand
                val updatedHand = activeHand + card

                val nextIsPlayerTurn = !state.isPlayerTurn
                val nextPassDevice = if (!state.isAiMode) true else state.showPassDeviceOverlay
                
                val cardName = "${card.suit.getDisplayName()} ${card.number}"
                val nextTurnMessage = if (state.isAiMode) {
                    "You drew $cardName. It is now AI's turn!"
                } else {
                    val pName = if (state.isPlayerTurn) "Player 1" else "Player 2"
                    val oppName = if (state.isPlayerTurn) "Player 2" else "Player 1"
                    "$pName drew $cardName. It is now $oppName's turn!"
                }

                _uiState.update {
                    if (state.isPlayerTurn) {
                        it.copy(
                            playerHand = updatedHand,
                            drawPileSize = deck.size,
                            isPlayerTurn = nextIsPlayerTurn,
                            hasDrawnThisTurn = false,
                            canPass = false,
                            turnMessage = nextTurnMessage,
                            showPassDeviceOverlay = nextPassDevice
                        )
                    } else {
                        it.copy(
                            opponentHand = updatedHand,
                            opponentHandSize = updatedHand.size,
                            drawPileSize = deck.size,
                            isPlayerTurn = nextIsPlayerTurn,
                            hasDrawnThisTurn = false,
                            canPass = false,
                            turnMessage = nextTurnMessage,
                            showPassDeviceOverlay = nextPassDevice
                        )
                    }
                }

                if (state.isAiMode && !nextIsPlayerTurn) {
                    triggerAiTurn()
                }
            } else {
                // Deck is empty! Allow passing
                val nextIsPlayerTurn = !state.isPlayerTurn
                val nextPassDevice = if (!state.isAiMode) true else state.showPassDeviceOverlay
                val nextTurnMessage = if (state.isAiMode) {
                    "Draw Pile is empty! Turn passed to AI."
                } else {
                    val pName = if (state.isPlayerTurn) "Player 1" else "Player 2"
                    val oppName = if (state.isPlayerTurn) "Player 2" else "Player 1"
                    "Draw Pile is empty! Turn passed to $oppName."
                }
                _uiState.update {
                    it.copy(
                        isPlayerTurn = nextIsPlayerTurn,
                        hasDrawnThisTurn = false,
                        canPass = false,
                        turnMessage = nextTurnMessage,
                        showPassDeviceOverlay = nextPassDevice
                    )
                }

                if (state.isAiMode && !nextIsPlayerTurn) {
                    triggerAiTurn()
                }
            }
        }
    }

    fun passTurn() {
        // Since drawCard now automatically ends the turn, passTurn is a fallback/no-op.
    }

    private fun triggerAiTurn() {
        viewModelScope.launch {
            _uiState.update { it.copy(turnMessage = "AI is thinking...") }
            delay(1800) // Realistic AI thinking delay so opponent sees AI actions clearly
            executeAiTurn()
        }
    }

    private suspend fun executeAiTurn() {
        val state = _uiState.value
        if (state.phase != GamePhase.PLAYING || state.isPlayerTurn || !state.isAiMode) return

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
            // AI has no playable card: AI goes to Market (draws a card)
            ensureDeckNotEmpty()
            if (deck.isNotEmpty()) {
                val card = deck.removeAt(0)
                val newHand = state.opponentHand + card

                // Strict Whot Rule: Going to Market ends turn immediately!
                // Player cannot play a card on the same turn after drawing.
                _uiState.update {
                    it.copy(
                        opponentHand = newHand,
                        opponentHandSize = newHand.size,
                        drawPileSize = deck.size,
                        isPlayerTurn = true, // Turn passes immediately to human player!
                        turnMessage = "AI went to Market and drew a card. Your turn!"
                    )
                }
            } else {
                // Deck is dry and AI cannot play, pass turn
                _uiState.update {
                    it.copy(
                        isPlayerTurn = true,
                        turnMessage = "Market is empty! AI passed. Your turn!"
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

    fun createOnlineRoom() {
        // Generate the 6-digit room code immediately so the user sees it without delay
        val code = (100000..999999).random().toString()
        _uiState.update {
            it.copy(
                roomCode = code,
                roomStatus = "waiting",
                roomErrorMessage = null,
                isOnlineMode = true,
                isHost = true,
                isAiMode = false,
                turnMessage = "Room created! Share code: $code"
            )
        }
        
        try {
            FirebaseGameService.createRoom(
                roomCode = code,
                onSuccess = {
                    listenToRoom(code)
                },
                onFailure = { e ->
                    android.util.Log.e("WhotViewModel", "Failed to sync room to Firebase: ${e.message}")
                    _uiState.update {
                        it.copy(
                            roomErrorMessage = "Offline or connecting: ${e.message}"
                        )
                    }
                    // Keep listening in case connectivity restores
                    listenToRoom(code)
                }
            )
        } catch (e: Throwable) {
            android.util.Log.e("WhotViewModel", "Exception creating room: ${e.message}")
            _uiState.update {
                it.copy(
                    roomErrorMessage = "Error creating room: ${e.message}"
                )
            }
        }
    }

    fun clearRoomError() {
        _uiState.update { it.copy(roomErrorMessage = null) }
    }

    fun joinOnlineRoom(code: String) {
        if (code.isBlank()) {
            _uiState.update { it.copy(roomErrorMessage = "Room code cannot be blank.") }
            return
        }
        _uiState.update {
            it.copy(
                roomStatus = "joining",
                roomErrorMessage = null,
                isOnlineMode = true,
                isHost = false,
                isAiMode = false
            )
        }
        FirebaseGameService.joinRoom(code,
            onSuccess = { room ->
                _uiState.update {
                    it.copy(
                        roomCode = code,
                        roomStatus = "playing",
                        turnMessage = "Joined room successfully! Game starting..."
                    )
                }
                listenToRoom(code)
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        roomStatus = "idle",
                        roomErrorMessage = "Failed to join room: ${e.message}",
                        isOnlineMode = false
                    )
                }
            }
        )
    }

    private fun listenToRoom(code: String) {
        FirebaseGameService.listenToRoom(code,
            onUpdate = { room ->
                handleRoomUpdate(room)
            },
            onError = { e ->
                _uiState.update {
                    it.copy(roomErrorMessage = e.message)
                }
            }
        )
    }

    private fun handleRoomUpdate(room: FirebaseGameRoom) {
        if (room.status == "waiting") {
            _uiState.update {
                it.copy(
                    roomCode = room.roomCode,
                    roomStatus = "waiting",
                    turnMessage = "Waiting for opponent... Share code: ${room.roomCode}"
                )
            }
            return
        }

        if (room.status == "playing") {
            val isHost = _uiState.value.isHost
            
            // Host initializes the deck and hands once guest joins
            if (isHost && room.hostHand.isEmpty() && room.deck.isEmpty() && !_uiState.value.gameStarted) {
                val rawDeck = createStandardDeck().shuffled().toMutableList()
                val hostHand = mutableListOf<WhotCard>()
                val guestHand = mutableListOf<WhotCard>()
                repeat(5) {
                    if (rawDeck.isNotEmpty()) hostHand.add(rawDeck.removeAt(0))
                    if (rawDeck.isNotEmpty()) guestHand.add(rawDeck.removeAt(0))
                }
                var initialCardIndex = -1
                for (i in rawDeck.indices) {
                    val card = rawDeck[i]
                    if (!card.isSpecial && !card.isWild) {
                        initialCardIndex = i
                        break
                    }
                }
                val topCard = if (initialCardIndex != -1) rawDeck.removeAt(initialCardIndex) else rawDeck.removeAt(0)
                
                val updatedRoom = room.copy(
                    hostHand = hostHand.map { it.toFirebaseCard() },
                    guestHand = guestHand.map { it.toFirebaseCard() },
                    deck = rawDeck.map { it.toFirebaseCard() },
                    discardPile = listOf(topCard.toFirebaseCard()),
                    isHostTurn = true,
                    turnMessage = "Game started! Your turn. Play a matching Card or Draw."
                )
                FirebaseGameService.updateRoomState(room.roomCode, updatedRoom)
                return
            }

            // If host has initialized or we are guest seeing playing state
            if (room.hostHand.isNotEmpty()) {
                val myHand = if (isHost) room.hostHand else room.guestHand
                val opponentHand = if (isHost) room.guestHand else room.hostHand
                val isMyTurn = if (isHost) room.isHostTurn else !room.isHostTurn

                val demandedSuitEnum = room.demandedSuit?.let { WhotSuit.valueOf(it) }

                _uiState.update {
                    it.copy(
                        phase = GamePhase.PLAYING,
                        gameStarted = true,
                        roomStatus = "playing",
                        playerHand = myHand.map { fc -> fc.toWhotCard() },
                        opponentHand = opponentHand.map { fc -> fc.toWhotCard() },
                        opponentHandSize = opponentHand.size,
                        discardPile = room.discardPile.map { fc -> fc.toWhotCard() },
                        drawPileSize = room.deck.size,
                        isPlayerTurn = isMyTurn,
                        demandedSuit = demandedSuitEnum,
                        activePenalty = room.activePenalty,
                        activePenaltyCardType = room.activePenaltyCardType,
                        turnMessage = if (isMyTurn) "Your turn! Play a matching Card or Draw." else "Opponent's turn. Waiting...",
                        showPassDeviceOverlay = false
                    )
                }
            }
        }

        if (room.status == "finished") {
            val isWinner = room.winnerId == FirebaseGameService.myPlayerId
            _uiState.update {
                it.copy(
                    phase = GamePhase.GAME_OVER,
                    playerWon = isWinner,
                    showConfetti = isWinner,
                    lastScore = 0,
                    turnMessage = if (isWinner) "Victory! You won the game!" else "Defeat! Opponent won the game."
                )
            }
        }
    }

    fun playCardOnline(card: WhotCard) {
        val state = _uiState.value
        if (!state.isPlayerTurn || state.phase != GamePhase.PLAYING) return
        
        // Use existing validation logic
        if (!isCardPlayable(card)) {
            _uiState.update { it.copy(turnMessage = "Invalid card! Must match suit, number, or be a Whot! (20).") }
            return
        }

        if (card.isWild) {
            _uiState.update {
                it.copy(
                    showSuitSelection = true,
                    pendingWhotCard = card
                )
            }
        } else {
            executePlayOnline(card)
        }
    }

    fun selectDemandedSuitOnline(suit: WhotSuit) {
        val state = _uiState.value
        val whotCard = state.pendingWhotCard ?: return
        
        _uiState.update {
            it.copy(
                showSuitSelection = false,
                pendingWhotCard = null
            )
        }
        executePlayOnline(whotCard, suit)
    }

    private fun executePlayOnline(card: WhotCard, demandedSuitOverride: WhotSuit? = null) {
        val state = _uiState.value
        val roomCode = state.roomCode ?: return
        
        val newHand = state.playerHand.filter { it.id != card.id }
        val newDiscard = state.discardPile + card

        val isExtraTurn = card.number == 1 || card.number == 8 || card.number == 2 || card.number == 5 || card.number == 14
        
        FirebaseGameService.getRoomOnce(roomCode, onSuccess = { currentRoom ->
            val deckMutable = currentRoom.deck.toMutableList()
            val oppHandMutable = (if (state.isHost) currentRoom.guestHand else currentRoom.hostHand).toMutableList()
            
            if (card.number == 2) {
                repeat(2) {
                    if (deckMutable.isNotEmpty()) oppHandMutable.add(deckMutable.removeAt(0))
                }
            } else if (card.number == 5) {
                repeat(3) {
                    if (deckMutable.isNotEmpty()) oppHandMutable.add(deckMutable.removeAt(0))
                }
            } else if (card.number == 14) {
                if (deckMutable.isNotEmpty()) oppHandMutable.add(deckMutable.removeAt(0))
            }

            val nextIsHostTurn = if (state.isHost) {
                isExtraTurn
            } else {
                !isExtraTurn
            }

            val hasWon = newHand.isEmpty()
            val gameStatus = if (hasWon) "finished" else "playing"
            val winnerId = if (hasWon) FirebaseGameService.myPlayerId else null

            val updatedRoom = FirebaseGameRoom(
                roomCode = roomCode,
                hostId = currentRoom.hostId,
                guestId = currentRoom.guestId,
                status = gameStatus,
                hostHand = if (state.isHost) newHand.map { it.toFirebaseCard() } else oppHandMutable,
                guestHand = if (state.isHost) oppHandMutable else newHand.map { it.toFirebaseCard() },
                deck = deckMutable,
                discardPile = newDiscard.map { it.toFirebaseCard() },
                isHostTurn = nextIsHostTurn,
                demandedSuit = demandedSuitOverride?.name ?: if (!card.isWild) null else currentRoom.demandedSuit,
                winnerId = winnerId,
                turnMessage = if (hasWon) "Game Finished!" else "Card played!"
            )

            FirebaseGameService.updateRoomState(roomCode, updatedRoom)
        }, onFailure = {})
    }

    fun drawCardOnline() {
        val state = _uiState.value
        val roomCode = state.roomCode ?: return
        if (!state.isPlayerTurn || state.phase != GamePhase.PLAYING) return

        FirebaseGameService.getRoomOnce(roomCode, onSuccess = { currentRoom ->
            val deckMutable = currentRoom.deck.toMutableList()
            val myHandMutable = (if (state.isHost) currentRoom.hostHand else currentRoom.guestHand).toMutableList()
            
            if (deckMutable.isNotEmpty()) {
                val drawn = deckMutable.removeAt(0)
                myHandMutable.add(drawn)
            }
            
            val nextIsHostTurn = if (state.isHost) false else true

            val updatedRoom = currentRoom.copy(
                hostHand = if (state.isHost) myHandMutable else currentRoom.hostHand,
                guestHand = if (state.isHost) currentRoom.guestHand else myHandMutable,
                deck = deckMutable,
                isHostTurn = nextIsHostTurn,
                turnMessage = "Card drawn!"
            )

            FirebaseGameService.updateRoomState(roomCode, updatedRoom)
        }, onFailure = {})
    }

    fun leaveOnlineRoom() {
        val state = _uiState.value
        val roomCode = state.roomCode
        if (roomCode != null) {
            if (state.isHost) {
                FirebaseGameService.deleteRoom(roomCode)
            } else {
                FirebaseGameService.stopListening()
            }
        }
        _uiState.update {
            it.copy(
                phase = GamePhase.START_SCREEN,
                gameStarted = false,
                isOnlineMode = false,
                roomCode = null,
                roomStatus = "idle",
                roomErrorMessage = null,
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
}
