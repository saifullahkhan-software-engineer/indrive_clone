# inDrive-style ride-hailing demo — Requirements & Design

A **frontend-only** Android demo of an inDrive-style ride-hailing app. There is **no backend**: all
ride data lives in an in-memory repository behind a `Flow`-based interface, so a real backend can be
dropped in later without rewriting a single screen.

Status: **implemented** (this document is the contract the code follows).

---

## 1. Goals / non-goals

**Goals**

- Rider flow: pick pickup + destination on an OpenStreetMap map → real **road** polyline (OSRM/ORS) →
  distance & ETA → suggested fare (adjustable) → request ride → receive driver offers → accept one.
- Driver flow: see open requests → open a request → accept at the rider's fare or send a counter-offer.
- Works with **zero API keys** (public OSRM), degrades to an offline estimate if the network fails.
- Architecture that can later grow a real backend, live tracking and push notifications.

**Non-goals (out of scope for this phase)**

- Authentication, payments, real backend, live driver tracking, push notifications, ratings/bookings.

---

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM + unidirectional state, `StateFlow` |
| Navigation | Navigation Compose |
| Map | **osmdroid** (OpenStreetMap raster tiles) — **no map API key** |
| Networking | OkHttp + kotlinx.serialization |
| Settings storage | DataStore (Preferences) |
| Optional place search | Nominatim (OSM geocoding) |
| Tests | JUnit4 + kotlinx-coroutines-test |

Min SDK 24, target/compile SDK 35, JDK 17, AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21.

---

## 3. Architecture

```
ui/         Compose screens + composables (no business logic)
viewmodel/  Rider / Offers / Driver / Settings ViewModels — hold UiState, expose actions
domain/     Pure logic: RouteService(s) + provider chain + planner, FareCalculator, geocoding, demo fleet
data/       Models, RideRepository (+ in-memory impl), SettingsRepository (DataStore), HTTP, LocationProvider
```

Rules that keep the UI future-proof:

- Screens only talk to ViewModels; ViewModels only talk to `data`/`domain` interfaces.
- `RideRepository` is an **interface**; the demo uses `InMemoryRideRepository` (single source of truth
  shared by rider and driver screens, exposed as `Flow`s).
- Fare maths is a **pure function** (`FareCalculator`) — no Android dependencies, unit-tested.
- Map/location access is hidden behind `MapController` / `LocationProvider` so live tracking can be
  added later without touching screens.

### Data models

```kotlin
data class LatLng(val latitude: Double, val longitude: Double)

enum class RideStatus { SEARCHING, OFFERED, ACCEPTED }
enum class OfferStatus { PENDING, ACCEPTED, DECLINED }

data class RideRequest(
    id, pickup: LatLng, pickupLabel, destination: LatLng, destinationLabel,
    distanceMeters, durationSeconds, routePolyline: List<LatLng>, routeSource: RouteSource,
    offeredFare, suggestedFare, status: RideStatus, acceptedOfferId, acceptedDriverName, createdAtMillis,
)

data class DriverOffer(
    id, rideId, driverName, driverRating, carModel, carPlate,
    fare, etaMinutes, status: OfferStatus, isCounterOffer, createdAtMillis,
)

data class AdminSettings(
    baseFare = 100.0, perKmRate = 45.0, minimumFare = 50.0,
    maxFareMultiplier = 2.0, orsApiKey = "",
)
```

### Repository

```kotlin
interface RideRepository {
    val rideRequests: StateFlow<List<RideRequest>>
    val offers: StateFlow<List<DriverOffer>>
    fun rideRequest(id: String): Flow<RideRequest?>
    fun offersForRide(rideId: String): Flow<List<DriverOffer>>
    suspend fun createRideRequest(draft: RideDraft): RideRequest   // status = SEARCHING
    suspend fun placeOffer(draft: NewOffer): DriverOffer           // status -> OFFERED
    suspend fun acceptOffer(offerId: String): Boolean              // ride -> ACCEPTED, other offers DECLINED
    suspend fun declineOffer(offerId: String)
    suspend fun cancelRide(rideId: String)
}
```

---

## 4. Screens

