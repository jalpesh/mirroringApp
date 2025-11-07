package com.example.mirroringapp

import android.content.Intent
import app.cash.turbine.test
import com.example.mirroringapp.data.repository.SettingsRepository
import com.example.mirroringapp.domain.usecase.GetProjectionIntentUseCase
import com.example.mirroringapp.domain.usecase.StartMirroringUseCase
import com.example.mirroringapp.domain.usecase.StopMirroringUseCase
import com.example.mirroringapp.mirroring.ConnectionOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class MirroringViewModelTest {

    private lateinit var viewModel: MirroringViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var startMirroringUseCase: StartMirroringUseCase
    private lateinit var stopMirroringUseCase: StopMirroringUseCase
    private lateinit var getProjectionIntentUseCase: GetProjectionIntentUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        settingsRepository = mockk(relaxed = true)
        startMirroringUseCase = mockk(relaxed = true)
        stopMirroringUseCase = mockk(relaxed = true)
        getProjectionIntentUseCase = mockk(relaxed = true)

        viewModel = MirroringViewModel(
            settingsRepository,
            startMirroringUseCase,
            stopMirroringUseCase,
            getProjectionIntentUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize loads settings and updates state to Idle`() = runTest {
        // Given
        coEvery { settingsRepository.initialize() } returns Unit
        coEvery { settingsRepository.getConnectionOption() } returns ConnectionOption.USB_C
        coEvery { settingsRepository.isLowLatencyEnabled() } returns true
        coEvery { settingsRepository.isHardwareEncoderEnabled() } returns true

        // When
        viewModel.initialize()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Idle::class.java)
            assertThat(state.connectionOption).isEqualTo(ConnectionOption.USB_C)
            assertThat(state.lowLatencyEnabled).isTrue()
            assertThat(state.hardwareEncoderEnabled).isTrue()
        }
    }

    @Test
    fun `setConnectionOption updates repository and state`() = runTest {
        // Given
        val newOption = ConnectionOption.WIFI_DIRECT
        coEvery { settingsRepository.setConnectionOption(newOption) } returns Unit

        // When
        viewModel.setConnectionOption(newOption)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { settingsRepository.setConnectionOption(newOption) }
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.connectionOption).isEqualTo(newOption)
        }
    }

    @Test
    fun `startMirroring success transitions to Mirroring state`() = runTest {
        // Given
        val resultCode = 1
        val intent = mockk<Intent>()
        coEvery { startMirroringUseCase(resultCode, intent) } returns Result.success(Unit)

        // When
        viewModel.startMirroring(resultCode, intent)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Mirroring::class.java)
        }
    }

    @Test
    fun `startMirroring failure transitions to Error state`() = runTest {
        // Given
        val resultCode = 1
        val intent = mockk<Intent>()
        val exception = Exception("Test error")
        coEvery { startMirroringUseCase(resultCode, intent) } returns Result.failure(exception)

        // When
        viewModel.startMirroring(resultCode, intent)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Error::class.java)
            assertThat((state as MirroringUiState.Error).message).contains("Test error")
        }
    }

    @Test
    fun `stopMirroring transitions to Idle state`() = runTest {
        // Given
        coEvery { stopMirroringUseCase() } returns Result.success(Unit)

        // When
        viewModel.stopMirroring()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Idle::class.java)
        }
    }

    @Test
    fun `onProjectionDenied transitions to Error state`() = runTest {
        // When
        viewModel.onProjectionDenied()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Error::class.java)
            assertThat((state as MirroringUiState.Error).message).contains("permission denied")
        }
    }

    @Test
    fun `retryAfterError transitions from Error to Idle`() = runTest {
        // Given - start in error state
        viewModel.onProjectionDenied()

        // When
        viewModel.retryAfterError()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(MirroringUiState.Idle::class.java)
        }
    }
}
