package com.example.indriveclone.ui.util

import com.example.indriveclone.data.model.RouteSource
import java.util.Locale
import kotlin.math.roundToInt

/** "2.4 km" (or "850 m" for short hops). */
fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToInt()} m"
    else String.format(Locale.US, "%.1f km", meters / 1_000.0)

/** "7 min" (or "1 h 05 min"). */
fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (totalMinutes < 60) {
        "$totalMinutes min"
    } else {
        String.format(Locale.US, "%d h %02d min", totalMinutes / 60, totalMinutes % 60)
    }
}

/** Whole units, thousands separated: "1,250". */
fun formatMoney(value: Double): String = String.format(Locale.US, "%,.0f", value)

/** The small provider label required by the spec. */
fun routeSourceLabel(source: RouteSource): String = when (source) {
    RouteSource.ORS -> "Route via OpenRouteService"
    RouteSource.OSRM -> "Route via OSRM"
    RouteSource.HAVERSINE -> "Route estimated (offline)"
}

/** Longer explanation for the settings/help text. */
fun routeSourceDescription(source: RouteSource): String = when (source) {
    RouteSource.ORS -> "OpenRouteService (API key)"
    RouteSource.OSRM -> "Public OSRM demo server"
    RouteSource.HAVERSINE -> "Straight-line estimate ×1.3 at 30 km/h"
}

fun formatRating(rating: Double): String = String.format(Locale.US, "%.1f", rating)

/** "5 min ago", "just now" — used for the request list. */
fun formatRelativeTime(createdAtMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - createdAtMillis) / 1_000L).coerceAtLeast(0)
    return when {
        seconds < 30 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3_600 -> "${seconds / 60} min ago"
        else -> "${seconds / 3_600} h ago"
    }
}
