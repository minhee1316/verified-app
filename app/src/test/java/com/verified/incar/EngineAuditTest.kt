package com.verified.incar

import com.verified.incar.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 판정 엔진 점검(엔진 영역)에서 찾은 오류를 다시 생기지 않게 막는 테스트. */
class EngineAuditTest {
    private fun engine(vararg p: Persona) =
        VerdictEngine(Settings(personas = p.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    @Test fun escort_gearOnlyIsNotDeparture_andDoorFlagsResetPerStop() {
        val e = engine(Persona.PARENT_CARE)
        val board = Signals(row2Seated = true, row2Belt = true)
        e.update(board, 0)
        e.startEscort(0)
        // 서 있는 채 D를 넣었다 P로 돌아와도 출발·도착이 아니다
        val a = e.update(board.copy(gear = Gear.D), 1_000)
        val b = e.update(board, 5_000)
        assertEquals(EscortPhase.READY, e.state.escort.phase)
        assertTrue((a + b).none { it is Effect.Notify || it is Effect.Speak })
        val go = board.copy(gear = Gear.D, speedKmh = 30f)
        e.update(go, 6_000)
        e.update(board, 7_000)
        e.update(board, 11_000)
        // 중간 정차에서 보도 쪽 문만 잠깐 열림
        e.update(board.copy(row2RightDoorOpen = true), 12_000)
        e.update(board, 13_000)
        e.update(go, 14_000)
        e.update(board, 15_000)
        e.update(board, 19_000)
        // 병원에서는 차도 쪽 문으로 내림
        e.update(board.copy(row2LeftDoorOpen = true), 20_000)
        val fx = e.update(board.copy(row2Seated = false, row2Belt = false), 21_000)
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.WARN && "차도 쪽" in it.body })
    }

    @Test fun harshBrake_measuredFromLastSpeedChange_notLastTick() {
        val e = engine()
        e.update(Signals(gear = Gear.D, speedKmh = 50f), 0)
        e.update(Signals(gear = Gear.D, speedKmh = 50f), 300) // 타이머 틱
        e.update(Signals(gear = Gear.D, speedKmh = 47f), 500) // 0.5초에 3km/h: 보통 감속
        assertTrue(e.state.stats.none { it.brakes > 0 })
    }

