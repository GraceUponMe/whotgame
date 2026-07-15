package com.example.data

import kotlinx.coroutines.flow.Flow

class GameStatsRepository(private val gameStatsDao: GameStatsDao) {
    val stats: Flow<GameStats?> = gameStatsDao.getStats()

    suspend fun saveStats(stats: GameStats) {
        gameStatsDao.insertStats(stats)
    }
}
