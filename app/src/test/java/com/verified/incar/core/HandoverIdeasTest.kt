package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverIdeasTest {
    private fun engine() = VerdictEngine(Settings())
    private val parked = Signals(gear = Gear.P, speedKmh = 0f, ignitionOn = true)

    @Test fun idleTimeIsReportedAndRecordedOnlyWhenLong() {
        val e = engine()
        e.startLend(parked, 0)
        e.update(parked, 1_000)
        e.update(parked, 7 * 60_000L)
        e.update(parked.copy(ignitionOn = false), 7 * 60_000L + 1_000)
        e.endLend(parked.copy(ignitionOn = false), 8 * 60_000L)
        assertEquals("7분", e.state.lend.report.toMap()["정차 중 시동"])
        assertTrue(e.state.history.single().changes.any { "정차 중 시동 7분" in it })

        val short = engine()
        short.startLend(parked, 0)
        short.update(parked, 1_000)
        short.endLend(parked, 60_000)
        assertNull(short.state.lend.report.toMap()["정차 중 시동"])
    }

    @Test fun speedingNotifiesOnceAndMarksRecord() {
        val e = engine()
        e.startLend(parked, 0)
        val fast = parked.copy(gear = Gear.D, speedKmh = VerdictEngine.HANDOVER_SPEED_KMH + 5)
        val first = e.update(fast, 10_000).filterIsInstance<Effect.Notify>().filter { "과속" in it.title }
        val second = e.update(fast, 11_000).filterIsInstance<Effect.Notify>().filter { "과속" in it.title }
        assertEquals(1, first.size)
        assertEquals(Level.WARN, first.single().level)
        assertTrue(second.isEmpty())
        e.endLend(parked, 20_000)
        assertFalse(e.state.history.single().ok)
    }

    @Test fun valetDoesNotReportSpeedToOwner() {
        val e = engine()
        e.startValet()
        e.confirmValetConsent(parked, 0)
        val fast = parked.copy(gear = Gear.D, speedKmh = VerdictEngine.HANDOVER_SPEED_KMH + 5)
        val fx = e.update(fast, 10_000)
        assertEquals(ValetPhase.DRIVING, e.state.valet.phase)
        assertTrue(fx.filterIsInstance<Effect.Notify>().none { "과속" in it.title })
        assertTrue(e.state.valet.proof.none { "km/h 주행" in it.what })
    }

    @Test fun fineDateMatchesHandover() {
        val start = MyHyundai.parseDateTime("2026-11-10 14:00")!!
        val end = MyHyundai.parseDateTime("2026.11.10 15:00")!!
        val h = listOf(HandoverRecord("대리", start, end, 1.0, 40f, 0, emptyList(), true))
        assertNotNull(MyHyundai.handoverAt(h, MyHyundai.parseDateTime("202611101430")!!))
        assertNull(MyHyundai.handoverAt(h, MyHyundai.parseDateTime("2026-11-10 15:01")!!))
        assertNull(MyHyundai.parseDateTime("2026-13-40 14:30"))
        assertNull(MyHyundai.parseDateTime("어제 오후"))
    }

    @Test fun tripBatteryAccountsForColdAndSpeed() {
        val mild = Signals(socPct = 60, naviRemainKm = 180f, outsideTempC = 15f, speedKmh = 80f)
        val harsh = mild.copy(outsideTempC = -5f, speedKmh = 115f)
        val a = MyHyundai.tripBattery(mild)!!
        val b = MyHyundai.tripBattery(harsh)!!
        assertTrue(b.arrivalPct < a.arrivalPct)
        assertFalse(a.low)
        assertTrue(b.low)
        assertEquals(listOf("추운 날씨", "고속 주행"), b.reasons)
        assertNull(MyHyundai.tripBattery(mild.copy(naviRemainKm = null)))
    }

    @Test fun evWarningOnlyForEvCars() {
        val s = Signals(socPct = 60, naviRemainKm = 180f, outsideTempC = -5f, speedKmh = 115f)
        fun cards(m: CarModel) = MyHyundai.feed(s, EngineState(), Settings(carModel = m), emptyList()).filter { "도착하면 배터리" in it.title }
        assertEquals(1, cards(CarModel.IONIQ3).size)
        assertTrue(cards(CarModel.GRANDEUR).isEmpty())
    }

    @Test fun chargerFiltersCombine() {
        val chargers = MyHyundai.POIS.mapNotNull { it.charger }
        fun pass(vararg names: String) = chargers.count { c -> MyHyundai.CHARGER_FILTERS.all { (n, t) -> n !in names || t(c) } }
        assertTrue(pass("사용 가능") < chargers.size)
        assertEquals(1, pass("사용 가능", "외부 출입 가능", "지하·실내", "DC콤보"))
    }
}
