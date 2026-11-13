package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VerdictEngineTest {
    private fun engine(vararg p: Persona) =
        VerdictEngine(Settings(personas = p.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    private fun startedValet(e: VerdictEngine, s: Signals) {
        e.update(s, 0)
        e.startValet()
        e.confirmValetConsent(s, 0)
    }

    private val driving = Signals(gear = Gear.D, speedKmh = 30f, place = Place.OTHER, driverSeated = true)

    @Test fun valet_shutdownRightAfterP_stillCompletes() {
        val e = engine(Persona.NIGHT_OUT)
        startedValet(e, driving)
        e.update(driving, 1_000)
        val parked = driving.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOME)
        e.update(parked, 2_000)
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        // 기사가 2초 만에 시동을 끔: 10초를 기다리지 않고 확정
        val fx = e.update(parked.copy(ignitionOn = false), 4_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.OK })
    }

    @Test fun valet_prelimSavedThenPowerCut_recoversAsComplete() {
        val e = VerdictEngine()
        e.recoverValet(ValetState(phase = ValetPhase.PRELIM, prelimAtMs = 1))
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
    }

    @Test fun valet_undergroundGpsLost_usesLastPlace() {
        val e = engine(Persona.NIGHT_OUT)
        startedValet(e, driving)
        e.update(driving.copy(place = Place.HOME), 1_000)
        val garage = driving.copy(gear = Gear.P, speedKmh = 0f, place = Place.OTHER, gpsLost = true, driverSeated = false, driverDoorOpen = true)
        e.update(garage, 2_000)
        e.update(garage, 13_000)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertTrue(e.state.valet.placeEstimated)
    }

    @Test fun valet_leftOutsideHome_warnsOwnerNotToDrive() {
        val e = engine(Persona.NIGHT_OUT)
        startedValet(e, driving)
        e.update(driving, 1_000)
        val fx = e.update(driving.copy(gear = Gear.P, speedKmh = 0f, driverSeated = false, driverDoorOpen = true), 2_000)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.DANGER && "직접 운전하지" in it.body })
    }

    @Test fun restore_blockedWhenRow2Occupied() {
        val e = engine(Persona.NIGHT_OUT)
        val fx = e.requestRestore(Signals(row2Seated = true))
        assertFalse(fx.contains(Effect.RestoreSeat))
        assertTrue(e.state.valet.restoreMsg!!.contains("뒷좌석"))
        assertTrue(e.requestRestore(Signals()).contains(Effect.RestoreSeat))
    }

    @Test fun driver_unknownPosture_warnsOnce() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(Signals(), 0)
        assertEquals(DriverVerdict.KNOWN, e.state.driver.verdict)
        val fx = e.update(Signals(posture = Postures.STRANGER), 1_000)
        assertEquals(DriverVerdict.UNKNOWN, e.state.driver.verdict)
        assertEquals(1, fx.count { it is Effect.Notify })
        assertEquals(DriverVerdict.ASK, e.judgeDriver(Postures.AMBIGUOUS).verdict)
        assertTrue(e.judgeDriver(Postures.OWNER, LocalDate.of(2030, 1, 1)).notes.any { "만료일" in it })
    }

    @Test fun escort_seaWarning_turnsAppLightOff() {
        val e = engine(Persona.PARENT_CARE)
        val board = Signals(row2Seated = true, row2Belt = true, place = Place.HOME)
        e.startEscort(0)
        e.update(board, 0)
        assertEquals(EscortPhase.READY, e.state.escort.phase)
        e.update(board.copy(gear = Gear.D, speedKmh = 20f), 1_000)
        val arrived = board.copy(place = Place.HOSPITAL)
        e.update(arrived, 2_000)
        e.update(arrived, 6_000)
        assertEquals(EscortPhase.ALIGHT_GUIDE, e.state.escort.phase)
        assertEquals(Level.OK, e.state.ambient!!.right)
        e.update(arrived.copy(seaWarning = true), 7_000)
        assertNull(e.state.ambient!!.right)
        assertNull(e.state.ambient!!.left)
        e.update(arrived.copy(row2LeftDoorOpen = true), 8_000)
        assertEquals(EscortPhase.WRONG_SIDE, e.state.escort.phase)
        e.update(arrived.copy(row2Seated = false, row2Belt = false), 9_000)
        assertEquals(EscortPhase.ALIGHTED, e.state.escort.phase)
    }

    @Test fun trunk_shortOpenIsNotLoad_longOpenRemindsAtHome() {
        val e = engine(Persona.SCHOOL_RUN)
        val mart = Signals(place = Place.MART)
        e.update(mart, 0)
        e.update(mart.copy(trunkOpen = true), 1_000)
        e.update(mart, 2_000)
        assertFalse(e.state.trunk.loaded)
        e.update(mart.copy(trunkOpen = true), 3_000)
        e.update(mart, 10_000)
        assertTrue(e.state.trunk.loaded)
        val fx = e.update(Signals(place = Place.HOME, driverDoorOpen = true), 11_000)
        assertTrue(fx.any { it is Effect.Speak && "트렁크" in it.text })
    }

    @Test fun school_slowStartSucceeds_fastStartWarnsAndFails() {
        val e = engine(Persona.SCHOOL_RUN)
        val stop = Signals(place = Place.SCHOOL, row2Seated = true, row2Belt = true)
        e.update(stop, 0)
        e.update(stop.copy(row2Seated = false, row2Belt = false), 1_000)
        assertEquals(SchoolPhase.WAIT_MOVE, e.state.school.phase)
        val go = stop.copy(row2Seated = false, gear = Gear.D, speedKmh = 20f)
        e.update(go, 2_000)
        e.update(go, 63_000)
        assertEquals(SchoolPhase.SUCCESS, e.state.school.phase)

        e.update(stop.copy(gear = Gear.P, speedKmh = 0f), 70_000)
        e.update(stop.copy(row2Seated = false), 71_000)
        e.update(go, 72_000)
        val fx = e.update(go.copy(speedKmh = 40f), 74_000)
        assertTrue(fx.any { it is Effect.Speak })
        e.update(go, 140_000)
        assertEquals(SchoolPhase.FAIL, e.state.school.phase)
    }

    @Test fun chauffeur_harshBrakeCostsScoreAndSpeaks() {
        val e = engine(Persona.PARENT_CARE)
        val ride = Signals(row2Seated = true, row2Belt = true, row2Reclined = true, gear = Gear.D, speedKmh = 50f)
        e.update(ride, 0)
        val fx = e.update(ride.copy(speedKmh = 35f), 1_000)
        assertEquals(94, e.state.chauffeur.score)
        assertTrue(fx.any { it is Effect.Speak })
    }

    @Test fun diag_neverClaimsSafeToDrive() {
        val e = VerdictEngine()
        val all = e.diagnose(Signals(wiper = Wiper.AUTO, recircOn = true, acOn = false, washerLow = true)) + e.diagnose(Signals())
        assertTrue(all.none { "정상 주행" in it.title || "정상 주행" in it.detail })
        assertEquals("R99", e.diagnose(Signals()).single().code)
        assertFalse(DIAG_DISCLAIMER.contains("정상 주행"))
    }

    @Test fun trip_endsAfterStop_andCsvSplitsCommute() {
        val e = engine(Persona.BUSINESS)
        e.update(Signals(), 0)
        e.update(Signals(gear = Gear.D, speedKmh = 40f, odometerKm = 100.0), 1_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.2), 2_000)
        e.update(Signals(place = Place.OTHER, odometerKm = 112.2), 13_000)
        assertTrue(e.state.trip.pending != null)
        e.selectPurpose("출퇴근")
        val csv = VerdictEngine.tripCsv(e.state.trip.logs)
        // 0·1행은 사업연도·차종 머리, 2행은 열 이름, 3행부터 기록
        assertTrue(csv.lines()[3].contains("\"12.2\",\"12.2\",\"\""))
    }

    @Test fun missions_detectRealSignalChanges() {
        val e = engine(Persona.NEW_DRIVER)
        e.update(Signals(), 0)
        e.startMissions(0)
        e.update(Signals(wiper = Wiper.LOW, doorsLocked = true), 4_000)
        val done = e.state.missions.items.filter { it.done }.map { it.id }
        assertEquals(listOf("M01", "M06"), done)
    }

    // ---------------------------------------------------------------- ⑩ 출발 준비
    private val parkedEmpty = Signals(driverSeated = false, ignitionOn = false, doorsLocked = true)

    @Test fun remote_needsLockedDoorsAndBattery_thenAutoOff() {
        val e = engine()
        e.update(parkedEmpty, 0)
        assertTrue(e.startRemote(parkedEmpty.copy(doorsLocked = false), 0).none { it is Effect.RemoteClimate })
        assertTrue(e.startRemote(parkedEmpty.copy(socPct = 15), 0).none { it is Effect.RemoteClimate })
        assertTrue(e.startRemote(parkedEmpty, 0).any { it is Effect.RemoteClimate })
        assertEquals(ComfortPhase.REMOTE_RUNNING, e.state.comfort.phase)
        e.update(parkedEmpty, 10 * 60_000L)
        assertEquals(ComfortPhase.OFF, e.state.comfort.phase)
    }

    @Test fun boarding_appliesRecognizedDriversPreset() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(parkedEmpty, 0)
        val fx = e.update(Signals(posture = Postures.SPOUSE), 1_000)
        val applied = fx.filterIsInstance<Effect.ApplyClimate>().single().preset
        assertEquals(ComfortPreset.SEAT.title, applied.title)
        assertEquals(3, applied.driverHeat)
    }

    @Test fun boarding_strangerGetsNoPersonalPreset() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(parkedEmpty, 0)
        val fx = e.update(Signals(posture = Postures.STRANGER), 1_000)
        assertTrue(fx.none { it is Effect.ApplyClimate })
    }

    @Test fun apply_dropsUnsupportedVentAndEmptyRearSeat() {
        val e = engine()
        e.caps = Capabilities(seatVent = false)
        e.settings = e.settings.copy(comfort = mapOf("차주" to ComfortPreset.COOL.copy(rearHeat = 2)))
        val p = e.applyNow(Signals(), 0, "차주", "").filterIsInstance<Effect.ApplyClimate>().single().preset
        assertEquals(0, p.driverVent)
        assertEquals(0, p.rearHeat)
    }

    @Test fun schedule_startsRemoteInsideLeadWindowOncePerDay() {
        val e = engine()
        e.settings = e.settings.copy(schedule = DepartureSchedule(true, 7, 30, (1..7).toSet(), 10))
        val at = { h: Int, m: Int -> java.time.LocalDateTime.of(2026, 11, 2, h, m).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        e.update(parkedEmpty, at(7, 0))
        assertEquals(ComfortPhase.OFF, e.state.comfort.phase)
        val fx = e.update(parkedEmpty, at(7, 22))
        assertTrue(fx.any { it is Effect.RemoteClimate })
        e.stopRemote()
        assertTrue(e.update(parkedEmpty, at(7, 25)).none { it is Effect.RemoteClimate })
    }

    @Test fun comfort_onlyWorksForRegisteredCar() {
        val e = engine()
        e.settings = e.settings.copy(carModel = null, schedule = DepartureSchedule(true, 7, 30, (1..7).toSet(), 10))
        e.update(parkedEmpty, 0)
        assertTrue(e.startRemote(parkedEmpty, 0).none { it is Effect.RemoteClimate })
        assertTrue(e.update(Signals(), 1_000).none { it is Effect.ApplyClimate })
        assertEquals(ComfortPhase.OFF, e.state.comfort.phase)
    }

    @Test fun otherCar_getsDrivingFeaturesButNoControl() {
        val e = engine(Persona.PARENT_CARE, Persona.NIGHT_OUT)
        e.settings = e.settings.copy(carModel = CarModel.OTHER)
        assertFalse(e.controlAllowed())
        e.update(parkedEmpty, 0)
        assertTrue(e.startRemote(parkedEmpty, 0).none { it is Effect.RemoteClimate })
        assertTrue(e.update(Signals(), 1_000).none { it is Effect.ApplyClimate })
        assertFalse(e.requestRestore(Signals()).contains(Effect.RestoreSeat))
        // 판정 기능은 그대로 동작한다
        e.startValet()
        e.confirmValetConsent(Signals(gear = Gear.D, speedKmh = 30f, place = Place.OTHER), 2_000)
        e.update(Signals(gear = Gear.D, speedKmh = 30f, place = Place.OTHER), 2_000)
        e.update(Signals(place = Place.HOME), 3_000)
        e.update(Signals(place = Place.HOME), 4_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
    }

    @Test fun carModel_detectsFromVehicleName() {
        assertEquals(CarModel.AVANTE, CarModel.detect("Hyundai ELANTRA"))
        assertEquals(CarModel.GRANDEUR, CarModel.detect("그랜저"))
        assertNull(CarModel.detect("Sonata"))
    }

    // ---------------------------------------------------------------- 추가 기능
    @Test fun exitCheck_warnsAtDoorOpen_andRemindsUnlockedAfterOneMinute() {
        val e = engine(Persona.SCHOOL_RUN)
        val seated = Signals(windowOpen = true, lowBeamOn = true)
        e.update(seated, 0)
        val fx = e.update(seated.copy(driverDoorOpen = true), 1_000)
        assertEquals(listOf("창문 열림", "전조등 켜짐"), e.state.exit.items)
        assertTrue(fx.any { it is Effect.Speak && "창문" in it.text })
        val away = Signals(driverSeated = false, ignitionOn = false, doorsLocked = false)
        e.update(away, 2_000)
        assertTrue(e.update(away, 30_000).none { it is Effect.Notify })
        assertTrue(e.update(away, 63_000).any { it is Effect.Notify && "잠기지" in it.title })
        assertTrue(e.update(away, 70_000).none { it is Effect.Notify })
    }

    @Test fun nHold_warnsThreeMinuteDoorRule_andLongHold() {
        val e = engine()
        e.startNHold()
        e.update(Signals(gear = Gear.N), 0)
        val fx = e.update(Signals(gear = Gear.N, ignitionOn = false, driverSeated = false), 1_000)
        assertEquals(NHoldPhase.HOLDING, e.state.nhold.phase)
        assertTrue(fx.any { it is Effect.Speak && "3분" in it.text })
        e.update(Signals(gear = Gear.P, ignitionOn = false, driverDoorOpen = true), 60_000)
        assertEquals(NHoldPhase.FAILED, e.state.nhold.phase)

        e.startNHold()
        e.update(Signals(gear = Gear.N), 100_000)
        e.update(Signals(gear = Gear.N, ignitionOn = false, driverSeated = false), 101_000)
        assertTrue(e.update(Signals(gear = Gear.N, ignitionOn = false, driverSeated = false), 101_000 + 30 * 60_000L).any { it is Effect.Notify })
    }

    @Test fun winterCheck_onlyBelowThreeDegrees_andJoinsRemoteNotice() {
        val e = engine()
        val cold = Signals(outsideTempC = -5f, washerLow = true, socPct = 25, driverSeated = false, ignitionOn = false, doorsLocked = true)
        assertEquals(listOf("워셔액 부족", "배터리·연료 25%", "앞 유리 성에 제거 켜기 추천"), e.winterCheck(cold, ComfortPreset.COOL))
        assertTrue(e.winterCheck(cold.copy(outsideTempC = 10f), ComfortPreset.COOL).isEmpty())
        e.update(cold, 0)
        val notice = e.startRemote(cold, 0).filterIsInstance<Effect.Notify>().single()
        assertTrue("출발 전 점검" in notice.body && "워셔액" in notice.body)
    }

    @Test fun parkingFloor_estimatesFromRampTurns_andLearnsPerPlace() {
        val e = engine(Persona.SCHOOL_RUN)
        fun descend(t0: Long, turns: Int) {
            e.update(Signals(place = Place.HOME, gear = Gear.D, speedKmh = 20f), t0)
            e.update(Signals(place = Place.OTHER, gpsLost = true, gear = Gear.D, speedKmh = 10f), t0 + 1_000)
            repeat(turns) { i ->
                e.update(Signals(place = Place.OTHER, gpsLost = true, gear = Gear.D, speedKmh = 10f, steeringDeg = 30f), t0 + 2_000 + i * 2_000L)
                e.update(Signals(place = Place.OTHER, gpsLost = true, gear = Gear.D, speedKmh = 10f, steeringDeg = 0f), t0 + 3_000 + i * 2_000L)
            }
            e.update(Signals(place = Place.OTHER, gpsLost = true), t0 + 20_000)
        }
        descend(0, 4)
        assertEquals(4, e.state.floor.estimate)
        e.confirmFloor(2)
        assertEquals(2f, e.settings.rampTurnsPerFloor[Place.HOME])
        // 지상으로 나갔다가 다시 같은 단지 지하로
        e.update(Signals(place = Place.HOME, gear = Gear.D, speedKmh = 10f), 40_000)
        descend(50_000, 4)
        assertEquals(2, e.state.floor.estimate)
    }

    @Test fun lendReport_factsOnly_whenOwnerSitsBack() {
        val e = engine(Persona.FAMILY_SHARE)
        e.update(Signals(), 0)
        e.startLend(Signals(), 0)
        e.update(Signals(driverSeated = false), 1_000)
        e.update(Signals(posture = Postures.SPOUSE), 2_000)
        e.update(Signals(posture = Postures.SPOUSE, gear = Gear.D, speedKmh = 60f), 4_500)
        e.update(Signals(posture = Postures.SPOUSE, gear = Gear.D, speedKmh = 40f), 5_500)
        e.update(Signals(posture = Postures.SPOUSE, odometerKm = 14830.4), 7_000)
        e.update(Signals(driverSeated = false, odometerKm = 14830.4), 8_000)
        val fx = e.update(Signals(posture = Postures.OWNER, odometerKm = 14830.4), 9_000)
        assertTrue(fx.any { it is Effect.Notify && "반납" in it.title })
        val report = e.state.lend.report.toMap()
        assertEquals("1회", report["급감속"])
        assertEquals("60km/h", report["최고 속도"])
        assertEquals("10.0km", report["이동 거리"])
        assertTrue(report.keys.none { "점수" in it })
    }
}
