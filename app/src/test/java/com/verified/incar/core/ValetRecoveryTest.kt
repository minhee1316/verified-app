package com.verified.incar.core

import org.junit.Assert.*
import org.junit.Test

/** 실제 차량과 같이 수신 시각이 있는 신호로 위치 보류·복구를 검증한다. */
class ValetRecoveryTest {
    private fun engine() = VerdictEngine().apply { onCar = true }
    private fun signal(at: Long, home: Boolean = true, ignition: Boolean = false) = Signals(
        place = if (home) Place.HOME else Place.OTHER, ignitionOn = ignition,
        seenAt = listOf("gps", "gear", "speed", "ignition", "doors").associateWith { at },
    )
    private fun restored(e: VerdictEngine, rebooted: Boolean = true) {
        e.recoverValet(ValetState(phase = ValetPhase.PRELIM, moved = true, prelimAtMs = 1), rebooted)
    }
    private fun assertNoResult(e: VerdictEngine, effects: List<Effect>) {
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        assertTrue(e.state.history.isEmpty())
        assertTrue(effects.none { it is Effect.Notify })
        assertNotNull(e.state.valet.verificationPending)
    }

    @Test fun firstFixMissing_doesNotDeclareOutsideEvenOnShutdown() {
        val e = engine(); restored(e)
        val s = signal(1000, home = false).copy(gpsLost = true, driverDoorOpen = true,
            seenAt = signal(1000).seenAt - "gps")
        assertNoResult(e, e.update(s, 1000, shutdown = true))
    }

    @Test fun missingGpsTimestamp_doesNotTrustDefaultHome() {
        val e = engine(); restored(e)
        assertNoResult(e, e.update(signal(1000).copy(seenAt = signal(1000).seenAt - "gps"), 1000))
    }

    @Test fun staleAndFutureFixes_doNotComplete() {
        for (fix in listOf(1L, 40_001L)) {
            val e = engine(); restored(e)
            assertNoResult(e, e.update(signal(40_000).copy(seenAt = signal(40_000).seenAt + ("gps" to fix)), 40_000))
        }
    }

    @Test fun gpsLostAfterPrelim_doesNotUseLastHome() {
        val e = engine(); restored(e)
        e.update(signal(1000, ignition = true), 1000)
        assertNoResult(e, e.update(signal(2000).copy(gpsLost = true, driverDoorOpen = true), 2000, shutdown = true))
    }

    @Test fun freshHomeAfterReboot_completesOnlyWithVehicleEvidence_andOnlyOnce() {
        val e = engine(); restored(e)
        e.update(signal(1000, ignition = true), 1000)
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        val fx = e.update(signal(2000), 2000)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertNull(e.state.valet.verificationPending)
        assertEquals(1, fx.count { it is Effect.Notify && it.level == Level.OK })
        assertTrue(e.update(signal(3000), 3000).none { it is Effect.Notify })
        assertEquals(1, e.state.history.size)
    }

