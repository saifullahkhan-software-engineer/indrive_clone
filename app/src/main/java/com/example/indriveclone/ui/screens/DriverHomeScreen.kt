package com.example.indriveclone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.components.EmptyState
import com.example.indriveclone.ui.components.RideRequestCard
import com.example.indriveclone.ui.util.formatDistance
import com.example.indriveclone.ui.util.formatDuration
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.ui.util.formatRelativeTime
import com.example.indriveclone.viewmodel.DriverViewModel

/**
 * Driver home: every open request in the system. Because the rider and driver screens observe the same
 * repository, a request created in rider mode shows up here immediately.
 */
@Composable
fun DriverHomeScreen(
    role: UserRole?,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    onOpenRequest: (String) -> Unit,
    viewModel: DriverViewModel = viewModel(factory = DriverViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Open requests",
                role = role,
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
            if (state.openRequests.isEmpty()) {
                EmptyState(
                    title = "No open requests",
                    subtitle = "Use the menu to switch to the rider role, request a ride, then come back here.",
                )
                Button(
                    onClick = onSwitchRole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) { Text("Switch to rider") }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.openRequests, key = { it.id }) { ride ->
                        val myOffer = state.myOffersByRide[ride.id]
                        RideRequestCard(
                            pickupLabel = ride.pickupLabel,
                            destinationLabel = ride.destinationLabel,
                            distanceText = formatDistance(ride.distanceMeters),
                            durationText = formatDuration(ride.durationSeconds),
                            fareText = formatMoney(ride.offeredFare),
                            createdText = formatRelativeTime(ride.createdAtMillis, System.currentTimeMillis()),
                            onClick = { onOpenRequest(ride.id) },
                            myOfferStatus = myOffer?.status,
                            myOfferFareText = myOffer?.let { formatMoney(it.fare) },
                        )
                    }
                }
            }
        }
    }
}
