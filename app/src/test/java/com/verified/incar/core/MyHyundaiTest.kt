package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MyHyundaiTest {
    private fun engine() = VerdictEngine(Settings(personas = Persona.entries.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    @Test fun consumables_countFromLastRecord() {
        val none = MyHyundai.consumables(14_820.0, emptyList()).first { it.item == Consumable.ENGINE_OIL }
        assertEquals(null, none.remainKm)
        assertFalse(none.due)
        val over = MyHyundai.consumables(25_000.0, listOf(ServiceRecord("2026-01-01", 14_000.0, Consumable.ENGINE_OIL.label))).first { it.item == Consumable.ENGINE_OIL }
        assertTrue(over.due)
        val after = MyHyundai.consumables(14_820.0, listOf(ServiceRecord("2026-10-01", 14_000.0, Consumable.ENGINE_OIL.label))).first { it.item == Consumable.ENGINE_OIL }
        assertEquals(9_180.0, after.remainKm!!, 0.1)
        assertFalse(after.due)
    }

    @Test fun weeks_compareThisAndLastWeek() {
        val stats = listOf(DailyStat("2026-11-02", 30.0, 1, 0, 6), DailyStat("2026-10-28", 50.0, 3, 2, 10), DailyStat("2026-10-20", 99.0))
        val (now, last) = MyHyundai.weeks(stats, LocalDate.of(2026, 11, 2))
        assertEquals(30.0, now.km, 0.01)
        assertEquals(50.0, last.km, 0.01)
        assertEquals(5.0, last.kmPerPct!!, 0.01)
    }

    @Test fun engine_recordsDailyDrivingWithoutScores() {
        val e = engine()
        e.update(Signals(gear = Gear.D, speedKmh = 50f, odometerKm = 100.0, socPct = 80), 0)
        e.update(Signals(gear = Gear.D, speedKmh = 30f, odometerKm = 100.5, socPct = 79), 1_000)
        val day = e.state.stats.single()
        assertEquals(0.5, day.km, 0.01)
        assertEquals(1, day.brakes)
        assertEquals(1, day.socUsed)
    }

    @Test fun control_onlyOnControllableCar_andHornOnlyInPark() {
        val e = engine()
        assertTrue(e.control(ControlKind.LOCK, Signals()).any { it is Effect.Control })
        assertTrue(e.control(ControlKind.HORN, Signals(gear = Gear.D, speedKmh = 20f)).none { it is Effect.Control })
        e.settings = e.settings.copy(carModel = CarModel.OTHER)
        assertTrue(e.control(ControlKind.LOCK, Signals()).none { it is Effect.Control })
    }

    @Test fun sharedKey_registersDriverProfile_andEnforcementNotifiesOnce() {
        val e = engine()
        e.shareKey("자녀", "2026-11-09")
        assertTrue(e.settings.profiles.any { it.name == "자녀" && !it.insured })
        val away = Signals(driverSeated = false, ignitionOn = false, enforcementZone = true)
        assertTrue(e.update(away, 0).any { it is Effect.Notify && "단속" in it.title })
        assertTrue(e.update(away, 1_000).none { it is Effect.Notify && "단속" in it.title })
    }

    /** LubeLogger처럼 거리·기간 중 먼저 오는 쪽: 조금만 달린 차도 1년이 지나면 엔진오일 교체 시기다. */
    @Test fun consumables_dueByDateOrDistanceWhicheverFirst() {
        val today = java.time.LocalDate.parse("2026-11-12")
        val oilAt = { date: String -> listOf(ServiceRecord(date, 14_000.0, Consumable.ENGINE_OIL.label)) }
        fun oil(date: String, odo: Double = 14_500.0) = MyHyundai.consumables(odo, oilAt(date), today = today).first { it.item == Consumable.ENGINE_OIL }
        val old = oil("2025-10-01")
        assertTrue(old.due)
        assertEquals("교체 기간 42일 지남", old.text)
        val nearDate = oil("2025-12-01")
        assertTrue(nearDate.soon)
        assertEquals("9,500km 남음 · 약 19일 뒤", nearDate.text)
        val fresh = oil("2026-10-01")
        assertFalse(fresh.due || fresh.soon)
        assertTrue(oil("2026-10-01", odo = 24_500.0).due) // 거리가 먼저 닿음
        // 브레이크 패드는 기간 기준이 없다
        assertEquals(null, MyHyundai.consumables(14_500.0, listOf(ServiceRecord("2020-01-01", 14_000.0, Consumable.BRAKE_PAD.label)), today = today)
            .first { it.item == Consumable.BRAKE_PAD }.daysLeft)
    }

    @Test fun consumables_etaFromRecentDriving_andHarshBrakingShortensPad() {
        val rec = listOf(ServiceRecord("2026-10-01", 14_000.0, Consumable.BRAKE_PAD.label), ServiceRecord("2026-10-01", 14_000.0, Consumable.ENGINE_OIL.label))
        val calm = listOf(DailyStat("2026-11-01", 70.0, 1), DailyStat("2026-10-26", 70.0, 1))
        val oil = MyHyundai.consumables(14_820.0, rec, calm).first { it.item == Consumable.ENGINE_OIL }
        assertEquals(459, oil.etaDays) // 9,180km ÷ 하루 20km(140km ÷ 7일)
        assertFalse(MyHyundai.consumables(14_820.0, rec, calm).first { it.item == Consumable.BRAKE_PAD }.harsh)
        val harsh = listOf(DailyStat("2026-11-01", 120.0, 8))
        val pad = MyHyundai.consumables(14_820.0, rec, harsh).first { it.item == Consumable.BRAKE_PAD }
        assertTrue(pad.harsh)
        assertEquals(27_180.0, pad.remainKm!!, 0.1)
    }

    @Test fun serviceNote_emergency_wash_arrival() {
        val e = engine()
        val s = Signals(tirePressureLow = true, wiper = Wiper.AUTO, windowOpen = true, socPct = 10, outsideTempC = -2f)
        val note = MyHyundai.serviceNote(s, MyHyundai.consumables(s.odometerKm, emptyList()), e.diagnose(s))
        assertTrue("타이어 공기압 낮음" in note)
        assertTrue(note.any { "와이퍼" in it })
        assertTrue(MyHyundai.emergencySummary(s, e.state).any { "배터리·연료 10%" == it })
        assertEquals(listOf(false, true, false), MyHyundai.washCheck(s).map { it.second })
        assertEquals(9, MyHyundai.arrivalSoc(10, 0.8, cold = false))
        assertTrue(MyHyundai.arrivalSoc(10, 40.0, cold = true) < 0)
        val booked = e.book("정비", "2026-11-04", "10:00", "블루핸즈", s)
        assertTrue(e.settings.bookings.first().note.contains("타이어"))
        assertTrue(booked.any { it is Effect.Notify && "함께 보낸 차 상태" in it.body })
    }

    @Test fun remoteLock_reportsWhatIsStillOpen() {
        val e = engine()
        // 차 신호로 잠금이 확인된 뒤에만 '문은 잠갔지만'을 보낸다
        val open = Signals(windowOpen = true)
        val fx = e.control(ControlKind.LOCK, open, 0)
        assertTrue(fx.any { it is Effect.Control } && fx.none { it is Effect.Notify })
        assertTrue(e.update(open.copy(doorsLocked = true), 1_000).any { it is Effect.Notify && "창문" in it.title })
        val f = engine()
        f.control(ControlKind.LOCK, open, 0)
        assertTrue(f.update(open, VerdictEngine.CONTROL_VERIFY_MS).none { it is Effect.Notify && "잠갔지만" in it.title })
        assertTrue(e.control(ControlKind.LOCK, Signals()).none { it is Effect.Notify })
    }

    @Test fun feed_surfacesDueConsumablesAndParking() {
        val e = engine()
        val cons = MyHyundai.consumables(25_000.0, listOf(ServiceRecord("2026-01-01", 14_000.0, Consumable.ENGINE_OIL.label)))
        val feed = MyHyundai.feed(Signals(washerLow = true), e.state, e.settings, cons)
        assertTrue(feed.any { "엔진오일" in it.title && "1,000km 지남" in it.body })
        assertTrue(feed.none { "-" in it.body })
        assertTrue(feed.any { "워셔액" in it.title })
    }
}
