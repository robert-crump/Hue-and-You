package com.example.hueandyou.ui.matchcolors

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.IntArrayPixelSource
import com.example.hueandyou.colorspace.WhiteBalanceCorrection
import com.example.hueandyou.colorspace.WhiteBalanceResult
import com.example.hueandyou.colorspace.CircleRegion
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.data.history.ThumbnailStore
import com.example.hueandyou.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class MatchObjectViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val backgroundDispatcher = StandardTestDispatcher(mainDispatcher.scheduler, "background")

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirmCalibration_showsSpinnerImmediately_andExtractsOnBackgroundDispatcher() {
        val extractionThreadNames = mutableListOf<String>()
        val bitmap = allocateWithoutConstructor(Bitmap::class.java)
        val viewModel = MatchObjectViewModel(
            historyRepository = stub<HistoryRepository>(),
            thumbnailStore = stub<ThumbnailStore>(),
            settingsRepository = stub<SettingsRepository>(),
            backgroundDispatcher = backgroundDispatcher,
            pixelSourceOf = {
                extractionThreadNames += Thread.currentThread().name
                IntArrayPixelSource(2, 2, IntArray(4) { 0xFF336699.toInt() })
            },
        )

        setCalibration(viewModel, MatchObjectUiState.Calibrating(bitmap, successResult()))

        viewModel.confirmCalibration()

        // Nothing has run yet: the caller only flipped state and queued the work.
        assertEquals(MatchObjectUiState.ExtractingColors, viewModel.uiState.value)
        assertTrue(extractionThreadNames.isEmpty())

        mainDispatcher.scheduler.runCurrent()

        assertEquals(1, extractionThreadNames.size)
        assertTrue(viewModel.uiState.value is MatchObjectUiState.SelectingColors)
    }

    private fun successResult() = WhiteBalanceResult.Success(
        correction = WhiteBalanceCorrection(1.0, 1.0, 1.0),
        sampledRegion = CircleRegion(0, 0, 0),
    )

    @Suppress("UNCHECKED_CAST")
    private fun setCalibration(viewModel: MatchObjectViewModel, state: MatchObjectUiState.Calibrating) {
        val field: Field = MatchObjectViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        (field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<MatchObjectUiState>).value = state
    }

    private inline fun <reified T : Any> stub(): T = java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, _, _ -> error("not expected") } as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
