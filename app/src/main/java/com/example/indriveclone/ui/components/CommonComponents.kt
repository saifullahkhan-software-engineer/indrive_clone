package com.example.indriveclone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.indriveclone.data.model.OfferStatus
import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.ui.theme.LabelTextStyle
import com.example.indriveclone.ui.util.formatRating
import com.example.indriveclone.ui.util.routeSourceLabel

/** "Route via OSRM" / "Route via OpenRouteService" / "Route estimated (offline)". */
@Composable
fun RouteSourceChip(source: RouteSource, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = when (source) {
            RouteSource.ORS -> MaterialTheme.colorScheme.primary
            RouteSource.OSRM -> MaterialTheme.colorScheme.secondaryContainer
            RouteSource.HAVERSINE -> MaterialTheme.colorScheme.errorContainer
        },
        contentColor = when (source) {
            RouteSource.ORS -> MaterialTheme.colorScheme.onPrimary
            RouteSource.OSRM -> MaterialTheme.colorScheme.onSecondaryContainer
            RouteSource.HAVERSINE -> MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Text(
            text = routeSourceLabel(source),
            style = LabelTextStyle,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Two-line route summary used in every trip card. */
@Composable
fun TripEndpoints(
    pickupLabel: String,
    destinationLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EndpointRow(
            label = pickupLabel,
            marker = "A",
            color = MaterialTheme.colorScheme.tertiary,
        )
        EndpointRow(
            label = destinationLabel,
            marker = "B",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EndpointRow(label: String, marker: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(4.dp)) {
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Key/value line for the metadata rows (distance, trip time, offers …). */
@Composable
fun StatPair(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Driver identity row: name, rating, car, plate. */
@Composable
fun DriverRow(
    name: String,
    rating: Double,
    carModel: String,
    carPlate: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$carModel • $carPlate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = formatRating(rating), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pending / accepted / rejected badge for the driver's own offers. */
@Composable
fun OfferStatusBadge(status: OfferStatus, modifier: Modifier = Modifier) {
    val (background, content) = when (status) {
        OfferStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to "Pending"
        OfferStatus.ACCEPTED -> MaterialTheme.colorScheme.primary to "Accepted by rider"
        OfferStatus.DECLINED -> MaterialTheme.colorScheme.errorContainer to "Rejected by rider"
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = background) {
        Text(
            text = content,
            style = LabelTextStyle,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Empty-state block with a big icon. */
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ride request card used by the driver list (optionally showing the driver's own bid status). */
@Composable
fun RideRequestCard(
    pickupLabel: String,
    destinationLabel: String,
    distanceText: String,
    durationText: String,
    fareText: String,
    createdText: String,
    onClick: () -> Unit,
    myOfferStatus: OfferStatus? = null,
    myOfferFareText: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fareText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = createdText,
                    style = LabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TripEndpoints(pickupLabel = pickupLabel, destinationLabel = destinationLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatPair(label = "Distance", value = distanceText)
                StatPair(label = "Trip time", value = durationText)
            }
            if (myOfferStatus != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfferStatusBadge(status = myOfferStatus)
                    if (myOfferFareText != null) {
                        Text(
                            text = " · my bid $myOfferFareText",
                            style = LabelTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Small green confirmation row (used after accepting). */
@Composable
fun ConfirmationRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
