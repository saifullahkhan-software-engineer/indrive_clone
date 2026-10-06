package com.example.indriveclone.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.example.indriveclone.data.model.LatLng
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * Compose wrapper around osmdroid. Purely declarative from the outside: state in, events out — the
 * screens never see an osmdroid type, so swapping the map engine (or adding live tracking later) stays
 * contained here and in [MapController].
 *
 * @param polyline road geometry to draw (fewer than 2 points clears it).
 * @param fitPoints camera is fitted to these points whenever [fitKey] changes.
 * @param initialCenter used for the first centring (until then the camera would sit at 0,0).
 * @param flyTo camera animates here whenever [flyToKey] changes.
 */
@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    pickup: LatLng? = null,
    destination: LatLng? = null,
    deviceLocation: LatLng? = null,
    polyline: List<LatLng> = emptyList(),
    fitPoints: List<LatLng> = emptyList(),
    fitKey: Any? = null,
    /** Centred on once, as soon as it is known (the map otherwise starts at 0,0). */
    initialCenter: LatLng? = null,
    flyTo: LatLng? = null,
    flyToKey: Any? = null,
    interactive: Boolean = true,
    onMapTap: (LatLng) -> Unit = {},
    onMarkerDrag: (PinKind, LatLng) -> Unit = { _, _ -> },
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentOnMarkerDrag by rememberUpdatedState(onMarkerDrag)

    var hasCentred by remember { mutableStateOf(false) }
    val controllerState = remember { mutableStateOf<MapController?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(MapController.DEFAULT_ZOOM)
                controllerState.value = MapController(context, this).apply {
                    onMapTap = { position ->
                        currentOnMapTap(position)
                        true
                    }
                    onMarkerDrag = { kind, position -> currentOnMarkerDrag(kind, position) }
                }
            }
        },
        update = { view ->
            view.setMultiTouchControls(interactive)
            val controller = controllerState.value ?: return@AndroidView
            controller.setPickup(pickup)
            controller.setDestination(destination)
            controller.setDeviceLocation(deviceLocation)
            if (polyline.size >= 2) controller.setRoute(polyline) else controller.clearRoute()
        },
    )

    // Camera moves are side effects, never part of the view update.
    val controller = controllerState.value
    LaunchedEffect(initialCenter, controller) {
        if (!hasCentred && initialCenter != null && controller != null) {
            controller.jumpTo(initialCenter)
            hasCentred = true
        }
    }
    LaunchedEffect(fitKey, controller) {
        if (fitKey != null && controller != null) controller.fitTo(fitPoints)
    }
    LaunchedEffect(flyToKey, flyTo, controller) {
        if (flyToKey != null && flyTo != null && controller != null) controller.moveTo(flyTo)
    }

    // osmdroid needs resume/pause to control its tile provider.
    LaunchedEffect(controller) { controller?.onResume() }

    // Keyed on the lifecycle only: keying this on the map view (or writing state inside onDispose)
    // would tear down the freshly created map and drop the controller reference.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controllerState.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> controllerState.value?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controllerState.value?.onDetach()
        }
    }
}
