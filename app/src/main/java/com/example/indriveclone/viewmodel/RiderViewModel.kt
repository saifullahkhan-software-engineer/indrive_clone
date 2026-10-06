package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.location.LocationProvider
import com.example.indriveclone.data.model.AddressSuggestion
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.model.DriveStep
import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RideDraft
import com.example.indriveclone.data.model.RideStatus
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.data.model.prettyCoords
import com.example.indriveclone.data.repository.RideRepository
import com.example.indriveclone.data.settings.SettingsRepository
import com.example.indriveclone.domain.demo.DemoOfferSource
import com.example.indriveclone.domain.fare.FareBounds
import com.example.indriveclone.domain.fare.FareCalculator
import com.example.indriveclone.domain.route.GeocodingService
import com.example.indriveclone.domain.route.RoutePlanner
import com.example.indriveclone.domain.route.RouteServiceFactory
import com.example.indriveclone.domain.route.RouteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A map pin: a position plus the best label we could find for it. */
data class MapPoint(val position: LatLng, val label: String)

data class RiderUiState(
    val pickup: MapPoint? = null,
    val destination: MapPoint? = null,
    val activeStep: DriveStep = DriveStep.PICKUP,
    val routeState: RouteState = RouteState.Idle,
    val suggestedFare: Double = 0.0,
    val bounds: FareBounds? = null,
    val fare: Double = 0.0,
    val deviceLocation: LatLng? = null,
    val usingDefaultCity: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AddressSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val submitting: Boolean = false,
    val manualDistanceKm: String = "",
) {
    val routeResult: RouteResult? get() = (routeState as? RouteState.Success)?.result

    val canRequestRide: Boolean
        get() = pickup != null && destination != null && routeResult != null && !submitting
}

/** One-shot navigation signals (navigation is a screen concern, not state). */
sealed interface RiderEvent {
    data class RideCreated(val rideId: String) : RiderEvent
}

/**
 * Rider screen state machine: two pins -> debounced route -> fare window -> ride request.
 *
 * The ViewModel owns no routing policy of its own; it delegates to [RoutePlanner], which debounces,
 * cancels superseded lookups and caches per (from, to) pair.
 */
