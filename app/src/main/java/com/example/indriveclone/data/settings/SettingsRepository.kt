package com.example.indriveclone.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the admin fare configuration, the optional ORS key and the chosen role. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AdminSettings> = dataStore.data.map { preferences ->
        val defaults = AdminSettings()
        AdminSettings(
            baseFare = preferences[KEY_BASE_FARE] ?: defaults.baseFare,
            perKmRate = preferences[KEY_PER_KM_RATE] ?: defaults.perKmRate,
            minimumFare = preferences[KEY_MINIMUM_FARE] ?: defaults.minimumFare,
            maxFareMultiplier = preferences[KEY_MAX_FARE_MULTIPLIER] ?: defaults.maxFareMultiplier,
            fareStep = preferences[KEY_FARE_STEP] ?: defaults.fareStep,
            orsApiKey = preferences[KEY_ORS_API_KEY].orEmpty(),
        )
    }

    val role: Flow<UserRole?> = dataStore.data.map { preferences ->
        preferences[KEY_ROLE]?.let { stored ->
            UserRole.entries.firstOrNull { it.name == stored }
        }
    }

    suspend fun updateSettings(transform: (AdminSettings) -> AdminSettings) {
        dataStore.edit { preferences ->
            val current = AdminSettings(
                baseFare = preferences[KEY_BASE_FARE] ?: AdminSettings().baseFare,
                perKmRate = preferences[KEY_PER_KM_RATE] ?: AdminSettings().perKmRate,
                minimumFare = preferences[KEY_MINIMUM_FARE] ?: AdminSettings().minimumFare,
                maxFareMultiplier = preferences[KEY_MAX_FARE_MULTIPLIER] ?: AdminSettings().maxFareMultiplier,
                fareStep = preferences[KEY_FARE_STEP] ?: AdminSettings().fareStep,
                orsApiKey = preferences[KEY_ORS_API_KEY].orEmpty(),
            )
            val updated = transform(current)
            preferences[KEY_BASE_FARE] = updated.baseFare
            preferences[KEY_PER_KM_RATE] = updated.perKmRate
            preferences[KEY_MINIMUM_FARE] = updated.minimumFare
            preferences[KEY_MAX_FARE_MULTIPLIER] = updated.maxFareMultiplier
            preferences[KEY_FARE_STEP] = updated.fareStep
            preferences[KEY_ORS_API_KEY] = updated.orsApiKey.trim()
        }
    }

    suspend fun setRole(role: UserRole) {
        dataStore.edit { preferences -> preferences[KEY_ROLE] = role.name }
    }

    companion object {
        private val KEY_BASE_FARE = doublePreferencesKey("base_fare")
        private val KEY_PER_KM_RATE = doublePreferencesKey("per_km_rate")
        private val KEY_MINIMUM_FARE = doublePreferencesKey("minimum_fare")
        private val KEY_MAX_FARE_MULTIPLIER = doublePreferencesKey("max_fare_multiplier")
        private val KEY_FARE_STEP = doublePreferencesKey("fare_step")
        private val KEY_ORS_API_KEY = stringPreferencesKey("ors_api_key")
        private val KEY_ROLE = stringPreferencesKey("user_role")
    }
}
