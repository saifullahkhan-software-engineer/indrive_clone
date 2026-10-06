package com.example.indriveclone

import android.app.Application
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration

/**
 * Configures osmdroid once, before any map is inflated.
 *
 * osmdroid requires a user agent (tile servers reject requests without one — the app's package name is
 * the recommended value) and a writable base path for its tile cache.
 */
class InDriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val configuration = Configuration.getInstance()
        configuration.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        configuration.userAgentValue = packageName
        configuration.osmdroidBasePath = cacheDir
        configuration.osmdroidTileCache = cacheDir.resolve(TILE_CACHE_DIR)
    }

    private companion object {
        const val TILE_CACHE_DIR = "osmdroid"
    }
}
