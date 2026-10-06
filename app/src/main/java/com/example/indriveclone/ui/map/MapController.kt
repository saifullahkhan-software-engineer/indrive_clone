package com.example.indriveclone.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.example.indriveclone.R
import com.example.indriveclone.data.model.LatLng
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Which pin an interaction refers to. */
enum class PinKind {
    PICKUP,
    DESTINATION,
}

/**
 * Every osmdroid call in the app lives here or in [MapCanvas] — the "map backend" seam.
 *
 * Adding live driver tracking later means adding one marker plus a `setDriverPosition(...)` call: no
 * screen ever touches `MapView`, `GeoPoint` or `Polyline` directly.
 */
class MapController(
    private val context: Context,
    private val mapView: MapView,
) {

    /** Called when the user taps the map. */
    var onMapTap: (LatLng) -> Boolean = { false }

    /** Called once a dragged marker is released. */
    var onMarkerDrag: (PinKind, LatLng) -> Unit = { _, _ -> }

    private var appliedPickup: LatLng? = null
    private var appliedDestination: LatLng? = null
    private var appliedDeviceLocation: LatLng? = null
    private var appliedPolyline: List<LatLng> = emptyList()

    private val pickupMarker: Marker = createPinMarker(R.drawable.ic_marker_pickup, PinKind.PICKUP)
    private val destinationMarker: Marker = createPinMarker(R.drawable.ic_marker_destination, PinKind.DESTINATION)
    private val deviceMarker: Marker = Marker(mapView).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_user_location)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = "Your location"
    }

    private val routeCasing: Polyline = Polyline(mapView).apply {
        outlinePaint.color = Color.argb(90, 0, 0, 0)
        outlinePaint.strokeWidth = 20f
        outlinePaint.isAntiAlias = true
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
    }

    private val routeLine: Polyline = Polyline(mapView).apply {
        outlinePaint.color = Color.rgb(0x2F, 0x6B, 0x3C)
        outlinePaint.strokeWidth = 11f
        outlinePaint.isAntiAlias = true
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
    }

    init {
        mapView.overlays.add(routeCasing)
        mapView.overlays.add(routeLine)
        // Added before the markers: osmdroid dispatches touches in reverse overlay order, so the
        // markers get their taps (and drags) first and this overlay only sees "empty map" taps.
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = onMapTap(p.toLatLng())

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }),
        )
        mapView.overlays.add(deviceMarker)
        mapView.overlays.add(destinationMarker)
        mapView.overlays.add(pickupMarker)
        deviceMarker.isEnabled = false
    }

    fun onResume() = mapView.onResume()

    fun onPause() = mapView.onPause()

    fun onDetach() = mapView.onDetach()

    /** Animated recentre. */
    fun moveTo(position: LatLng, zoom: Double = DEFAULT_ZOOM) {
        mapView.controller.animateTo(position.toGeoPoint(), zoom, ANIMATION_MS)
    }

    /** Immediate recentre — used for the very first position so the map does not fly in from (0,0). */
    fun jumpTo(position: LatLng, zoom: Double = DEFAULT_ZOOM) {
        mapView.controller.setCenter(position.toGeoPoint())
        mapView.controller.setZoom(zoom)
    }

    fun fitTo(points: List<LatLng>, animated: Boolean = true) {
        val geoPoints = points.filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .map { it.toGeoPoint() }
        when {
            geoPoints.isEmpty() -> Unit
            geoPoints.size == 1 -> {
                mapView.controller.setCenter(geoPoints.first())
                mapView.controller.setZoom(SINGLE_POINT_ZOOM)
            }
            else -> {
                val box: BoundingBox = BoundingBox.fromGeoPoints(geoPoints)
                mapView.post {
                    mapView.zoomToBoundingBox(box, animated, FIT_PADDING_PX)
                }
            }
        }
    }

    fun setPickup(position: LatLng?) {
        appliedPickup = bindPin(pickupMarker, position, appliedPickup)
    }

    fun setDestination(position: LatLng?) {
        appliedDestination = bindPin(destinationMarker, position, appliedDestination)
    }

    fun setDeviceLocation(position: LatLng?) {
        appliedDeviceLocation = bindPin(deviceMarker, position, appliedDeviceLocation)
    }

    /** Road polyline from the active [com.example.indriveclone.data.model.RouteResult]. */
    fun setRoute(polyline: List<LatLng>) {
        if (polyline == appliedPolyline) return
        val points = polyline.map { it.toGeoPoint() }
        routeCasing.setPoints(points)
        routeLine.setPoints(points)
        appliedPolyline = polyline
        mapView.invalidate()
    }

    fun clearRoute() {
        if (appliedPolyline.isEmpty()) return
        routeCasing.setPoints(emptyList())
        routeLine.setPoints(emptyList())
        appliedPolyline = emptyList()
        mapView.invalidate()
    }

    /**
     * Binds a marker to a position, doing nothing when the value is unchanged — [MapCanvas] calls this
     * from every recomposition, and a redundant `invalidate()` per frame is a real cost on a map.
     * Returns the value now on the map.
     */
    private fun bindPin(marker: Marker, position: LatLng?, applied: LatLng?): LatLng? {
        if (position == applied) return applied
        if (position == null) {
            mapView.overlays.remove(marker)
        } else {
            marker.position = position.toGeoPoint()
            if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
        }
        mapView.invalidate()
        return position
    }

    private fun createPinMarker(iconRes: Int, kind: PinKind): Marker = Marker(mapView).apply {
        icon = ContextCompat.getDrawable(context, iconRes)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        isDraggable = true
        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) = Unit

            override fun onMarkerDrag(marker: Marker) = Unit

            override fun onMarkerDragEnd(marker: Marker) {
                onMarkerDrag(kind, marker.position.toLatLng())
            }
        })
    }

    companion object {
        const val DEFAULT_ZOOM = 14.5
        private const val SINGLE_POINT_ZOOM = 15.5
        private const val ANIMATION_MS = 450L
        private const val FIT_PADDING_PX = 90
    }
}

internal fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

internal fun GeoPoint.toLatLng(): LatLng = LatLng(latitude = latitude, longitude = longitude)