    @Test fun freshOutsideAfterReboot_warnsOnlyAfterLocationReturns() {
        val e = engine(); restored(e)
        assertNoResult(e, e.update(signal(1000, home = false).copy(gpsLost = true), 1000))
        val fx = e.update(signal(2000, home = false), 2000)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.DANGER })
    }

    @Test fun recoveredMovingCar_resumesDrivingInsteadOfCompleting() {
        val e = engine(); restored(e)
        e.update(signal(1000).copy(gear = Gear.D, speedKmh = 20f, ignitionOn = true), 1000)
        assertEquals(ValetPhase.DRIVING, e.state.valet.phase)
        assertNull(e.state.valet.prelimAtMs)
        assertTrue(e.state.history.isEmpty())
    }

    @Test fun recovery_doesNotReusePreRebootStopDuration() {
        val e = engine()
        e.update(signal(100, ignition = true), 100)
        restored(e)
        e.update(signal(20_000, ignition = true).copy(driverDoorOpen = true), 20_000)
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        e.update(signal(30_000, ignition = true).copy(driverDoorOpen = true), 30_000)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
    }

    @Test fun locationCapabilityDenied_defersDespiteHomeValue() {
        val e = engine(); restored(e)
        e.caps = e.caps.copy(locationWithoutRoute = false)
        assertNoResult(e, e.update(signal(1000), 1000))
    }

    @Test fun freshOutsideWhileDriverStays_clearsGpsWaitWithoutWarning() {
        val e = engine(); restored(e)
        e.update(signal(1000, ignition = true).copy(gpsLost = true), 1000)
        val fx = e.update(signal(2000, home = false, ignition = true), 2000)
        assertEquals(ValetPhase.DRIVING, e.state.valet.phase)
        assertNull(e.state.valet.verificationPending)
        assertNull(e.state.valet.prelimAtMs)
        assertTrue(fx.none { it is Effect.Notify })
    }

    /** 좌석 구독은 됐지만 운전석 영역 값을 받지 못한 차(조수석 착좌만 주는 차)에서 앉아 있는 기사를 '하차'로 보지 않는다. */
    @Test fun seatGroupWithoutDriverArea_isNotDriverLeft() {
        val e = engine(); restored(e)
        val noDriverArea = signal(1000, home = false, ignition = true).copy(driverSeated = false,
            seenAt = signal(1000).seenAt + ("row2Occupancy" to 1000L))
        val fx = e.update(noDriverArea, 1000)
        assertNotEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx.none { it is Effect.Notify && it.level == Level.DANGER })
        // 운전석 영역 값까지 받은 뒤에는 실제 하차로 판정하고 근거 출처도 운전석 값으로 남긴다
        val fx2 = e.update(noDriverArea.copy(seenAt = noDriverArea.seenAt + ("driverSeat" to 2000L)), 2000)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx2.any { it is Effect.Notify && it.level == Level.DANGER })
        assertTrue(e.state.valet.proof.any { it.what == "운전석 하차" && it.src.startsWith("차 신호") })
    }

    /** 집 반경 경계에서 서 있는 차의 GPS가 흔들려도 '목적지 밖 하차'가 '완료'로 바뀌지 않는다. */
    @Test fun incompleteOutside_staysIncompleteWhenParkedFixJittersToHome() {
        val e = engine(); restored(e)
        val outside = signal(1000, home = false, ignition = true).copy(driverDoorOpen = true)
        assertTrue(e.update(outside, 1000).any { it is Effect.Notify && it.level == Level.DANGER })
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        val fx = e.update(signal(2000, home = true, ignition = true).copy(driverDoorOpen = true), 2000) +
            e.update(signal(13_000, home = true), 13_000, shutdown = true)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx.none { it is Effect.Notify && it.level == Level.OK })
        assertTrue(e.state.history.isEmpty())
    }

    /** 집에서 P단으로 1차 완료를 잡은 뒤 서 있는 차의 위치가 반경 밖으로 흔들려도 거짓 '집 밖 하차' 경고를 보내지 않고 완료한다. */
    @Test fun prelimAtHome_completesDespiteParkedFixJitteringOutside() {
        val e = engine(); restored(e)
        e.update(signal(1000, home = true, ignition = true), 1000)
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        val jitter = signal(2000, home = false, ignition = true).copy(driverDoorOpen = true)
        val fx = e.update(jitter, 2000)
        assertEquals(ValetPhase.PRELIM, e.state.valet.phase)
        val done = e.update(signal(12_000, home = false, ignition = true).copy(driverDoorOpen = true), 12_000)
        assertTrue((fx + done).none { it is Effect.Notify && it.level == Level.DANGER })
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertEquals(1, done.count { it is Effect.Notify && it.level == Level.OK })
        // 차가 다시 움직이면 집 판정 고정은 풀린다
        val moved = engine(); restored(moved)
        moved.update(signal(1000, home = true, ignition = true), 1000)
        moved.update(signal(2000, home = false, ignition = true).copy(gear = Gear.D, speedKmh = 20f), 2000)
        assertEquals(ValetPhase.DRIVING, moved.state.valet.phase)
        assertNull(moved.state.valet.prelimAtMs)
    }

    /** 인수인계서의 창문·트렁크·잠금은 영역을 다 읽은 값(windowStatus·trunk·lockStatus)일 때만 기록한다. */
    @Test fun carCheck_recordsWindowTrunkLockOnlyFromCompleteAreaReadings() {
        val e = engine()
        val partial = Signals(windowOpen = false, trunkOpen = false, doorsLocked = true,
            seenAt = mapOf("window" to 1L, "doors" to 1L, "lock" to 1L))
        val unknown = e.carCheck(partial, 1)
        assertNull(unknown.windowOpen)
        assertNull(unknown.trunkOpen)
        assertNull(unknown.doorsLocked)
        val read = e.carCheck(partial.copy(seenAt = partial.seenAt + mapOf("windowStatus" to 1L, "trunk" to 1L, "lockStatus" to 1L)), 1)
        assertEquals(false, read.windowOpen)
        assertEquals(false, read.trunkOpen)
        assertEquals(true, read.doorsLocked)
    }

    @Test fun recovery_preservesAlreadyConfirmedResult_withoutNewHistoryOrNotification() {
        val e = engine()
        val saved = ValetState(phase = ValetPhase.COMPLETE, confirmedBy = "시동 OFF")
        assertTrue(e.recoverValet(saved).isEmpty())
        assertEquals(saved, e.state.valet)
        assertTrue(e.state.history.isEmpty())
    }
}
