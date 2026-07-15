package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameStatsRepository
import com.example.game.WhotViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhotViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var repository: GameStatsRepository
    private lateinit var viewModel: WhotViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GameStatsRepository(database.gameStatsDao())
        viewModel = WhotViewModel(repository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialGameModeIsVsComputer() = runTest(testDispatcher) {
        // Initially, isAiMode defaults to true (vs Computer)
        assertTrue(viewModel.uiState.value.isAiMode)
        assertFalse(viewModel.uiState.value.showPassDeviceOverlay)
    }

    @Test
    fun testToggleGameModeToPassAndPlay() = runTest(testDispatcher) {
        // Toggle to Pass & Play (isAi = false)
        viewModel.toggleGameMode(isAi = false)
        
        // Assert mode changed and showPassDeviceOverlay is triggered initially
        assertFalse(viewModel.uiState.value.isAiMode)
        assertTrue(viewModel.uiState.value.showPassDeviceOverlay)
    }

    @Test
    fun testRevealHandHidesOverlay() = runTest(testDispatcher) {
        viewModel.toggleGameMode(isAi = false)
        assertTrue(viewModel.uiState.value.showPassDeviceOverlay)

        viewModel.revealHand()
        assertFalse(viewModel.uiState.value.showPassDeviceOverlay)
    }
}
