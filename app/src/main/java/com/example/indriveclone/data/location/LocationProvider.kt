package com.example.indriveclone.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.indriveclone.data.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * All location access goes through here so that live tracking can be added later without touching a
 * single composable: replace [currentLocation] with a `Flow<LatLng>` fed by
 * `requestLocationUpdates` (plus a foreground service on Android 14+) and the map keeps working.
 *
 * Deliberately uses the platform [LocationManager] — no Play Services dependency, so the demo builds
 * and runs anywhere (emulator, de-Googled device, CI).
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Best-effort one-shot fix: last known position if it is recent enough, otherwise a single
     * update. Returns null when permission is missing, location is switched off, or nothing arrives
     * within [timeoutMillis] — callers then use [DEFAULT_CITY].
     */
    suspend fun currentLocation(timeoutMillis: Long = 6_000L): LatLng? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        if (!isLocationEnabled(manager)) return null

        lastKnown(manager)?.let { return it.toLatLng() }

        return withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location.toLatLng())
                        }

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    val requested = try {
                        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
                        true
                    } catch (securityException: SecurityException) {
                        false
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        false
                    }
                    if (!requested) {
                        val gpsStarted = try {
                            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
                            true
                        } catch (securityException: SecurityException) {
                            false
                        } catch (illegalArgumentException: IllegalArgumentException) {
                            false
                        }
                        if (!gpsStarted) {
                            manager.removeUpdates(listener)
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
    }

    private fun isLocationEnabled(manager: LocationManager): Boolean = try {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (exception: Exception) {
        false
    }

    private fun lastKnown(manager: LocationManager): Location? {
        val candidates = try {
            manager.allProviders.mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (securityException: SecurityException) {
                    null
                }
            }
        } catch (exception: Exception) {
            emptyList()
        }
        val newest = candidates.maxByOrNull { it.time } ?: return null
        val ageMillis = System.currentTimeMillis() - newest.time
        return newest.takeIf { ageMillis <= MAX_LAST_KNOWN_AGE_MS }
    }

    private fun Location.toLatLng() = LatLng(latitude = latitude, longitude = longitude)

    companion object {
        /** Fallback when permission is denied or no fix is available: central Almaty. */
        val DEFAULT_CITY = LatLng(latitude = 43.238949, longitude = 76.889709)

        private const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L
    }
}
