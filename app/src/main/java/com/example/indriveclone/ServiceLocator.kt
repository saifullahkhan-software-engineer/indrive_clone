package com.example.indriveclone

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.indriveclone.data.location.LocationProvider
import com.example.indriveclone.data.repository.InMemoryRideRepository
import com.example.indriveclone.data.repository.RideRepository
import com.example.indriveclone.data.settings.SettingsRepository
import com.example.indriveclone.domain.demo.DemoOfferSource
import com.example.indriveclone.domain.demo.MockDriverFleet
import com.example.indriveclone.domain.route.GeocodingService
import com.example.indriveclone.domain.route.NominatimGeocodingService
import com.example.indriveclone.domain.route.RoutePlanner
import com.example.indriveclone.domain.route.RouteServiceFactory

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_settings")

/**
 * Hand-rolled dependency container — the whole app has one wiring point, which is exactly where a real
 * backend implementation of [RideRepository] would be swapped in:
 *
 * ```kotlin
 * val rideRepository: RideRepository = RemoteRideRepository(api, socket)   // instead of in-memory
 * ```
 *
 * (If the app grows further, this is the natural place to introduce Hilt.)
 */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)

    val rideRepository: RideRepository = InMemoryRideRepository()

    val locationProvider = LocationProvider(appContext)

    val routeServiceFactory = RouteServiceFactory()

    val routePlanner = RoutePlanner(routeServiceFactory)

    val geocodingService: GeocodingService = NominatimGeocodingService()

    val demoFleet: DemoOfferSource = MockDriverFleet()

    /** `ORS_API_KEY` from local.properties (empty string when not configured). */
    val buildConfigOrsKey: String get() = BuildConfig.ORS_API_KEY

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun from(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context.applicationContext).also { instance = it }
            }
    }
}