class RiderViewModel(
    private val rideRepository: RideRepository,
    private val settingsRepository: SettingsRepository,
    private val routePlanner: RoutePlanner,
    private val routeServiceFactory: RouteServiceFactory,
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService,
    private val demoFleet: DemoOfferSource,
    private val buildConfigOrsKey: String,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RiderUiState())
    val uiState: StateFlow<RiderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RiderEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RiderEvent> = _events.asSharedFlow()

    private var settings: AdminSettings = AdminSettings()
    private var fareKey: Pair<LatLng, LatLng>? = null
    private var offerSimulation: Job? = null

    private val pickupFlow = MutableStateFlow<MapPoint?>(null)
    private val destinationFlow = MutableStateFlow<MapPoint?>(null)

    init {
        // Provider chain follows the ORS key: Settings screen first, then BuildConfig (local.properties).
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.orsApiKey }
                .distinctUntilChanged()
                .collect { settingsKey ->
                    routePlanner.updateKey(routeServiceFactory.resolveOrsKey(settingsKey, buildConfigOrsKey))
                }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { updated ->
                settings = updated
                recomputeFareWindow()
            }
        }

        // Collected for the lifetime of the ViewModel: it debounces and cancels superseded lookups.
        viewModelScope.launch {
            routePlanner
                .route(
                    from = pickupFlow.map { it?.position },
                    to = destinationFlow.map { it?.position },
                )
                .collect { routeState -> onRouteState(routeState) }
        }

        viewModelScope.launch { loadDeviceLocation() }
    }

    // ---------------------------------------------------------------------------------------------
    // Pins
    // ---------------------------------------------------------------------------------------------

    fun setActiveStep(step: DriveStep) =
        _uiState.update { it.copy(activeStep = step, searchResults = emptyList()) }

    /** Tap on the map: sets whichever pin is currently active. */
    fun onMapTap(position: LatLng) = setPin(_uiState.value.activeStep, position)

    fun onMarkerDragged(step: DriveStep, position: LatLng) = setPin(step, position)

    fun setPin(step: DriveStep, position: LatLng) {
        applyPin(step, MapPoint(position = position, label = position.prettyCoords()))
        // After the pickup is placed, the next tap naturally targets the destination.
        if (step == DriveStep.PICKUP && _uiState.value.destination == null) {
            _uiState.update { it.copy(activeStep = DriveStep.DESTINATION) }
        }
        resolveLabel(step, position)
    }

    fun clearPin(step: DriveStep) = applyPin(step, null)

    fun swapEndpoints() {
        val state = _uiState.value
        applyPin(DriveStep.PICKUP, state.destination)
        applyPin(DriveStep.DESTINATION, state.pickup)
    }

    fun selectSuggestion(suggestion: AddressSuggestion) {
        val step = _uiState.value.activeStep
        applyPin(step, MapPoint(suggestion.position, suggestion.label))
        _uiState.update { it.copy(searchResults = emptyList(), searchQuery = suggestion.label) }
        if (step == DriveStep.PICKUP && _uiState.value.destination == null) {
            _uiState.update { it.copy(activeStep = DriveStep.DESTINATION) }
        }
    }

    private fun applyPin(step: DriveStep, point: MapPoint?) {
        when (step) {
            DriveStep.PICKUP -> pickupFlow.value = point
            DriveStep.DESTINATION -> destinationFlow.value = point
        }
        _uiState.update { state ->
            if (step == DriveStep.PICKUP) state.copy(pickup = point) else state.copy(destination = point)
        }
    }

    /** Best-effort reverse geocoding: the pin is usable immediately, the label arrives a moment later. */
    private fun resolveLabel(step: DriveStep, position: LatLng) {
        viewModelScope.launch {
            val label = geocodingService.reverse(position) ?: return@launch
            val current = when (step) {
                DriveStep.PICKUP -> _uiState.value.pickup
                DriveStep.DESTINATION -> _uiState.value.destination
            } ?: return@launch
            if (current.position != position) return@launch
            applyPin(step, current.copy(label = label))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------------------------------------

    fun onLocationPermissionResult(granted: Boolean) {
        viewModelScope.launch { loadDeviceLocation(forcePickup = granted) }
    }

    /**
     * One-shot fix, with the demo city as fallback. A pickup the user has already moved by hand is
     * never overwritten — only an auto-selected one (or none) gets updated when we learn more.
     */
    private suspend fun loadDeviceLocation(forcePickup: Boolean = false) {
        val located = locationProvider.currentLocation()
        val fallback = located == null
        val position = located ?: LocationProvider.DEFAULT_CITY
        val previousDeviceLocation = _uiState.value.deviceLocation
        val currentPickup = _uiState.value.pickup

        _uiState.update { it.copy(deviceLocation = position, usingDefaultCity = fallback) }

        val pickupIsAuto = currentPickup == null || currentPickup.position == previousDeviceLocation
        if (pickupIsAuto && (currentPickup == null || forcePickup || fallback)) {
            setPin(DriveStep.PICKUP, position)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------------

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    /** Explicit submit keeps Nominatim usage (1 req/s) polite. */
    fun submitSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.length < 3) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val results = geocodingService.search(query, near = _uiState.value.deviceLocation)
            _uiState.update { it.copy(isSearching = false, searchResults = results) }
        }
    }

    fun dismissSearchResults() = _uiState.update { it.copy(searchResults = emptyList()) }

    // ---------------------------------------------------------------------------------------------
    // Routing + fare
    // ---------------------------------------------------------------------------------------------

    private fun onRouteState(routeState: RouteState) {
        _uiState.update { it.copy(routeState = routeState) }
        when (routeState) {
            is RouteState.Success -> recomputeFareWindow()
            RouteState.Idle -> {
                fareKey = null
                _uiState.update { it.copy(bounds = null, suggestedFare = 0.0, fare = 0.0) }
            }
            else -> Unit
        }
    }

    /**
     * Recomputes the fare window for the current route. The fare itself is only reset to the
     * suggestion when the endpoints changed, so a user's adjustment survives settings edits and
     * cache hits.
     */
    private fun recomputeFareWindow() {
        val state = _uiState.value
        val route = state.routeResult ?: return
        val pickup = state.pickup?.position ?: return
        val destination = state.destination?.position ?: return

        val suggested = FareCalculator.suggestedFare(route.distanceMeters, settings)
        val bounds = FareCalculator.boundsFor(suggested, settings)
        val key = pickup to destination
        val isNewTrip = key != fareKey

        fareKey = key
        _uiState.update { current ->
            current.copy(
                suggestedFare = suggested,
                bounds = bounds,
                fare = if (isNewTrip) {
                    FareCalculator.initialFare(bounds)
                } else {
                    FareCalculator.clampToBounds(current.fare, bounds)
                },
            )
        }
    }

    fun setFare(fare: Double) {
        val bounds = _uiState.value.bounds ?: return
        _uiState.update { it.copy(fare = FareCalculator.clampToBounds(fare, bounds)) }
    }

    /** `direction` is +1 for "+" and -1 for "−"; both respect the window. */
    fun adjustFare(direction: Int) {
        val state = _uiState.value
        val bounds = state.bounds ?: return
        _uiState.update { it.copy(fare = FareCalculator.stepFare(state.fare, direction, bounds)) }
    }

    fun onManualDistanceChange(text: String) = _uiState.update { it.copy(manualDistanceKm = text) }

    /** Escape hatch, shown only if even the offline estimate failed. */
    fun useManualDistance() {
        val km = _uiState.value.manualDistanceKm.replace(',', '.').toDoubleOrNull() ?: return
        if (km <= 0) return
        val state = _uiState.value
        val pickup = state.pickup?.position ?: return
        val destination = state.destination?.position ?: return
        val distanceMeters = km * 1_000.0
        onRouteState(
            RouteState.Success(
                result = RouteResult(
                    distanceMeters = distanceMeters,
                    durationSeconds = distanceMeters / (30.0 / 3.6),
                    polyline = listOf(pickup, destination),
                    source = RouteSource.HAVERSINE,
                ),
                fromCache = false,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Requesting a ride
    // ---------------------------------------------------------------------------------------------

    fun requestRide() {
        val state = _uiState.value
        val pickup = state.pickup ?: return
        val destination = state.destination ?: return
        val route = state.routeResult ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true) }
            val ride = rideRepository.createRideRequest(
                RideDraft(
                    pickup = pickup.position,
                    pickupLabel = pickup.label,
                    destination = destination.position,
                    destinationLabel = destination.label,
                    distanceMeters = route.distanceMeters,
                    durationSeconds = route.durationSeconds,
                    routePolyline = route.polyline,
                    routeSource = route.source,
                    offeredFare = state.fare,
                    suggestedFare = state.suggestedFare,
                ),
            )
            _uiState.update { it.copy(submitting = false) }
            _events.emit(RiderEvent.RideCreated(ride.id))
        }
    }

    /**
     * Demo: 2–3 drivers answer a few seconds after the request. Stops as soon as the rider has accepted
     * an offer (or the ride vanished), so no offers trickle in on the "driver is on the way" screen.
     */
    fun simulateIncomingOffers(rideId: String) {
        offerSimulation?.cancel()
        offerSimulation = viewModelScope.launch {
            val ride = rideRepository.rideRequest(rideId).first() ?: return@launch
            val offers = demoFleet.offersFor(ride, settings)
            offers.forEachIndexed { index, offer ->
                delay(FIRST_OFFER_DELAY_MS + index * OFFER_INTERVAL_MS + random.nextLong(OFFER_JITTER_MS))
                val current = rideRepository.rideRequest(rideId).first() ?: return@launch
                if (current.status == RideStatus.ACCEPTED) return@launch
                rideRepository.placeOffer(offer)
            }
        }
    }

    fun stopOfferSimulation() {
        offerSimulation?.cancel()
        offerSimulation = null
    }

    companion object {
        private const val FIRST_OFFER_DELAY_MS = 2_500L
        private const val OFFER_INTERVAL_MS = 2_000L
        private const val OFFER_JITTER_MS = 900L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                val services = ServiceLocator.from(application)
                RiderViewModel(
                    rideRepository = services.rideRepository,
                    settingsRepository = services.settingsRepository,
                    routePlanner = services.routePlanner,
                    routeServiceFactory = services.routeServiceFactory,
                    locationProvider = services.locationProvider,
                    geocodingService = services.geocodingService,
                    demoFleet = services.demoFleet,
                    buildConfigOrsKey = services.buildConfigOrsKey,
                )
            }
        }
    }
}
