package com.verified.incar.core

import org.junit.Assert.*
import org.junit.Test

class HandoverPrivacyAndRoamTest {
    private fun engine() = VerdictEngine(Settings(personas = setOf(Persona.NIGHT_OUT),
        carModel = CarModel.GRANDEUR, flags = Flag.entries.toSet(), consents = Consent.entries.toSet()))

    @Test fun valetHistoryDoesNotRetainDrivingStyleStatistics() {
        val e = engine()
        val start = Signals(gear = Gear.P, speedKmh = 0f)
        e.startValet()
        e.confirmValetConsent(start, 0)
        e.update(start.copy(gear = Gear.D, speedKmh = 140f, place = Place.OTHER), 1_000)
        e.update(start.copy(place = Place.OTHER, ignitionOn = true), 2_000)
        e.update(start.copy(place = Place.OTHER, ignitionOn = true), 400_000)
        e.manualValetReturn(start, 401_000)
        val h = e.state.history.single()
        assertEquals(0f, h.maxSpeed)
        assertEquals(0, h.brakes)
        assertFalse(h.speedKnown)
        assertTrue(h.changes.none { "정차 중 시동" in it })
        assertEquals(0, e.state.stats.sumOf { it.brakes })
        assertTrue(MyHyundai.historyCsv(listOf(h)).contains("대리 기록 제외"))
    }

    @Test fun gpsJumpWhileStationaryCannotCertifyWorkshopDeparture() {
        val e = engine()
        val start = Signals(lat = 37.5, lon = 127.0, ignitionOn = true)
        e.update(start, 0)
        e.startLend(start, 0, HandKind.SERVICE)
        val fx = e.update(start.copy(lat = 37.52), 1_000)
        assertFalse(e.state.lend.roamWarned)
        assertTrue(fx.filterIsInstance<Effect.Notify>().none { "정비소를 벗어났어요" in it.title })
    }

    private fun signal(at: Long, far: Boolean = false, moved: Boolean = false) = Signals(
        lat = if (far) 37.52 else 37.5, lon = 127.0, gpsAccuracyM = 5f,
        odometerKm = if (moved) 15_000.5 else 15_000.0, ignitionOn = true,
        seenAt = mapOf("gps" to at, "odometer" to at, "ignition" to at))

    private fun workshop(): VerdictEngine = engine().also {
        it.onCar = true
        it.startLend(signal(0), 0, HandKind.SERVICE)
    }

    @Test fun departureRequiresContinuousFreshFixesAndNotifiesOnlyOnce() {
        val e = workshop()
        for (t in listOf(1_000L, 11_000L, 21_000L)) {
            e.update(signal(t, far = true, moved = true), t)
            assertFalse(e.state.lend.roamWarned)
        }
        val confirmed = e.update(signal(31_000, far = true, moved = true), 31_000)
        assertTrue(e.state.lend.roamWarned)
        assertEquals(1, confirmed.filterIsInstance<Effect.Notify>().count { "정비소를 벗어났어요" in it.title })
        assertTrue(e.update(signal(41_000, far = true, moved = true), 41_000)
            .filterIsInstance<Effect.Notify>().none { "정비소를 벗어났어요" in it.title })
    }

    @Test fun staleInaccurateMissingAndRepeatedFixesCannotAccumulateEvidence() {
        val unusable = listOf(
            signal(1_000, true, true).copy(gpsAccuracyM = 300f),
            signal(1_000, true, true).copy(gpsLost = true),
            signal(1_000, true, true).copy(ignitionOn = false),
            signal(1_000, true, true).copy(seenAt = mapOf("gps" to 1_000)),
            signal(1_000, true, true).copy(odometerKm = Double.NaN),
            signal(1_000, true, false),
        )
        unusable.forEach { s ->
            val e = workshop()
            for (t in listOf(1_000L, 11_000L, 21_000L, 31_000L, 41_000L))
                e.update(s.copy(seenAt = s.seenAt.mapValues { t }), t)
            assertFalse(e.state.lend.roamWarned)
        }
        val repeated = workshop()
        val once = signal(1_000, true, true)
        for (t in listOf(1_000L, 11_000L, 21_000L, 31_000L)) repeated.update(once, t)
        assertFalse(repeated.state.lend.roamWarned)
    }

    @Test fun returnInsideOrRestartRequiresANewFullObservationPeriod() {
        val e = workshop()
        e.update(signal(1_000, true, true), 1_000)
        e.update(signal(11_000, true, true), 11_000)
        e.update(signal(21_000, moved = true), 21_000)
        e.update(signal(31_000, true, true), 31_000)
        e.update(signal(41_000, true, true), 41_000)
        assertFalse(e.state.lend.roamWarned)
        val restarted = engine().also { it.onCar = true }
        restarted.loadSession(null, null, e.state.lend)
        restarted.update(signal(51_000, true, true), 51_000)
        restarted.update(signal(61_000, true, true), 61_000)
        restarted.update(signal(71_000, true, true), 71_000)
        assertFalse(restarted.state.lend.roamWarned)
        restarted.update(signal(81_000, true, true), 81_000)
        assertTrue(restarted.state.lend.roamWarned)
    }

    @Test fun locationAccuracyMarginProtectsTheBoundary() {
        val e = workshop()
        // 위도 차이는 약 1,023m이고 양쪽 오차 반경의 합은 55m다.
        for (t in listOf(1_000L, 11_000L, 21_000L, 31_000L)) {
            e.update(signal(t, moved = true).copy(lat = 37.5092, gpsAccuracyM = 50f), t)
        }
        assertFalse(e.state.lend.roamWarned)
    }
}
