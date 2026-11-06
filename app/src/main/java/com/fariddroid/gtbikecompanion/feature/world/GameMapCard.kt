package com.fariddroid.gtbikecompanion.feature.world

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class MapUiState(val map: GameMap?, val image: ImageBitmap?)

/**
 * P8a. Exists only when the PC operator started the bridge with an authorized, calibrated image
 * (capability map.position). No phone GPS is involved: the position is the rider in the game.
 */
@HiltViewModel
class GameMapViewModel @Inject constructor(telemetry: TelemetryRepository, private val control: ControlRepository) : ViewModel() {
    private var imageFor: String? = null
    private var image: ImageBitmap? = null

    private val available = telemetry.feed.map { it.capability(CapabilityId.MAP_POSITION)?.available == true }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<MapUiState?> = available.flatMapLatest { on ->
        if (!on) flowOf<MapUiState?>(null) else flow<MapUiState?> {
            while (true) {
                val map = (control.gameMap() as? Outcome.Success)?.value
                if (map != null && map.assetId != imageFor) {
                    image = (control.mapImage(map.assetId) as? Outcome.Success)?.value?.let { decode(it) }
                    imageFor = if (image != null) map.assetId else null
                }
                emit(map?.let { MapUiState(it, image) })
                delay(1_000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Downsampled so a large map never exhausts memory on small phones. */
    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}

@Composable
fun GameMapCard(feed: TelemetryFeed, viewModel: GameMapViewModel = hiltViewModel()) {
    if (feed.capability(CapabilityId.MAP_POSITION)?.available != true) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val map = s.map ?: return
    SectionCard(stringResource(R.string.map_title), help = HelpTopic.MAP_GAME_WORLD) {
        val image = s.image
        if (image == null) {
            Text(stringResource(R.string.map_image_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val description = stringResource(if (map.position == null) R.string.map_outside else R.string.map_description)
        val route = MaterialTheme.colorScheme.primary
        val rider = MaterialTheme.colorScheme.secondary
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(map.width.toFloat() / map.height).semantics { contentDescription = description }.testTag("gameMap"),
        ) {
            drawImage(image, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            map.route?.let { r ->
                var previous: Offset? = null
                for (p in r.waypoints) {
                    if (p == null) { previous = null; continue }
                    val point = Offset((p.u * size.width).toFloat(), (p.v * size.height).toFloat())
                    previous?.let { drawLine(route, it, point, strokeWidth = 4f) }
                    previous = point
                }
            }
            map.position?.let { p ->
                val c = Offset((p.u * size.width).toFloat(), (p.v * size.height).toFloat())
                drawCircle(Color.White, radius = 11f, center = c)
                drawCircle(rider, radius = 8f, center = c)
                drawCircle(Color.Black, radius = 11f, center = c, style = Stroke(width = 2f))
            }
        }
        if (map.position == null) Text(stringResource(R.string.map_outside), style = MaterialTheme.typography.labelMedium)
    }
}
