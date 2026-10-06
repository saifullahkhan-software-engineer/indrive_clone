package com.example.indriveclone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indriveclone.data.model.RideStatus
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.components.ConfirmationRow
import com.example.indriveclone.ui.components.FareAdjuster
import com.example.indriveclone.ui.components.OfferStatusBadge
import com.example.indriveclone.ui.components.RouteSourceChip
import com.example.indriveclone.ui.components.StatPair
import com.example.indriveclone.ui.components.TripEndpoints
import com.example.indriveclone.ui.map.MapCanvas
import com.example.indriveclone.ui.theme.LabelTextStyle
import com.example.indriveclone.ui.util.formatDistance
import com.example.indriveclone.ui.util.formatDuration
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.viewmodel.DriverRideDetailUiState
import com.example.indriveclone.viewmodel.DriverRideDetailViewModel
import com.example.indriveclone.viewmodel.DriverRideEvent

/**
 * Driver's view of one request: a small map of the stored route, "accept at the rider's fare", and a
 * counter-offer bounded by exactly the same [com.example.indriveclone.domain.fare.FareCalculator]
 * window the rider's adjuster uses.
 */
@Composable
fun DriverRequestDetailScreen(
    rideId: String,
    role: UserRole?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    viewModel: DriverRideDetailViewModel = viewModel(
        key = "driver-ride-$rideId",
        factory = DriverRideDetailViewModel.factory(rideId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(rideId) {
        viewModel.events.collect { event ->
            when (event) {
                is DriverRideEvent.OfferPlaced -> snackbarHostState.showSnackbar(
                    if (event.isCounterOffer) "Counter-offer sent — the rider decides." else "Offer sent.",
                )
            }
        }
    }

    val ride = state.ride

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Ride request",
                role = role,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onSwitchRole = onSwitchRole,
                onResetDemoData = onResetDemoData,
            )
        },
    ) { padding ->
        if (ride == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    Text("This request is no longer available.")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.7f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                MapCanvas(
                    pickup = ride.pickup,
                    destination = ride.destination,
                    polyline = ride.routePolyline,
                    fitPoints = ride.routePolyline.ifEmpty { listOf(ride.pickup, ride.destination) },
                    fitKey = ride.id,
                    interactive = false,
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Rider offers", style = LabelTextStyle)
                            Text(
                                text = formatMoney(ride.offeredFare),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        RouteSourceChip(source = ride.routeSource)
                    }
                    TripEndpoints(pickupLabel = ride.pickupLabel, destinationLabel = ride.destinationLabel)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatPair(label = "Road distance", value = formatDistance(ride.distanceMeters))
                        Spacer(Modifier.width(24.dp))
                        StatPair(label = "Trip time", value = formatDuration(ride.durationSeconds))
                        Spacer(Modifier.width(24.dp))
                        StatPair(label = "Suggested", value = formatMoney(ride.suggestedFare))
                    }
                }
            }

            if (state.myOffers.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "My offers", style = MaterialTheme.typography.titleSmall)
                        state.myOffers.forEach { offer ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatMoney(offer.fare),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (offer.isCounterOffer) " (counter-offer)" else " (rider's fare)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                OfferStatusBadge(status = offer.status)
                            }
                        }
                    }
                }
            }

            if (ride.status == RideStatus.ACCEPTED) {
                ConfirmationRow(
                    text = ride.acceptedDriverName?.let { driver ->
                        if (driver == state.myOffers.firstOrNull()?.driverName) {
                            "The rider accepted your offer."
                        } else {
                            "$driver took this ride."
                        }
                    } ?: "The rider accepted an offer.",
                )
            } else {
                HorizontalDivider()

                Button(
                    onClick = { viewModel.submitOffer(isCounterOffer = false) },
                    enabled = !state.sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Accept at rider's fare (${formatMoney(ride.offeredFare)})",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (state.counterBounds != null) {
                    CounterOfferSection(
                        state = state,
                        onFareChange = viewModel::setCounterFare,
                        onStep = viewModel::adjustCounterFare,
                        onTextChange = viewModel::onCounterFareTextChange,
                        onSend = { viewModel.submitOffer(isCounterOffer = true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterOfferSection(
    state: DriverRideDetailUiState,
    onFareChange: (Double) -> Unit,
    onStep: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Counter-offer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Same bounds as the rider's adjuster: minimum fare to the suggested fare × the admin multiplier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.counterBounds?.let { bounds ->
                FareAdjuster(
                    fare = state.counterFare,
                    bounds = bounds,
                    onFareChange = onFareChange,
                    onStep = onStep,
                    amountLabel = "My counter-offer",
                    suggestedLabel = "Rider's suggestion",
                )
            }

            OutlinedTextField(
                value = state.counterFareText,
                onValueChange = onTextChange,
                label = { Text("Custom fare") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSend,
                enabled = !state.sending && !state.alreadyAccepted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send counter-offer", fontWeight = FontWeight.SemiBold) }
        }
    }
}
