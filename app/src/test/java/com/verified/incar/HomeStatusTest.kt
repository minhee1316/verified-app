package com.verified.incar

import com.verified.incar.core.EngineState
import com.verified.incar.core.Level
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.Settings
import com.verified.incar.core.Signals
import com.verified.incar.core.VerdictEngine
import com.verified.incar.ui.homeReading
import com.verified.incar.ui.homeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusTest {
    @Test fun tireAlertFromFeedAndChecklistIsCountedOnce() {
        val signals = Signals(tirePressureLow = true, seenAt = mapOf("tire" to 1_000L))
        val feed = MyHyundai.feed(signals, EngineState(), Settings(), emptyList())
        val status = UiState(carConnected = true, liveSignals = setOf("tire"), signals = signals, feed = feed).homeStatus()
        assertEquals("확인 필요 1건", status.verdict.word)
        assertEquals("확인 필요 1건", status.checklistSummary)
        assertEquals("SERVICE", status.verdict.tab)
    }

    @Test fun disconnectedProductionVehicleAndSubscriptionsWithoutReadingsShowUnknown() {
        val u = UiState(simAllowed = false, liveSignals = setOf("lock", "soc", "tire"))
        val status = u.homeStatus()
        assertEquals("확인 불가", u.homeReading("soc", "80%"))
        assertEquals("차량 미연결", status.verdict.word)
        assertEquals("확인 중", u.copy(carConnected = true).homeStatus().verdict.word)
        assertEquals("확인 불가 5건", status.checklistSummary)
        assertTrue(status.checks.all { it.needsAttention == null })
        assertFalse(status.verdict.basis.contains("시뮬레이터"))
    }

    @Test fun realWindowAlertOpensExitCheckAndUnknownItemsCannotBecomeNormal() {
        val u = UiState(simAllowed = false, carConnected = true, liveSignals = setOf("window"),
            signals = Signals(windowOpen = true, seenAt = mapOf("window" to 1000L, "windowStatus" to 1000L)))
        assertEquals("TRUNK", u.homeStatus().verdict.tab)
        assertEquals("확인 필요 1건", u.homeStatus().verdict.word)
        val closed = u.copy(signals = u.signals.copy(windowOpen = false)).homeStatus()
        assertEquals(Level.INFO, closed.verdict.level)
        assertEquals("확인 불가 4건", closed.checklistSummary)
    }

    @Test fun vhalTirePressureNeedsAllFourWheelsToConcludeNormal() {
        val u = UiState(simAllowed = false, carConnected = true, liveSignals = setOf("tirePressure"),
            signals = Signals(seenAt = mapOf("tirePressure" to 1000L)))
        assertEquals("확인 불가", u.homeReading("tire", "정상"))
        assertEquals("정상", u.copy(signals = u.signals.copy(tireKpa = List(4) { 240f })).homeReading("tire", "정상"))
        assertEquals("확인 필요 1건", u.copy(signals = u.signals.copy(tirePressureLow = true)).homeStatus().verdict.word)
    }

    @Test fun eachCheckUsesItsOwnLastReadingAndUnrelatedGpsCannotRefreshTheVerdict() {
        val lockAt = 120_000L
        val windowAt = 240_000L
        val u = UiState(simAllowed = false, carConnected = true, locationOn = true,
            liveSignals = setOf("window", "lock"), signals = Signals(
                seenAt = mapOf("windowStatus" to windowAt, "lockStatus" to lockAt, "gps" to 1_200_000L)))
        val status = u.homeStatus()
        assertEquals(lockAt, status.checks.single { it.signal == "lock" }.readAtMs)
        assertEquals(windowAt, status.checks.single { it.signal == "window" }.readAtMs)
        assertEquals(null, status.checks.single { it.signal == "doors" }.readAtMs)
        assertTrue(status.verdict.basis.contains(VerdictEngine.hhmm(windowAt)))
    }

    @Test fun onlyDemoLabelsExampleValues() {
        val offline = UiState(simAllowed = false, carConnected = false).homeStatus().verdict
        assertEquals("차량 미연결", offline.word)
        assertFalse(offline.basis.contains("예시"))
        assertTrue(UiState(simAllowed = true).homeStatus().verdict.basis.contains("예시 값"))
    }

    @Test fun aSingleDoorWindowOrRearSeatCannotConfirmTheWholeCar() {
        val s = Signals(doorsLocked = false, ignitionOn = false, driverSeated = false, seenAt = mapOf(
            "doors" to 1000L, "window" to 1000L, "lock" to 1000L, "row2Occupancy" to 1000L, "ignition" to 1000L))
        val u = UiState(simAllowed = false, carConnected = true, liveSignals = s.seenAt.keys, signals = s)
        val initial = u.homeStatus().checks.associateBy { it.signal }
        listOf("doors", "window", "lock").forEach { assertEquals("확인 불가", initial.getValue(it).value) }
        val partial = u.copy(signals = s.copy(seenAt = s.seenAt + ("lockStatus" to 1000L))).homeStatus()
        assertEquals(null, partial.checks.single { it.signal == "lock" }.needsAttention)
    }
}
