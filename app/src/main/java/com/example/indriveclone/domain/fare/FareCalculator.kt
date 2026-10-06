package com.example.indriveclone.domain.fare

import com.example.indriveclone.data.model.AdminSettings
import kotlin.math.ceil
import kotlin.math.max

/**
 * The fare window a rider (or a counter-offering driver) may pick from.
 *
 * [minimum] is the admin minimum fare, [maximum] is the suggested fare times the admin multiplier
 * (never below [minimum]), and both are aligned to [step] so the slider and the +/- buttons land on
 * exact values.
 */
data class FareBounds(
    val minimum: Double,
    val maximum: Double,
    val suggested: Double,
    val step: Double,
) {
    /**
     * The value Material3's `Slider(..., steps = ...)` expects: the number of discrete stops
     * *between* the two endpoints.
     */
    val sliderSteps: Int
        get() = (((maximum - minimum) / step).toInt() - 1).coerceAtLeast(0)

    /** Number of `step`-sized moves from [minimum] to [maximum]. */
    val availableSteps: Int
        get() = ((maximum - minimum) / step).toInt().coerceAtLeast(0)

    init {
        require(step > 0) { "Fare step must be positive" }
        require(maximum >= minimum) { "Fare maximum must not be below the minimum" }
    }
}

/**
 * **Pure** fare maths — no Android, no coroutines, no I/O, so it is trivially unit-testable and can be
 * reused by both the rider's adjuster and the driver's counter-offer (they must agree).
 *
 * ```
 * suggestedFare = max(minimumFare, baseFare + perKmRate * roadDistanceKm)
 * maximumFare   = max(minimumFare, suggestedFare * maxFareMultiplier)
 * ```
 */
object FareCalculator {

    /** Default adjuster granularity ("step = 5 or 10"). */
    const val DEFAULT_STEP: Double = 5.0

    /** Coarser granularity alternative, exposed for the settings screen / future toggle. */
    const val COARSE_STEP: Double = 10.0

    fun roadDistanceKm(distanceMeters: Double): Double =
        (distanceMeters.coerceAtLeast(0.0)) / 1_000.0

    /** Suggested fare in the app's currency, rounded to a whole unit for display. */
    fun suggestedFare(
        distanceMeters: Double,
        baseFare: Double,
        perKmRate: Double,
        minimumFare: Double,
    ): Double {
        val raw = baseFare + perKmRate * roadDistanceKm(distanceMeters)
        return max(minimumFare, raw).roundToMoney()
    }

    /** Suggested fare for a ride, aligned up to the adjuster's grid. */
    fun suggestedFare(distanceMeters: Double, settings: AdminSettings, step: Double = DEFAULT_STEP): Double =
        alignUpToStep(
            value = suggestedFare(
                distanceMeters = distanceMeters,
                baseFare = settings.baseFare,
                perKmRate = settings.perKmRate,
                minimumFare = settings.minimumFare,
            ),
            step = step,
        )

    /**
     * Fare window derived from a **stored** suggestion. Driver screens use this so a ride keeps the
     * window it was created with even if the admin changes rates afterwards.
     */
    fun boundsFor(suggestedFare: Double, settings: AdminSettings, step: Double = DEFAULT_STEP): FareBounds {
        val minimum = settings.minimumFare.coerceAtLeast(0.0)
        val rawMaximum = max(minimum, suggestedFare * settings.maxFareMultiplier.coerceAtLeast(1.0))
        val maximum = minimum + ceil((rawMaximum - minimum) / step).coerceAtLeast(0.0) * step
        return FareBounds(
            minimum = minimum,
            maximum = maximum,
            suggested = suggestedFare.coerceIn(minimum, maximum),
            step = step,
        )
    }

    fun bounds(distanceMeters: Double, settings: AdminSettings, step: Double = DEFAULT_STEP): FareBounds =
        boundsFor(suggestedFare(distanceMeters, settings, step), settings, step)

    /** Clamps any fare into the window. The single place where the min-fare rule is enforced. */
    fun clampToBounds(fare: Double, bounds: FareBounds): Double =
        fare.coerceIn(bounds.minimum, bounds.maximum).roundToMoney()

    /** Where the rider's slider starts: the suggestion, snapped to the window's grid. */
    fun initialFare(bounds: FareBounds): Double =
        clampToBounds(snapToStep(bounds.suggested, bounds), bounds)

    /**
     * Moves one step up or down; `direction` is +1 (more) or -1 (less). Values are clamped, so holding
     * "+" at the ceiling is a no-op instead of an invalid fare.
     */
    fun stepFare(currentFare: Double, direction: Int, bounds: FareBounds): Double =
        clampToBounds(currentFare + direction * bounds.step, bounds)

    /** Snaps to the grid of the window, keeping [bounds] minimum/maximum reachable exactly. */
    fun snapToStep(fare: Double, bounds: FareBounds): Double {
        val offset = fare - bounds.minimum
        val snapped = bounds.minimum + Math.round(offset / bounds.step) * bounds.step
        return snapped.coerceIn(bounds.minimum, bounds.maximum)
    }

    /**
     * Validates a driver's counter-offer against the very same window the rider saw. Returns null when
     * the fare is not a usable number.
     */
    fun driverCounterOffer(fare: Double, bounds: FareBounds): Double? =
        fare.takeIf { it.isFinite() && it > 0 }?.let { clampToBounds(it, bounds) }

    /** Rounds up to the next multiple of [step] so suggestions land on the adjuster's grid. */
    private fun alignUpToStep(value: Double, step: Double): Double =
        ceil(value / step) * step

    private fun Double.roundToMoney(): Double = Math.round(this).toDouble()
}
