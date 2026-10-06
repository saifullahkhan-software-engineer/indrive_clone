package com.example.indriveclone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.components.DriverRow
import com.example.indriveclone.ui.components.RouteSourceChip
import com.example.indriveclone.ui.components.StatPair
import com.example.indriveclone.ui.components.TripEndpoints
import com.example.indriveclone.ui.util.formatDistance
import com.example.indriveclone.ui.util.formatDuration
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.viewmodel.OffersEvent
import com.example.indriveclone.viewmodel.OffersUiState
import com.example.indriveclone.viewmodel.OffersViewModel

/**
 * Rider's incoming offers. The list is live (mock drivers answer a few seconds after the request was
 * created); accepting one moves the ride to ACCEPTED and opens the "driver is on the way" screen.
 */
@Composable
fun OffersScreen(
    rideId: String,
    role: UserRole?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    onDriverOnTheWay: (String) -> Unit,
    viewModel: OffersViewModel = viewModel(
        key = "offers-$rideId",
        factory = OffersViewModel.factory(rideId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) {
        viewModel.events.collect { event ->
            if (event is OffersEvent.OfferAccepted) onDriverOnTheWay(rideId)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Driver offers",
                role = role,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onSwitchRole = onSwitchRole,
                onResetDemoData = onResetDemoData,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RideSummary(state = state, modifier = Modifier.padding(16.dp))

            if (state.offers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(16.dp))
                    Text("Looking for drivers nearby…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Simulated drivers answer within a few seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.offers, key = { it.id }) { offer ->
                        OfferCard(
                            offer = offer,
                            busy = state.busyOfferId == offer.id,
                            onAccept = { viewModel.accept(offer.id) },
                            onDecline = { viewModel.decline(offer.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideSummary(state: OffersUiState, modifier: Modifier = Modifier) {
    val ride = state.ride ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Your offer", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = formatMoney(ride.offeredFare),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                RouteSourceChip(source = ride.routeSource)
            }
            TripEndpoints(pickupLabel = ride.pickupLabel, destinationLabel = ride.destinationLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatPair(label = "Road distance", value = formatDistance(ride.distanceMeters))
                StatPair(label = "Trip time", value = formatDuration(ride.durationSeconds))
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: DriverOffer,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatMoney(offer.fare),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (offer.isCounterOffer) {
                        Text(
                            text = "Counter-offer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "ETA ${offer.etaMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            DriverRow(
                name = offer.driverName,
                rating = offer.driverRating,
                carModel = offer.carModel,
                carPlate = offer.carPlate,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Decline") }
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Accept", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