    @Test fun trip_reparkAndGearOnly_doNotPushRealTripToUnclassified() {
        val e = engine(Persona.BUSINESS)
        e.update(Signals(), 0)
        e.update(Signals(gear = Gear.D, speedKmh = 40f, odometerKm = 100.0), 1_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.2), 2_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.2), 13_000)
        // 목적을 고르기 전에 R로 3m 재주차
        e.update(Signals(place = Place.OTHER, gear = Gear.R, speedKmh = 3f, odometerKm = 112.203), 14_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.203), 15_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.203), 26_000)
        // 정지한 채 P→D→P
        e.update(Signals(place = Place.OTHER, gear = Gear.D, odometerKm = 112.203), 27_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.203), 28_000)
        assertEquals(12.2, e.state.trip.pending!!.distKm, 0.01)
        assertTrue(e.state.trip.logs.isEmpty())
    }

    @Test fun tripCsv_unclassifiedIsNotBusiness_andHasTotals() {
        val csv = VerdictEngine.tripCsv(listOf(
            TripLog("2026-11-01", "차주", "집", "마트", 0.0, 5.0, VerdictEngine.UNCLASSIFIED, true),
            TripLog("2026-11-02", "차주", "집", "거래처", 5.0, 15.0, "거래처 방문", true),
        ), CarModel.GRANDEUR)
        val lines = csv.lines()
        assertTrue(lines[1].contains(CarModel.GRANDEUR.label))
        assertTrue(lines[2].contains("사용자(부서)"))
        assertTrue(lines[3].contains("\"5.0\",\"\",\"\",\"미분류 (집→마트)\""))
        assertTrue(lines[4].contains("\"10.0\",\"\",\"10.0\""))
        assertEquals("\"15.0\",\"10.0\",\"66.7\"", lines[6])
    }

    @Test fun nHold_showsStepOnLockedScreen_andSpeaksWhenShiftedToN() {
        val e = engine()
        e.startNHold()
        e.update(Signals(), 0)
        val fx = e.update(Signals(gear = Gear.N), 1_000)
        assertTrue(fx.any { it is Effect.Speak && "OK 버튼" in it.text })
        assertTrue(e.drivingLines().any { it.startsWith("⑨") })
        e.update(Signals(gear = Gear.D, speedKmh = 30f), 2_000)
        assertTrue(e.drivingLines().none { it.startsWith("⑨") })
    }

    @Test fun nHold_endsWhenRestartedAndDrivenOff_withoutP() {
        val e = engine()
        e.startNHold()
        e.update(Signals(gear = Gear.N), 0)
        e.update(Signals(gear = Gear.N, ignitionOn = false), 1_000)
        e.update(Signals(gear = Gear.N), 60_000)
        // 시동을 다시 켠 뒤 문을 열어 P가 된 것은 실패가 아니다
        e.update(Signals(gear = Gear.P, driverDoorOpen = true), 61_000)
        assertEquals(NHoldPhase.DONE, e.state.nhold.phase)

        e.startNHold()
        e.update(Signals(gear = Gear.N), 100_000)
        e.update(Signals(gear = Gear.N, ignitionOn = false), 101_000)
        e.update(Signals(gear = Gear.N), 400_000)
        e.update(Signals(gear = Gear.D, speedKmh = 40f), 401_000)
        assertEquals(NHoldPhase.OFF, e.state.nhold.phase)
        val late = e.update(Signals(gear = Gear.D, speedKmh = 60f), 101_000 + 30 * 60_000L)
        assertTrue(late.none { it is Effect.Notify && "⑨" in it.title })
    }

    @Test fun serviceReport_doesNotCountOwnersPickupStart() {
        val e = engine(Persona.FAMILY_SHARE)
        val parked = Signals(odometerKm = 1_000.0)
        e.update(parked, 0)
        e.startLend(parked, 0, HandKind.SERVICE)
        e.update(parked.copy(ignitionOn = false), 1_000)
        e.update(parked, 2_000) // 직원 시동
        e.update(parked.copy(gear = Gear.D, speedKmh = 30f), 3_000)
        val back = parked.copy(odometerKm = 1_005.0)
        e.update(back, 4_000)
        e.update(back.copy(ignitionOn = false), 5_000)
        e.update(back, 6_000) // 찾으러 온 차주 시동
        e.endLend(back, 7_000)
        assertEquals("1회", e.state.lend.report.toMap()["시동 켠 횟수"])
    }

    @Test fun firstRealOdometer_whileParked_isNotDriving() {
        val e = engine()
        e.update(Signals(), 0)
        e.update(Signals(odometerKm = 32_104.0), 1_000)
        assertTrue(e.state.stats.none { it.km > 0 })
    }

    @Test fun valet_nGearShutdown_completesAndClosesTripOnce() {
        val e = engine(Persona.NIGHT_OUT, Persona.BUSINESS)
        val pickup = Signals(place = Place.OTHER)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        e.update(pickup.copy(gear = Gear.D, speedKmh = 30f), 1_000)
        val nStop = Signals(place = Place.HOME, gear = Gear.N, odometerKm = pickup.odometerKm + 5)
        e.update(nStop, 2_000)
        e.update(nStop.copy(ignitionOn = false), 3_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertNotNull(e.state.trip.pending)
        // N단·시동 OFF로 서 있는 동안 운행이 다시 열리지 않는다
        val off = nStop.copy(ignitionOn = false, driverSeated = false, driverDoorOpen = true)
        e.update(off, 20_000)
        e.update(off, 40_000)
        assertEquals(null, e.state.trip.open)
        assertTrue(e.state.trip.logs.isEmpty())
    }

    @Test fun valet_completeWithDriverStillSeated_doesNotWarnOnce() {
        val e = engine(Persona.NIGHT_OUT)
        val pickup = Signals(place = Place.OTHER, posture = Postures.STRANGER)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        e.update(pickup.copy(gear = Gear.D, speedKmh = 30f), 1_000)
        val home = pickup.copy(place = Place.HOME)
        e.update(home, 2_000)
        val fx = e.update(home.copy(ignitionOn = false), 3_500, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertTrue(fx.none { it is Effect.Notify && it.level == Level.WARN })
        assertEquals(1, fx.count { it is Effect.Speak })
        // 기사가 내린 뒤 다른 사람이 앉으면 다시 경고한다
        e.update(home.copy(driverSeated = false), 5_000)
        val again = e.update(home, 6_000)
        assertTrue(again.any { it is Effect.Notify && it.level == Level.WARN && "처음 보는" in it.title })
    }

    @Test fun service_staffSeating_isNotStrangerAlert() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(Signals(), 0)
        e.startLend(Signals(), 0, HandKind.SERVICE)
        e.update(Signals(driverSeated = false), 1_000)
        val fx = e.update(Signals(posture = Postures.STRANGER), 2_000)
        assertTrue(fx.none { it is Effect.Notify || (it is Effect.Speak && "보험" in it.text) })
    }

    @Test fun feed_serviceReportCard_titledAndRoutedRight_andExpires() {
        val e = engine()
        e.startLend(Signals(), 0, HandKind.SERVICE)
        e.endLend(Signals(), 1_000)
        val soon = MyHyundai.feed(Signals(), e.state, e.settings, emptyList(), 1_000 + 3_600_000L).first()
        assertEquals(HandKind.SERVICE.report, soon.title)
        assertEquals("SERVICE", soon.tab)
        val later = MyHyundai.feed(Signals(), e.state, e.settings, emptyList(), 1_000 + 25 * 3_600_000L)
        assertTrue(later.none { it.title == HandKind.SERVICE.report })
    }

    @Test fun needsTick_onlyWhileStopped_forTripValetEscort() {
        val e = engine(Persona.BUSINESS)
        e.update(Signals(), 0)
        e.update(Signals(gear = Gear.D, speedKmh = 40f), 1_000)
        assertFalse(e.needsTick())
        e.update(Signals(), 2_000)
        assertTrue(e.needsTick())
        e.update(Signals(), 13_000)
        assertFalse(e.needsTick())
    }

    @Test fun ownerTexts_useDotDecimal_inAnyLocale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val e = engine()
            e.startLend(Signals(odometerKm = 1_000.0), 0, HandKind.SERVICE)
            e.endLend(Signals(odometerKm = 1_012.6), 1_000)
            assertEquals("12.6km", e.state.lend.report.toMap()["이동 거리"])
            val cons = MyHyundai.consumables(25_000.0, listOf(ServiceRecord("2026-01-01", 14_000.0, Consumable.ENGINE_OIL.label)))
            assertTrue(MyHyundai.feed(Signals(), e.state, e.settings, cons).any { "1,000km 지남" in it.body })
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun parkingFloor_reverseFullLock_isNotRampTurn() {
        val e = engine(Persona.SCHOOL_RUN)
        val b1 = Signals(place = Place.OTHER, gpsLost = true, gear = Gear.D, speedKmh = 10f)
        e.update(Signals(place = Place.HOME, gear = Gear.D, speedKmh = 20f), 0)
        e.update(b1, 1_000)
        e.update(b1.copy(steeringDeg = 30f), 2_000)
        e.update(b1, 3_000)
        // 후진 주차에서 핸들을 끝까지 두 번 돌림
        val back = b1.copy(gear = Gear.R, speedKmh = 3f)
        e.update(back.copy(steeringDeg = 35f), 4_000)
        e.update(back, 5_000)
        e.update(back.copy(steeringDeg = -35f), 6_000)
        e.update(back, 7_000)
        e.update(Signals(place = Place.OTHER, gpsLost = true), 20_000)
        assertEquals(1, e.state.floor.estimate)
    }
}
