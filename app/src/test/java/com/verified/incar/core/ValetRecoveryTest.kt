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

    @Test fun recovery_preservesAlreadyConfirmedResult_withoutNewHistoryOrNotification() {
        val e = engine()
        val saved = ValetState(phase = ValetPhase.COMPLETE, confirmedBy = "시동 OFF")
        assertTrue(e.recoverValet(saved).isEmpty())
        assertEquals(saved, e.state.valet)
        assertTrue(e.state.history.isEmpty())
    }
}
