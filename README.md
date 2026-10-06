# inDrive Clone — frontend-only ride-hailing demo (Android)

An inDrive-style ride-hailing demo written in **Kotlin + Jetpack Compose (Material 3)** with an
**OpenStreetMap** map (osmdroid — **no map API key needed**). There is **no backend**: rides and offers
live in an in-memory repository exposed as `Flow`s, so rider mode and driver mode see the same data.

The full requirements, architecture and design decisions are in **[REQUIREMENTS.md](REQUIREMENTS.md)**.

---

## What works

| Flow | Behaviour |
|---|---|
| **Role selection** | "I'm a Rider" / "I'm a Driver" cards; changeable any time from the overflow menu. |
| **Rider** | Map with the current location (or a default city when permission is denied) → tap the map (or search with Nominatim) to set pickup/destination → draggable pins → debounced road route → road distance + trip time + "Route via …" label → suggested fare with a slider/± adjuster → **Request ride**. |
| **Offers** | 2–3 mock drivers answer a few seconds later; each card shows name, rating, car, fare and mock ETA. Accept → "driver is on the way"; Decline → the offer is marked rejected. |
| **Driver** | List of open requests (pickup, destination, road distance, trip time, rider's fare, own bid status) → detail with a mini map of the **stored** polyline → **Accept at rider's fare** or **counter-offer** inside the same fare window. |
| **Admin settings** | Base fare, per-km rate, minimum fare (50), max fare multiplier (2×), fare step (5/10) and an optional **OpenRouteService API key** (masked, with Clear), with a live fare preview. Persisted with DataStore; no login. |

Routing falls back automatically: **ORS (only with a key) → OSRM → Haversine estimate**, and the
provider actually used is shown on the fare panel. The demo therefore works completely offline of any
API key, and never breaks when a routing server is down.

---

## Project structure

```
indrive_clone/
├── REQUIREMENTS.md                     ← the spec this code implements
├── local.properties.example            ← copy to local.properties (git-ignored)
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradle/libs.versions.toml           ← version catalog (single place for versions)
└── app/
    ├── build.gradle.kts                ← reads ORS_API_KEY from local.properties → BuildConfig
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml      ← INTERNET, ACCESS_NETWORK_STATE, FINE/COARSE_LOCATION
        │   ├── res/                     ← theme, strings, marker vectors
        │   └── java/com/example/indriveclone/
        │       ├── InDriveApplication.kt        osmdroid user agent + tile cache (app package name)
        │       ├── MainActivity.kt              edge-to-edge Activity, hosts AppRoot
        │       ├── ServiceLocator.kt            the one place the app is wired together
        │       ├── data/
        │       │   ├── model/Models.kt          RideRequest, DriverOffer, AdminSettings, LatLng,
        │       │   │                            RouteResult, RideStatus, OfferStatus, RouteSource
        │       │   ├── network/Http.kt          OkHttp client + cancellable GET/POST helpers
        │       │   ├── repository/RideRepository.kt          interface (backend seam)
        │       │   ├── repository/InMemoryRideRepository.kt  demo implementation, StateFlows
        │       │   ├── settings/SettingsRepository.kt        DataStore (fares, ORS key, role)
        │       │   └── location/LocationProvider.kt          LocationManager wrapper (live-tracking seam)
        │       ├── domain/
        │       │   ├── fare/FareCalculator.kt        pure fare maths + FareBounds
        │       │   ├── route/RouteService.kt         the interface from the spec
        │       │   ├── route/OsrmRouteService.kt             default, key-less
        │       │   ├── route/OpenRouteServiceRouteService.kt used only with a key
        │       │   ├── route/HaversineRouteService.kt        offline fallback (×1.3, 30 km/h)
        │       │   ├── route/RouteServiceProvider.kt         the fallback chain + 8 s timeouts
        │       │   ├── route/RouteServiceFactory.kt          key resolution → chain
        │       │   ├── route/RoutePlanner.kt                 debounce + cancel + cache
        │       │   ├── route/GeocodingService.kt             Nominatim search/reverse (rate-limited)
        │       │   ├── route/RouteBackend.kt                 HTTP seam for the route services
        │       │   └── demo/                                 MockDriverFleet, DemoDriver
        │       ├── viewmodel/                AppViewModel, RiderViewModel, OffersViewModel,
        │       │                             ActiveRideViewModel, DriverViewModel,
        │       │                             DriverRideDetailViewModel, SettingsViewModel
        │       └── ui/
        │           ├── AppRoot.kt, navigation/AppNavHost.kt
        │           ├── map/MapCanvas.kt, map/MapController.kt   all osmdroid usage lives here
        │           ├── components/            AppTopBar, FareAdjuster, cards, chips
        │           ├── screens/               RoleSelection, RiderMap, Offers, DriverOnTheWay,
        │           │                          DriverHome, DriverRequestDetail, Settings
        │           ├── theme/Theme.kt, util/Format.kt
        └── test/java/com/example/indriveclone/
            ├── TestFixtures.kt                            shared ride/offer fixtures
            ├── domain/route/Fakes.kt                      FakeRouteBackend / FakeRouteService
            ├── domain/fare/FareCalculatorTest.kt          min-fare clamping, bounds, steps
            ├── domain/route/RouteServiceProviderTest.kt   ORS→OSRM, OSRM, Haversine, timeout, cancel
            ├── domain/route/RouteServiceParsingTest.kt    URL/body format + lon-lat order
            ├── domain/route/RoutePlannerTest.kt           debounce, cache, key change
            ├── domain/demo/MockDriverFleetTest.kt         offers stay inside the rider's window
            └── data/repository/InMemoryRideRepositoryTest.kt  SEARCHING→OFFERED→ACCEPTED
```

---

## Build & run

**Requirements:** Android Studio Ladybug (or newer), JDK 17, Android SDK 35.

**Open the folder in Android Studio and let it sync** — that is the fastest path (it generates the
Gradle wrapper and downloads the distribution from `gradle/wrapper/gradle-wrapper.properties`).

Command line:

```bash
# 1. local.properties (git-ignored) — required for the SDK path, optional ORS key
cp local.properties.example local.properties
$EDITOR local.properties        # sdk.dir=/…/Android/sdk  [+ ORS_API_KEY=…]

# 2. build & install (./gradlew appears after the first Android Studio sync)
./gradlew assembleDebug
./gradlew installDebug

# 3. unit tests (pure logic, no emulator needed)
./gradlew testDebugUnitTest
```

> The binary `gradle-wrapper.jar` is not committed. Android Studio creates it on first sync; with a
> local Gradle 8.9+ you can also run `gradle wrapper --gradle-version 8.9` once.

Notes

- `ORS_API_KEY` is optional. Without it the app uses the **public OSRM demo server**; with it the chain
  becomes ORS → OSRM → offline estimate. A key can also be pasted into the in-app **Settings** screen,
  which takes precedence over `local.properties`.
- **Never commit `local.properties`** — it is in `.gitignore`. `BuildConfig` is generated, so rebuild
  after changing the key.
- Location permission is optional: deny it and the app falls back to a default city; you can drag the
  pins anywhere.
- The public OSRM server is **for demos only** (rate limits, no uptime guarantee) and the ORS free tier
  is daily-limited — which is why route requests are debounced (500 ms) and cached per pin pair.

---

## Where to plug things in later

**Real backend** — implement `RideRepository` over Retrofit + WebSocket and swap the instance in
`ServiceLocator`:

```kotlin
val rideRepository: RideRepository = RemoteRideRepository(api, socket)   // instead of InMemoryRideRepository()
```

Screens and ViewModels never touch the implementation, and the repository already returns `Flow`s, so
a socket-backed version can push live updates through the same API.

**Self-hosted OSRM** — one string:

```kotlin
RouteServiceFactory(osrmBaseUrl = "https://osrm.yourdomain.com")   // or http://10.0.2.2:5000 locally
```

**Live driver location + ETA** — the seams already exist:

1. the driver app publishes its position to the backend (`LocationProvider` is the only class that
   needs continuous updates: turn `currentLocation()` into `observeLocation(): Flow<LatLng>`);
2. the rider screen draws it via a new `MapController.setDriverPosition(LatLng)`;
3. driver-to-pickup ETA reuses the existing routing stack:
   `routePlanner.plan(driverPosition, ride.pickup)` (same debounce/cache/fallback chain, already
   cancellable), or `RouteService.getRoute(...)` directly for a one-off.

**Push notifications / background tracking** — add FCM and a foreground service; only
`LocationProvider` and the ViewModel wiring change.

---

## Deliberate shortcuts (demo scope)

- In-memory data only: everything resets when the process dies (there is a "Reset demo data" menu item).
- One demo driver identity (`DemoDriver`), no login, no payments, no chat, no ratings.
- Mock driver offers are generated locally with a few seconds of delay.
- Reverse geocoding of tapped pins is best-effort; the label falls back to coordinates and the core
  tap-to-pick flow never waits for it.
