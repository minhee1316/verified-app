package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실차가 각 신호를 순서대로 보내는 동안 미수신 기본값이 운행 기록에 섞이지 않아야 한다. */
class DrivingSignalReadinessTest {
    private fun engine(real: Boolean = true) = VerdictEngine(
        Settings(extraModules = setOf(Module.TRIP), businessMode = false)
    ).apply { onCar = real }

    private fun seen(vararg names: String) = names.associateWith { 1_000L }
    private val readings = seen("gear", "speed", "odometer", "soc")
    private fun moving(odo: Double = 14_000.0, soc: Int = 80) = Signals(
        gear = Gear.D, speedKmh = 20f, odometerKm = odo, socPct = soc, seenAt = readings
    )

    @Test fun gearBeforeOdometer_doesNotCreateZeroBaselineOrFivePhantomKilometres() {
        val e = engine()
        val blank = Signals(odometerKm = 0.0, socPct = 0)
        e.update(blank, 0)
        e.update(blank.copy(gear = Gear.D, seenAt = seen("gear")), 1_000)
        e.update(blank.copy(gear = Gear.D, speedKmh = 20f, seenAt = seen("gear", "speed")), 2_000)
        assertNull(e.state.trip.open)
        assertTrue(e.state.stats.isEmpty())

        e.update(moving(), 3_000)
        assertEquals(14_000.0, e.state.trip.open!!.startOdo, 0.001)
        assertTrue(e.state.stats.isEmpty())
        e.update(moving(14_000.4), 4_000)
        assertEquals(0.4, e.state.stats.sumOf { it.km }, 0.001)
        e.update(moving(14_000.8).copy(gear = Gear.P, speedKmh = 0f), 10_000, shutdown = true)
        assertEquals(0.8, e.state.trip.logs.single().distKm, 0.001)
        assertEquals(0.8, e.state.stats.sumOf { it.km }, 0.001)
    }

    @Test fun missingRequiredMotionReading_neverCreatesTripOrDistance() {
        for (missing in listOf("gear", "speed", "odometer")) {
            val e = engine()
            val keys = readings - missing
            e.update(moving().copy(gear = Gear.P, speedKmh = 0f, seenAt = keys), 0)
            e.update(moving(14_001.0).copy(seenAt = keys), 5_000)
            e.update(moving(14_002.0).copy(seenAt = keys), 10_000)
            assertNull("Missing $missing must not open a trip", e.state.trip.open)
            assertEquals("Missing $missing must not accumulate distance", 0.0, e.state.stats.sumOf { it.km }, 0.001)
        }
    }

    @Test fun firstCompleteMovingSample_startsAtObservedOdometer() {
        val e = engine()
        e.update(moving(14_025.0), 1_000)
        assertEquals(14_025.0, e.state.trip.open!!.startOdo, 0.001)
        assertTrue(e.state.stats.isEmpty())
    }

    @Test fun missingEndOdometer_preservesOpenTripUntilActualReadingArrives() {
        val e = engine()
        e.update(moving(), 0)
        val parked = moving(14_003.0).copy(gear = Gear.P, speedKmh = 0f)
        e.update(parked.copy(odometerKm = 0.0, seenAt = readings - "odometer"), 5_000, shutdown = true)
        assertNotNull(e.state.trip.open)
        assertTrue(e.state.trip.logs.isEmpty())
        e.update(parked, 6_000, shutdown = true)
        assertNull(e.state.trip.open)
        assertEquals(3.0, e.state.trip.logs.single().distKm, 0.001)
    }

    @Test fun firstSocAndSocAfterMissingSample_areBaselinesNotEnergyConsumption() {
        val e = engine()
        e.update(moving().copy(seenAt = readings - "soc"), 0)
        e.update(moving(14_000.2, 42), 5_000)
        assertEquals(0, e.state.stats.sumOf { it.socUsed })
        e.update(moving(14_000.4, 41), 10_000)
        assertEquals(1, e.state.stats.sumOf { it.socUsed })
        e.update(moving(14_000.6, 0).copy(seenAt = readings - "soc"), 15_000)
        e.update(moving(14_000.8, 39), 20_000)
        assertEquals(1, e.state.stats.sumOf { it.socUsed })
        e.update(moving(14_001.0, 38), 25_000)
        assertEquals(2, e.state.stats.sumOf { it.socUsed })
        assertEquals(1.0, e.state.stats.sumOf { it.km }, 0.001)
    }

    @Test fun initialSpeedDoesNotBecomeHarshAcceleration() {
        val e = engine()
        e.update(moving().copy(speedKmh = 0f, seenAt = readings - "speed"), 0)
        e.update(moving(), 1_000)
        assertEquals(0, e.state.stats.sumOf { it.accels })
        assertEquals(0, e.state.stats.sumOf { it.brakes })
    }

    @Test fun invalidReadings_doNotBecomeTripBaselinesOrStatistics() {
        for (invalid in listOf(
            moving(Double.NaN), moving(Double.POSITIVE_INFINITY), moving(-1.0),
            moving().copy(speedKmh = Float.NaN), moving().copy(speedKmh = -1f)
        )) {
            val e = engine()
            e.update(invalid, 0)
            e.update(invalid, 5_000)
            assertNull(e.state.trip.open)
            assertTrue(e.state.stats.isEmpty())
            e.update(moving(), 10_000)
            assertEquals(14_000.0, e.state.trip.open!!.startOdo, 0.001)
            assertTrue(e.state.stats.isEmpty())
        }
        val e = engine()
        e.update(moving(soc = 80), 0)
        e.update(moving(soc = -1), 5_000)
        e.update(moving(soc = 40), 10_000)
        assertTrue(e.state.stats.isEmpty())
        e.update(moving(soc = 39), 15_000)
        assertEquals(1, e.state.stats.single().socUsed)
    }

    @Test fun demonstrationWithoutSeenAt_stillRecordsTripAndDailyUse() {
        val e = engine(real = false)
        val parked = moving(100.0).copy(gear = Gear.P, speedKmh = 0f, seenAt = emptyMap())
        e.update(parked, 0)
        e.update(parked.copy(gear = Gear.D, speedKmh = 20f), 5_000)
        e.update(parked.copy(odometerKm = 101.0, socPct = 79), 10_000, shutdown = true)
        assertEquals(1.0, e.state.trip.logs.single().distKm, 0.001)
        assertEquals(1.0, e.state.stats.sumOf { it.km }, 0.001)
        assertEquals(1, e.state.stats.sumOf { it.socUsed })
    }
}
