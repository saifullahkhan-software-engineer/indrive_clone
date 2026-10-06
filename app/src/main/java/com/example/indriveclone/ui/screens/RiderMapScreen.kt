package com.example.indriveclone.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indriveclone.data.model.AddressSuggestion
import com.example.indriveclone.data.model.DriveStep
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.domain.route.RouteState
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.components.FareAdjuster
import com.example.indriveclone.ui.components.RouteSourceChip
import com.example.indriveclone.ui.components.StatPair
import com.example.indriveclone.ui.map.MapCanvas
import com.example.indriveclone.ui.map.PinKind
import com.example.indriveclone.ui.theme.LabelTextStyle
import com.example.indriveclone.ui.util.formatDistance
import com.example.indriveclone.ui.util.formatDuration
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.viewmodel.RiderEvent
import com.example.indriveclone.viewmodel.RiderUiState
import com.example.indriveclone.viewmodel.RiderViewModel

/**
 * Rider screen: map + two draggable pins + debounced road route + fare adjuster + "Request ride".
 *
 * Everything about how a route is obtained (debounce, cancel, cache, ORS→OSRM→Haversine fallback) lives
 * behind [RiderViewModel]; this file is layout and events only.
 */
@Composable
fun RiderMapScreen(
    role: UserRole?,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    onOpenOffers: (String) -> Unit,
    viewModel: RiderViewModel = viewModel(factory = RiderViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onLocationPermissionResult(granted)
        if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RiderEvent.RideCreated -> {
                    // Demo drivers answer while the offers screen is open.
                    viewModel.simulateIncomingOffers(event.rideId)
                    onOpenOffers(event.rideId)
                }
            }
        }
    }

    // Refit the camera when a new road route arrives (route fetches are debounced, so this stays calm).
    var fitRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.routeResult) {
        if ((state.routeResult?.polyline?.size ?: 0) >= 2) fitRequest++
    }

    var recenterRequest by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Request a ride",
                role = role,
                onOpenSettings = onOpenSettings,
                onSwitchRole = onSwitchRole,
                onResetDemoData = onResetDemoData,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MapCanvas(
                pickup = state.pickup?.position,
                destination = state.destination?.position,
                deviceLocation = state.deviceLocation,
                polyline = state.routeResult?.polyline.orEmpty(),
                fitPoints = listOfNotNull(state.pickup?.position, state.destination?.position),
                fitKey = fitRequest,
                initialCenter = state.deviceLocation ?: state.pickup?.position,
                flyTo = state.deviceLocation,
                flyToKey = recenterRequest,
                onMapTap = viewModel::onMapTap,
                onMarkerDrag = { kind, position ->
                    viewModel.onMarkerDragged(
                        step = if (kind == PinKind.PICKUP) DriveStep.PICKUP else DriveStep.DESTINATION,
                        position = position,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(
                    query = state.searchQuery,
                    searching = state.isSearching,
                    hasResults = state.searchResults.isNotEmpty(),
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSubmit = {
                        focusManager.clearFocus()
                        viewModel.submitSearch()
                    },
                    onClear = {
                        viewModel.onSearchQueryChange("")
                        viewModel.dismissSearchResults()
                    },
                )

                if (state.searchResults.isNotEmpty()) {
                    SearchResultsCard(state = state, onSelect = viewModel::selectSuggestion)
                }

                PinSelector(
                    activeStep = state.activeStep,
                    onStepChange = viewModel::setActiveStep,
                    onSwap = viewModel::swapEndpoints,
                    onRecenter = { recenterRequest++ },
                )

                if (state.usingDefaultCity) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "Location unavailable — using a default city. Drag the pins to your street.",
                            style = LabelTextStyle,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            TripPanel(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter),
                onEditPickup = { viewModel.setActiveStep(DriveStep.PICKUP) },
                onEditDestination = { viewModel.setActiveStep(DriveStep.DESTINATION) },
                onFareChange = viewModel::setFare,
                onFareStep = viewModel::adjustFare,
                onManualDistanceChange = viewModel::onManualDistanceChange,
                onUseManualDistance = viewModel::useManualDistance,
                onRequestRide = viewModel::requestRide,
            )
        }
    }
}

@Composable
private fun SearchResultsCard(state: RiderUiState, onSelect: (AddressSuggestion) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            state.searchResults.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = suggestion.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    searching: Boolean,
    hasResults: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search a place (optional)") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (!hasResults) onSubmit() }),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun PinSelector(
    activeStep: DriveStep,
    onStepChange: (DriveStep) -> Unit,
    onSwap: () -> Unit,
    onRecenter: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tap the map:",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            FilterChip(
                selected = activeStep == DriveStep.PICKUP,
                onClick = { onStepChange(DriveStep.PICKUP) },
                label = { Text("Pickup") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = activeStep == DriveStep.DESTINATION,
                onClick = { onStepChange(DriveStep.DESTINATION) },
                label = { Text("Destination") },
            )
            IconButton(onClick = onSwap) {
                Icon(Icons.Filled.Refresh, contentDescription = "Swap pickup and destination")
            }
            IconButton(onClick = onRecenter) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Center on my location")
            }
        }
    }
}

@Composable
private fun TripPanel(
    state: RiderUiState,
    modifier: Modifier = Modifier,
    onEditPickup: () -> Unit,
    onEditDestination: () -> Unit,
    onFareChange: (Double) -> Unit,
    onFareStep: (Int) -> Unit,
    onManualDistanceChange: (String) -> Unit,
    onUseManualDistance: () -> Unit,
    onRequestRide: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EndpointLine(marker = "A", label = state.pickup?.label ?: "Pickup not set", onEdit = onEditPickup)
            EndpointLine(
                marker = "B",
                label = state.destination?.label ?: "Destination not set",
                onEdit = onEditDestination,
            )

            HorizontalDivider()

            when (val routeState = state.routeState) {
                is RouteState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatPair(label = "Road distance", value = formatDistance(routeState.result.distanceMeters))
                    Spacer(Modifier.width(24.dp))
                    StatPair(label = "Trip time", value = formatDuration(routeState.result.durationSeconds))
                    Spacer(Modifier.weight(1f))
                    RouteSourceChip(source = routeState.result.source)
                }

                RouteState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Finding the road route…", style = MaterialTheme.typography.bodyMedium)
                }

                is RouteState.Failure -> ManualDistanceEntry(
                    text = state.manualDistanceKm,
                    message = routeState.message,
                    onTextChange = onManualDistanceChange,
                    onConfirm = onUseManualDistance,
                )

                RouteState.Idle -> Text(
                    text = "Tap the map (or search) to set the pickup and the destination.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.bounds?.let { bounds ->
                HorizontalDivider()
                FareAdjuster(
                    fare = state.fare,
                    bounds = bounds,
                    onFareChange = onFareChange,
                    onStep = onFareStep,
                )
            }

            Button(
                onClick = onRequestRide,
                enabled = state.canRequestRide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = if (state.bounds == null) {
                            "Set pickup and destination"
                        } else {
                            "Request ride for ${formatMoney(state.fare)}"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointLine(marker: String, label: String, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (marker == "A") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = marker,
                color = Color.White,
                style = LabelTextStyle,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit point $marker",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ManualDistanceEntry(
    text: String,
    message: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Routing failed: $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Distance (km)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConfirm) { Text("Use") }
        }
    }
}
