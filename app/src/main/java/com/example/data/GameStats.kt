package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_stats")
data class GameStats(
    @PrimaryKey val id: Int = 1,
    val wins: Int = 0,
    val losses: Int = 0,
    val gamesPlayed: Int = 0,
    val bestScore: Int = 999 // In Whot!, lower score at end of game is better
)
