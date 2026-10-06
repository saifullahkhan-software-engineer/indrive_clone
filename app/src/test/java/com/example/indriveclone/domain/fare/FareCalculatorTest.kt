package com.example.indriveclone.domain.fare

import com.example.indriveclone.data.model.AdminSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fare rules are pure maths, so they get exhaustive tests — both screens depend on them. */
class FareCalculatorTest {

    private val defaults = AdminSettings() // base 100, per km 45, min 50, multiplier 2

    @Test
    fun `suggested fare is base plus per-km times road distance`() {
        // 2 km: 100 + 45 * 2 = 190 -> aligned up to the 5-unit grid.
        assertEquals(190.0, FareCalculator.suggestedFare(2_000.0, defaults), 0.001)
    }

    @Test
    fun `suggested fare is clamped up to the minimum fare`() {
        val cheapRide = AdminSettings(baseFare = 20.0, perKmRate = 10.0, minimumFare = 50.0, maxFareMultiplier = 2.0)
        // 200 m would be 22 -> the admin minimum fare wins.
        assertEquals(50.0, FareCalculator.suggestedFare(200.0, cheapRide), 0.001)
    }

    @Test
    fun `bounds maximum is the suggested fare times the multiplier`() {
        val bounds = FareCalculator.bounds(distanceMeters = 2_000.0, settings = defaults)
        assertEquals(190.0, bounds.suggested, 0.001)
        assertEquals(50.0, bounds.minimum, 0.001)
        assertEquals(380.0, bounds.maximum, 0.001)
    }

    @Test
    fun `a multiplier below one never drops the ceiling under the suggestion`() {
        val settings = defaults.copy(maxFareMultiplier = 0.5)
        val bounds = FareCalculator.bounds(2_000.0, settings)
        assertTrue(bounds.maximum >= bounds.suggested)
    }

    @Test
    fun `bounds stay aligned to the adjuster step`() {
        val settings = AdminSettings(baseFare = 103.0, perKmRate = 41.0, minimumFare = 50.0, maxFareMultiplier = 2.0)
        val bounds = FareCalculator.bounds(distanceMeters = 3_170.0, settings = settings, step = 5.0)
        assertEquals(0.0, (bounds.maximum - bounds.minimum) % bounds.step, 1e-9)
        assertEquals(0.0, bounds.suggested % bounds.step, 1e-9)
        // Material's Slider expects the stops *between* the endpoints.
        assertEquals((bounds.maximum - bounds.minimum).toInt() / bounds.step.toInt() - 1, bounds.sliderSteps)
    }

    @Test
    fun `clamping keeps fares inside the window`() {
        val bounds = FareCalculator.bounds(2_000.0, defaults)
        assertEquals(bounds.minimum, FareCalculator.clampToBounds(10.0, bounds), 0.001)
        assertEquals(bounds.maximum, FareCalculator.clampToBounds(9_999.0, bounds), 0.001)
        assertEquals(185.0, FareCalculator.clampToBounds(185.0, bounds), 0.001)
    }

    @Test
    fun `initial fare snaps the suggestion onto the grid`() {
        val bounds = FareBounds(minimum = 50.0, maximum = 200.0, suggested = 187.0, step = 5.0)
        assertEquals(185.0, FareCalculator.initialFare(bounds), 0.001)
    }

    @Test
    fun `step down stops at the minimum and step up stops at the maximum`() {
        val bounds = FareBounds(minimum = 50.0, maximum = 100.0, suggested = 50.0, step = 5.0)
        assertEquals(50.0, FareCalculator.stepFare(50.0, -1, bounds), 0.001)
        assertEquals(55.0, FareCalculator.stepFare(50.0, 1, bounds), 0.001)
        assertEquals(100.0, FareCalculator.stepFare(100.0, 1, bounds), 0.001)
        assertEquals(95.0, FareCalculator.stepFare(100.0, -1, bounds), 0.001)
    }

    @Test
    fun `coarse step of ten is supported`() {
        val bounds = FareCalculator.bounds(7_000.0, defaults, step = FareCalculator.COARSE_STEP)
        assertEquals(10.0, bounds.step, 0.001)
        assertEquals(0.0, (bounds.maximum - bounds.minimum) % 10.0, 1e-9)
    }

    @Test
    fun `driver counter-offer uses the same window as the rider`() {
        val rideSuggestion = 190.0
        val bounds = FareCalculator.boundsFor(rideSuggestion, defaults)
        assertEquals(190.0, FareCalculator.driverCounterOffer(190.0, bounds)!!, 0.001)
        assertEquals(bounds.maximum, FareCalculator.driverCounterOffer(5_000.0, bounds)!!, 0.001)
        assertEquals(bounds.minimum, FareCalculator.driverCounterOffer(1.0, bounds)!!, 0.001)
        assertNull(FareCalculator.driverCounterOffer(Double.NaN, bounds))
        assertNull(FareCalculator.driverCounterOffer(-10.0, bounds))
    }

    @Test
    fun `stored suggestion drives the window so later settings edits do not shrink it`() {
        val bounds = FareCalculator.boundsFor(suggestedFare = 210.0, settings = defaults)
        assertEquals(210.0, bounds.suggested, 0.001)
        assertEquals(420.0, bounds.maximum, 0.001)
    }
}