| # | Screen | Content |
|---|---|---|
| 1 | **Role selection** | Two cards: *I'm a Rider* / *I'm a Driver*. Persisted in DataStore; changeable later from the overflow menu. |
| 2 | **Rider — map & request** | Map with current location (or default city if permission denied), tap-to-pick + draggable pickup/destination pins, optional search, road polyline, road distance (km) + trip time, "Route via OSRM/OpenRouteService/estimated" chip, suggested fare + slider/± fare adjuster, **Request Ride**. |
| 3 | **Rider — offers** | Live list of driver offers (name, rating, car, offered fare, mock ETA) with **Accept** / **Decline**; 2–3 mock offers arrive a few seconds after the request. |
| 4 | **Rider — driver on the way** | Accepted driver card + trip/route summary, cancel option. |
| 5 | **Driver — open requests** | Open ride requests: pickup, destination, road distance, trip time, rider's fare, plus my offer status if any. |
| 6 | **Driver — request detail** | Mini map with the stored route polyline, **Accept at rider's fare**, **Counter-offer** (same bounds as the rider's adjuster), list of my offers with pending/accepted/rejected state. |
| 7 | **Admin settings** | Reachable from the menu, no login. Base fare, per-km rate, minimum fare (50), max fare multiplier (2×), ORS API key (masked input + Clear, hint that empty = public OSRM). Persisted in DataStore. |

Shared: top bar with overflow menu → *Settings*, *Switch role*, *Reset demo data*.

---

## 5. Routing

```kotlin
interface RouteService { val source: RouteSource; suspend fun getRoute(from: LatLng, to: LatLng): RouteResult }
data class RouteResult(val distanceMeters: Double, val durationSeconds: Double, val polyline: List<LatLng>, val source: RouteSource)
enum class RouteSource { ORS, OSRM, HAVERSINE }
```

`val source` is the only addition to the interface in the brief; it makes the fallback chain
introspectable (UI label + tests). Each implementation fills in `RouteResult.source` for itself.

### Providers

1. **`OsrmRouteService`** (default, no key)
   `GET https://router.project-osrm.org/route/v1/driving/{lon},{lat};{lon},{lat}?overview=full&geometries=geojson`
   → `routes[0].distance` (m), `routes[0].duration` (s), `routes[0].geometry.coordinates` (GeoJSON `[lon,lat]`).
2. **`OpenRouteServiceRouteService`** (only with a key)
   `POST https://api.openrouteservice.org/v2/directions/driving-car/geojson`, header `Authorization: <key>`,
   body `{"coordinates": [[lon,lat],[lon,lat]]}`
   → `features[0].properties.summary.distance|duration`, `features[0].geometry.coordinates`.
3. **`HaversineRouteService`** (offline fallback)
   Straight-line distance × **1.3** road factor, duration at ~**30 km/h**, two-point polyline.

**Coordinate order:** both OSRM and ORS are **longitude first**. Conversion in/out of `LatLng(lat,lng)`
happens only inside the two services.

### Selection logic

`RouteServiceFactory.resolveOrsKey(settingsKey)`:

1. key saved in the in-app Settings screen (DataStore) → if non-blank, use it;
2. else `ORS_API_KEY` from `local.properties`, exposed through `BuildConfig.ORS_API_KEY`;
3. blank counts as "not provided".

Chain:

- key present → **ORS → OSRM → Haversine**
- no key → **OSRM → Haversine**

`RouteServiceProvider` walks the chain, falling through on any exception, timeout (**8 s**) or non-2xx
response, and reports the provider actually used through `RouteResult.source`. Timeouts are enforced
twice: OkHttp call timeouts and `withTimeout` per attempt. Cancellation is never swallowed.

### Request behaviour (`RoutePlanner` + ViewModel)

- Fetch only when **both** pickup and destination are set.
- **Debounce 500 ms** (map taps and marker drags), **cancel the in-flight request** when a new one
  starts (`mapLatest` + OkHttp call cancellation through `suspendCancellableCoroutine`).
- **Cache** the last result per `(from, to)` pair (small LRU). Changing the ORS key clears the cache.
- Loading indicator while routing; small label "Route via OpenRouteService / OSRM / estimated".

### Keys & safety

- Never hardcode a key. `local.properties` is git-ignored; `local.properties.example` is committed.
- Public OSRM is a **demo** endpoint (rate limits, no uptime guarantee) → self-host OSRM or use ORS for
  anything real. The ORS free tier has daily limits → hence debounce + cache + fallback.

---

## 6. Fare rules (pure, unit-tested)

```
suggestedFare = max(minimumFare, baseFare + perKmRate × routeKm)
maxFare       = max(minimumFare, suggestedFare × maxFareMultiplier)
bounds        = [minimumFare … maxFare], snapped to a 5 (or 10) step
```

- Slider + `−` / `+` buttons move in steps of 5 by default (10 configurable in code), always clamped
  to `bounds`.
