package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesTest {
    private fun engine() = VerdictEngine(Settings(personas = setOf(Persona.FAMILY_SHARE), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    @Test fun serviceHandover_countsStartsDoorsAndDistance() {
        val e = engine()
        val parked = Signals(odometerKm = 1_000.0)
        e.update(parked, 0)
        e.startLend(parked, 0, HandKind.SERVICE)
        e.update(parked.copy(ignitionOn = false, driverSeated = false), 1_000)
        e.update(parked.copy(ignitionOn = false, driverSeated = false, driverDoorOpen = true), 2_000)
        // 직원이 시트를 그대로 두고 앉아도(차주 자세) 맡김은 끝나지 않는다
        e.update(parked.copy(ignitionOn = true), 3_000)
        assertTrue(e.state.lend.active)
        e.update(parked.copy(gear = Gear.D, speedKmh = 84f), 4_000)
        val back = parked.copy(odometerKm = 1_012.6)
        e.update(back, 5_000)
        val fx = e.endLend(back, 6_000)
        val report = e.state.lend.report.toMap()
        assertEquals("12.6km", report["이동 거리"])
        assertEquals("1회", report["시동 켠 횟수"])
        assertEquals("1회", report["운전석 문 연 횟수"])
        assertEquals("84km/h", report["최고 속도"])
        assertTrue(fx.any { it is Effect.Notify && "맡긴 차 인수 리포트" in it.title })
    }

    @Test fun slowLeak_findsOneWheel_butNotColdWeather() {
        fun day(d: Int, t: List<Float>) = DailyStat("2026-11-0${d}", tires = t)
        val leak = (1..7).map { d -> day(d, listOf(250f - d * 0.3f, 250f - d * 0.3f, 252f - d * 2.2f, 250f - d * 0.3f)) }
        val (wheel, extra) = MyHyundai.slowLeak(leak)!!
        assertEquals(2, wheel)
        assertTrue(extra >= MyHyundai.LEAK_KPA)
        // 추워서 네 바퀴가 같이 내려가면 누설이 아니다
        val cold = (1..7).map { d -> day(d, List(4) { 250f - d * 3f }) }
        assertNull(MyHyundai.slowLeak(cold))
        // 기록이 3일 미만이면 판단하지 않는다
        assertNull(MyHyundai.slowLeak(leak.take(2)))
    }

    @Test fun tires_recordDailyMinimumWhileParked() {
        val e = engine()
        e.update(Signals(tireKpa = listOf(250f, 250f, 245f, 250f)), 0)
        e.update(Signals(tireKpa = listOf(251f, 249f, 247f, 250f)), 1_000)
        assertEquals(listOf(250f, 249f, 245f, 250f), e.state.stats.single().tires)
    }

    @Test fun evac_onlyAfterFastDriving_andClosesWhenHazardOff() {
        val e = engine()
        // 주차장에서 비상등만 켜면 안내하지 않는다
        e.update(Signals(hazardOn = true), 0)
        assertFalse(e.state.evac.active)
        e.update(Signals(hazardOn = false), 1_000)
        e.update(Signals(gear = Gear.D, speedKmh = 92f), 2_000)
        e.update(Signals(gear = Gear.D, speedKmh = 0f), 10_000)
        // 정체 때 D단으로 서서 켠 비상등(뒤차 경고)에는 대피 안내를 하지 않는다
        e.update(Signals(gear = Gear.D, speedKmh = 0f, hazardOn = true), 12_000)
        assertFalse(e.state.evac.active)
        val fx = e.update(Signals(gear = Gear.P, speedKmh = 0f, hazardOn = true), 13_000)
        assertTrue(e.state.evac.active)
        assertTrue(fx.any { it is Effect.Speak && "대피" in it.text })
        assertTrue(e.drivingLines().first().contains("대피"))
        e.update(Signals(gear = Gear.D, speedKmh = 0f, hazardOn = false), 20_000)
        assertFalse(e.state.evac.active)
    }
}
