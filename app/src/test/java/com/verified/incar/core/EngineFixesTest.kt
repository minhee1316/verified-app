package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-06 점검에서 찾은 판정 오류를 다시 생기지 않게 막는 테스트. */
class EngineFixesTest {
    private fun engine(vararg p: Persona) =
        VerdictEngine(Settings(personas = p.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    private val pickup = Signals(place = Place.OTHER)

    @Test fun valet_cannotBeRestartedToWipeIncompleteWarning() {
        val e = engine(Persona.NIGHT_OUT)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        e.update(pickup.copy(gear = Gear.D, speedKmh = 30f), 1_000)
        e.update(pickup.copy(driverSeated = false, driverDoorOpen = true), 2_000)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(e.startValet().any { it is Effect.Toast })
        e.chooseToday("VALET", pickup)
        e.callValet()
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
    }

    @Test fun valet_noVerdictBeforeTheCarMoves() {
        val e = engine(Persona.NIGHT_OUT)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        // 기사가 키를 받으려고 문을 열어도 아직 '집이 아닌 곳에서 내림'이 아니다
        val fx = e.update(pickup.copy(driverDoorOpen = true), 1_000)
        assertEquals(ValetPhase.DRIVING, e.state.valet.phase)
        assertTrue(fx.none { it is Effect.Notify })
    }

    @Test fun valet_onRealCarNeedsHomePin() {
        val e = engine(Persona.NIGHT_OUT)
        e.requireHomePin = true
        assertTrue(e.startValet().any { it is Effect.Toast && "집" in it.text })
        assertEquals(ValetPhase.IDLE, e.state.valet.phase)
        e.settings = e.settings.copy(pins = mapOf(Place.HOME to Pin(37.5, 127.0)))
        assertTrue(e.startValet().isEmpty())
        assertEquals(ValetPhase.CONSENT, e.state.valet.phase)
    }

    @Test fun posture_comparesOnlyAxesTheCarSends() {
        assertTrue(Postures.distance(Postures.OWNER, Postures.OWNER.take(3)) < 0.01f)
        assertEquals(1f, Postures.distance(emptyList(), Postures.OWNER))
    }

    @Test fun driver_withoutRegisteredPosture_asksInsteadOfStrangerAlert() {
        val e = engine(Persona.FAMILY_SHARE)
        e.settings = e.settings.copy(profiles = e.settings.profiles.map { it.copy(posture = emptyList()) })
        val fx = e.update(Signals(posture = listOf(0.5f, 0.5f, 0.5f)), 0)
        assertEquals(DriverVerdict.ASK, e.state.driver.verdict)
        assertTrue(fx.none { it is Effect.Notify })
        // 누구인지 모르는 자세는 차주 프로필에 섞지 않는다
        e.chooseToday("KNOWN", Signals(posture = listOf(0.5f, 0.5f, 0.5f)))
        assertTrue(e.settings.profiles.all { it.posture.isEmpty() })
    }

    @Test fun driver_rejudgesAfterSeatIsAdjusted() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(Signals(posture = Postures.STRANGER), 0)
        assertEquals(DriverVerdict.UNKNOWN, e.state.driver.verdict)
        // 시트를 조금씩 움직여 차주 자세가 되면 다시 판정한다
        var t = 1_000L
        for (step in 1..10) {
            val f = step / 10f
            e.update(Signals(posture = Postures.STRANGER.zip(Postures.OWNER) { a, b -> a + (b - a) * f }), t)
            t += 1_000
        }
        assertEquals(DriverVerdict.KNOWN, e.state.driver.verdict)
    }

    @Test fun lend_endsOnlyAfterSomeoneElseSat() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(Signals(), 0)
        e.startLend(Signals(), 0)
        // 빌린 사람이 시트를 그대로 두고 앉아도(차주 자세) 반납으로 보지 않는다
        e.update(Signals(driverSeated = false), 1_000)
        e.update(Signals(), 2_000)
        assertTrue(e.state.lend.active)
        e.update(Signals(posture = Postures.SPOUSE), 3_000)
        e.update(Signals(driverSeated = false), 4_000)
        e.update(Signals(odometerKm = 14_850.0), 5_000)
        assertFalse(e.state.lend.active)
        assertTrue(e.state.lend.report.isNotEmpty())
    }

    @Test fun schedule_refusedStartIsNotRetriedEveryTick() {
        val e = engine()
        e.settings = e.settings.copy(schedule = DepartureSchedule(true, 0, 5, (1..7).toSet(), 10), flags = Flag.entries.toSet() - Flag.REMOTE)
        e.caps = e.caps.copy(climateControl = true, seatControl = true)
        val zone = java.time.ZoneId.systemDefault()
        // 00:05 출발 10분 전 = 전날 23:57 (자정을 넘는 예약)
        val t = java.time.LocalDate.of(2026, 11, 6).atTime(23, 57).atZone(zone).toInstant().toEpochMilli()
        val parked = Signals(driverSeated = false, ignitionOn = false, doorsLocked = true)
        val first = e.update(parked, t)
        assertTrue(first.any { it is Effect.Toast })
        val again = e.update(parked, t + 30_000)
        assertTrue(again.none { it is Effect.Toast && "원격" in it.text })
    }

    @Test fun escort_alightOnRoadSide_isNotReportedAsSidewalk() {
        val e = engine(Persona.PARENT_CARE)
        val board = Signals(row2Seated = true, row2Belt = true)
        e.update(board, 0)
        e.startEscort(0)
        e.update(board, 1_000)
        e.update(board.copy(gear = Gear.D, speedKmh = 30f), 2_000)
        e.update(board, 3_000)
        e.update(board, 7_000)
        e.update(board.copy(row2LeftDoorOpen = true), 8_000)
        val fx = e.update(board.copy(row2Seated = false, row2Belt = false), 9_000)
        assertEquals(EscortPhase.ALIGHTED, e.state.escort.phase)
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.WARN && "차도 쪽" in it.body })
    }

    @Test fun tripLog_namesValetDriver_notOwner() {
        val e = engine(Persona.NIGHT_OUT, Persona.BUSINESS)
        e.settings = e.settings.copy(businessMode = false)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        e.update(pickup.copy(gear = Gear.D, speedKmh = 30f), 1_000)
        e.update(pickup.copy(odometerKm = pickup.odometerKm + 5), 2_000)
        e.update(pickup.copy(odometerKm = pickup.odometerKm + 5), 20_000)
        assertEquals("대리기사", e.state.trip.logs.first().driver)
    }
}