- The suggested fare is shown as a reference label; the driver's counter-offer uses the **same**
  `FareCalculator.bounds(...)`.
- Bounds for the driver's counter-offer are derived from the ride's stored road distance, not re-routed.

---

## 7. Gradle / manifest / local.properties

**Permissions** (`AndroidManifest.xml`): `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`,
`ACCESS_COARSE_LOCATION`.

**`app/build.gradle.kts`**

```kotlin
buildFeatures { compose = true; buildConfig = true }

val orsApiKey = /* read ORS_API_KEY from ../local.properties, default "" */
buildConfigField("String", "ORS_API_KEY", "\"$orsApiKey\"")
```

**`local.properties`** (git-ignored, next to `settings.gradle.kts`)

```properties
sdk.dir=/path/to/Android/sdk
# optional — leave out to run on public OSRM only
ORS_API_KEY=your_openrouteservice_key
```

After changing it, rebuild (`./gradlew clean assembleDebug`) because `BuildConfig` is generated.

**osmdroid** needs a user agent — set once in `InDriveApplication`:
`Configuration.getInstance().userAgentValue = packageName`, with tile caches in the app cache dir.

---

## 8. Tests

| Test | Covers |
|---|---|
| `FareCalculatorTest` | min-fare clamping, suggested fare, bounds, max multiplier, grid/step snapping, driver counter-offer clamping |
| `RouteServiceProviderTest` | with key: ORS fails → OSRM used; without key: OSRM used; timeout falls through; dead chain → Haversine; cancelling the caller is *not* treated as a provider failure; key resolution order |
| `RouteServiceParsingTest` | OSRM/ORS request URL + JSON body, **longitude-first** order, non-2xx throws, empty/invalid geometry throws, Haversine ×1.3 @ 30 km/h |
| `RoutePlannerTest` | burst of marker drags → one request, nothing routed until both pins exist, cache hit for the same pair, cache dropped when the key changes, offline fallback with a dead network |
| `InMemoryRideRepositoryTest` | SEARCHING → OFFERED → ACCEPTED, other offers declined, last offer declined → SEARCHING again, cancel removes offers, reset clears everything |
| `MockDriverFleetTest` | 2–3 distinct drivers, every offer inside the rider's window, first driver takes the rider's fare |

Network isolation: `RouteBackend` (in `domain/route`) is the HTTP seam — the route services take it as a
constructor parameter, so tests inject a fake and never touch the network.

`./gradlew testDebugUnitTest`

---

## 9. Where to plug things in later

**Real backend** — implement `RideRepository` over Retrofit/WebSocket and swap the instance in
`ServiceLocator`. Nothing else changes: ViewModels depend on the interface, and the repository already
returns cold/hot `Flow`s, so a socket-backed implementation can emit updates.

**Self-hosted OSRM** — `RouteServiceFactory(osrmBaseUrl = "https://osrm.yourdomain.com")`
(or `http://10.0.2.2:5000` for a locally running Docker OSRM). The demo default is the public server;
only that one string changes.

**Live driver location + ETA** — the driver app is expected to push its position to the backend; the
rider screen adds a `MyLocationNewOverlay`/marker fed by `LocationProvider.observeLocation()` (rename
the current one-shot API to a `Flow`). ETA = `RouteService.getRoute(driverPosition, pickup)` reusing the
same `RoutePlanner` (same debounce/cache/fallback chain). `MapController.updateDriverMarker(...)`
already isolates all osmdroid specifics, so the screen change is a handful of lines.

**Push notifications / background tracking** — add FCM + a foreground service; `LocationProvider` is the
only place that needs the new plumbing.

---

## 10. Implementation notes / deviations

- `RouteService` gained a read-only `val source: RouteSource` property (see §5).
- `RouteBackend` is a small interface (GET/POST-JSON returning code + body) that the two HTTP services
  take as a constructor parameter. It exists for two reasons: unit tests inject a fake instead of
  hitting public servers, and a future self-hosted OSRM/ORS setup with custom TLS or retries is wired
  in one place.
- Location uses the platform `LocationManager` (no Play Services dependency). One-shot fix only; the
  wrapper is the single place to change for continuous updates.
- Reverse geocoding of tapped pins is best-effort (Nominatim, rate-limited to 1 req/s) and *never*
  blocks the core tap-to-pick flow; labels fall back to coordinates.
- UI copy lives inline in composables for demo readability; only `app_name` is in `strings.xml`.
