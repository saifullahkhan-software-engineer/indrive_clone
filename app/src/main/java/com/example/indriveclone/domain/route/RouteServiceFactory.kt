package com.example.indriveclone.domain.route

/**
 * Builds the provider chain from the resolved OpenRouteService key.
 *
 * Key resolution order (blank counts as "not provided"):
 *  1. key saved in the in-app Settings screen (DataStore) — [settingsKey];
 *  2. `ORS_API_KEY` from local.properties, exposed as `BuildConfig.ORS_API_KEY` — [buildConfigKey];
 *  3. nothing.
 *
 * Chain: key present -> **ORS -> OSRM -> Haversine**; no key -> **OSRM -> Haversine**.
 * No key is ever hardcoded anywhere.
 */
class RouteServiceFactory(
    private val osrmBaseUrl: String = OsrmRouteService.DEFAULT_BASE_URL,
    private val orsBaseUrl: String = OpenRouteServiceRouteService.DEFAULT_BASE_URL,
    private val backend: RouteBackend = OkHttpRouteBackend(),
) {

    fun create(settingsKey: String?, buildConfigKey: String?): RouteServiceProvider =
        createForResolvedKey(resolveOrsKey(settingsKey, buildConfigKey))

    fun createForResolvedKey(resolvedKey: String?): RouteServiceProvider {
        val services = buildList {
            if (resolvedKey != null) {
                add(OpenRouteServiceRouteService(apiKey = resolvedKey, baseUrl = orsBaseUrl, backend = backend))
            }
            add(OsrmRouteService(baseUrl = osrmBaseUrl, backend = backend))
            add(HaversineRouteService())
        }
        return RouteServiceProvider(services)
    }

    /** Settings screen wins over local.properties; blank means "not provided". */
    fun resolveOrsKey(settingsKey: String?, buildConfigKey: String?): String? =
        settingsKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: buildConfigKey?.trim()?.takeIf { it.isNotEmpty() }
}
