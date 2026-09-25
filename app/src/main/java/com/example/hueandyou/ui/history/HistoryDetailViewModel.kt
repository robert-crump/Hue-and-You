package com.example.hueandyou.ui.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hueandyou.HueAndYouApplication
import com.example.hueandyou.colorspace.ColorExtractor
import com.example.hueandyou.colorspace.PaletteScorer
import com.example.hueandyou.colorspace.PixelSource
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.data.history.HistoryRepository
import com.example.hueandyou.ui.common.toPixelSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface HistoryDetailUiState {
    data object Loading : HistoryDetailUiState

    /** The entry vanished (e.g. deleted from another screen) while this one was open. */
    data object NotFound : HistoryDetailUiState

    data class Loaded(
        val entry: HistoryEntry,
        val photo: Bitmap,
        /** The fixed top-3 chip row, computed once from the photo; only the highlight moves on a pick. */
        val chipColorsArgb: List<Int>,
    ) : HistoryDetailUiState
}

private fun decodeThumbnail(path: String): Bitmap =
    requireNotNull(BitmapFactory.decodeFile(path)) { "Thumbnail file missing: $path" }

class HistoryDetailViewModel(
    private val repository: HistoryRepository,
    private val entryId: Long,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bitmapDecoder: (String) -> Bitmap = ::decodeThumbnail,
    private val pixelSourceOf: (Bitmap) -> PixelSource = Bitmap::toPixelSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryDetailUiState>(HistoryDetailUiState.Loading)
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    private var photo: Bitmap? = null

    /** The thumbnail's candidate colors, extracted once (off-main-thread) when the entry first opens. */
    private var candidates: List<Int>? = null

    init {
        viewModelScope.launch {
            repository.observeEntry(entryId).collect { entry -> onEntry(entry) }
        }
    }

    private suspend fun onEntry(entry: HistoryEntry?) {
        if (entry == null) {
            _uiState.value = HistoryDetailUiState.NotFound
            return
        }
        val bitmap = photo ?: withContext(backgroundDispatcher) { bitmapDecoder(entry.thumbnailPath) }
            .also { photo = it }
        val extractedCandidates = candidates ?: withContext(backgroundDispatcher) {
            ColorExtractor.extractCandidates(pixelSourceOf(bitmap)).map { it.argb }
        }.also { candidates = it }
        _uiState.value = HistoryDetailUiState.Loaded(entry, bitmap, ColorExtractor.selectTopColors(extractedCandidates))
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renameEntry(entryId, name) }
    }

    /** Re-picks the entry's color from one of the chips; resets the sample point to the center box. */
    fun pickCandidate(argb: Int) {
        val state = _uiState.value as? HistoryDetailUiState.Loaded ?: return
        if (argb == state.entry.calibratedArgb) return
        viewModelScope.launch { persistPick(state.entry, argb, sampleX = null, sampleY = null) }
    }

    /** Re-picks the entry's color by sampling around a tap on the photo, at normalized [x]/[y] in [0, 1]. */
    fun pickAtPoint(x: Double, y: Double) {
        val state = _uiState.value as? HistoryDetailUiState.Loaded ?: return
        viewModelScope.launch {
            val argb = withContext(backgroundDispatcher) {
                val pixels = pixelSourceOf(state.photo)
                ColorExtractor.extractColorAtPoint(
                    pixels,
                    x = (x * pixels.width).roundToInt(),
                    y = (y * pixels.height).roundToInt(),
                )
            }
            val latest = (_uiState.value as? HistoryDetailUiState.Loaded)?.entry ?: return@launch
            persistPick(latest, argb, sampleX = x, sampleY = y)
        }
    }

    private suspend fun persistPick(entry: HistoryEntry, argb: Int, sampleX: Double?, sampleY: Double?) {
        when (entry.type) {
            HistoryEntryType.OBJECT -> repository.updateObjectPick(entryId, argb, sampleX, sampleY)
            HistoryEntryType.CLOTHING -> {
                val score = PaletteScorer.score(argb, entry.bestColorsArgb, entry.avoidColorsArgb)
                repository.updateClothingPick(entryId, argb, score, sampleX, sampleY)
            }
        }
    }

    companion object {
        fun factory(context: Context, entryId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (context.applicationContext as HueAndYouApplication)
                    .container.historyRepository
                HistoryDetailViewModel(repository, entryId)
            }
        }
    }
}
