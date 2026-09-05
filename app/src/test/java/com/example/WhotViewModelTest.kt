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
        val options = com.google.firebase.FirebaseOptions.Builder()
            .setApplicationId("1:847081931947:android:01a58ce6f2e8d1bf1ce3ee")
            .setApiKey("AIzaSyAMg02t7C33FYbqgy0VyuafHdSEKGSlsZE")
            .setProjectId("whot-ead8e")
            .build()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
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

    @Test
    fun testCreateOnlineRoom() = runTest(testDispatcher) {
        viewModel.createOnlineRoom()
        val state = viewModel.uiState.value
        assertEquals("waiting", state.roomStatus)
        assertNotNull(state.roomCode)
        assertEquals(6, state.roomCode?.length)
        assertTrue(state.isOnlineMode)
        assertTrue(state.isHost)

        // Leaving the room cleans up state
        viewModel.leaveOnlineRoom()
        val leftState = viewModel.uiState.value
        assertEquals("idle", leftState.roomStatus)
        assertNull(leftState.roomCode)
        assertFalse(leftState.isOnlineMode)
    }
}
