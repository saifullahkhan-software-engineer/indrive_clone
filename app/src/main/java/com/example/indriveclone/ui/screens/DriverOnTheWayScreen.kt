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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.components.ConfirmationRow
import com.example.indriveclone.ui.components.DriverRow
import com.example.indriveclone.ui.components.RouteSourceChip
import com.example.indriveclone.ui.components.StatPair
import com.example.indriveclone.ui.components.TripEndpoints
import com.example.indriveclone.ui.map.MapCanvas
import com.example.indriveclone.ui.util.formatDistance
import com.example.indriveclone.ui.util.formatDuration
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.viewmodel.ActiveRideViewModel

/** "Your driver is on the way" — the end of the rider flow in this phase. */
@Composable
fun DriverOnTheWayScreen(
    rideId: String,
    role: UserRole?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    onTripCancelled: () -> Unit,
    viewModel: ActiveRideViewModel = viewModel(
        key = "active-$rideId",
        factory = ActiveRideViewModel.factory(rideId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The ride disappeared (cancelled or demo data reset) — nothing left to show.
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onTripCancelled()
    }

    val ride = state.ride ?: return
    val offer = state.acceptedOffer

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Driver is on the way",
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConfirmationRow(
                text = offer?.let { "${it.driverName} accepted your ride." } ?: "Your driver is on the way.",
            )

            if (offer != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DriverRow(
                            name = offer.driverName,
                            rating = offer.driverRating,
                            carModel = offer.carModel,
                            carPlate = offer.carPlate,
                        )
                        Row {
                            StatPair(label = "Arrives in", value = "${offer.etaMinutes} min")
                            Spacer(Modifier.width(24.dp))
                            StatPair(label = "Agreed fare", value = formatMoney(offer.fare))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
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
                    TripEndpoints(pickupLabel = ride.pickupLabel, destinationLabel = ride.destinationLabel)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatPair(label = "Road distance", value = formatDistance(ride.distanceMeters))
                        Spacer(Modifier.width(24.dp))
                        StatPair(label = "Trip time", value = formatDuration(ride.durationSeconds))
                        Spacer(Modifier.weight(1f))
                        RouteSourceChip(source = ride.routeSource)
                    }
                    Text(
                        text = "Live driver tracking, chat and payments are out of scope for this phase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = { viewModel.cancelRide(); onTripCancelled() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel ride", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
