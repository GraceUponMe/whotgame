package com.example.game

enum class WhotSuit {
    CIRCLE,
    TRIANGLE,
    CROSS,
    SQUARE,
    STAR,
    WHOT;

    fun getDisplayName(): String {
        return when (this) {
            CIRCLE -> "Circle"
            TRIANGLE -> "Triangle"
            CROSS -> "Cross"
            SQUARE -> "Square"
            STAR -> "Star"
            WHOT -> "Whot!"
        }
    }
}

data class WhotCard(
    val id: String,
    val suit: WhotSuit,
    val number: Int
) {
    // Whot! 20 cards are wild cards
    val isWild: Boolean get() = suit == WhotSuit.WHOT && number == 20

    // Get score value for end game
    val scoreValue: Int get() = when {
        isWild -> 20
        suit == WhotSuit.STAR -> number * 2 // Star cards are double points
        else -> number
    }

    // Check if card is a special action card
    val isSpecial: Boolean get() = number in listOf(1, 2, 5, 8, 14)
}
