package com.verified.incar

import com.verified.incar.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControlHistoryTest {
    private fun engine() = VerdictEngine(Settings(carModel = CarModel.GRANDEUR))

    @Test fun hornDoesNotReceivePendingLocksResult_andNewRequestEndsOldWait() {
        val e = engine()
        val s = Signals(doorsLocked = false)
        e.control(ControlKind.LOCK, s, 1000)
        e.control(ControlKind.HORN, s, 1100)
        e.update(s.copy(doorsLocked = true), 1200)
        assertEquals(ControlResult.NO_SIGNAL, e.state.controlLog[0].result)
        assertEquals(ControlResult.CONFIRMED, e.state.controlLog[1].result)
        e.control(ControlKind.UNLOCK, s.copy(doorsLocked = true), 1300)
        e.control(ControlKind.CLIMATE_ON, s, 1400)
        assertEquals(ControlResult.UNCONFIRMED, e.state.controlLog[1].result)
    }

    @Test fun recentThirtyPersistAndRestartDoesNotResumeOrConfirmWaitingCommand() {
        val app = RuntimeEnvironment.getApplication()
        val store = Store(app)
        val trial = Store(app, ns = Store.TRIAL_NS)
        val e = engine()
        repeat(35) { e.control(ControlKind.HORN, Signals(), 1000L + it) }
        e.control(ControlKind.UNLOCK, Signals(), 2000)
        assertEquals(30, e.state.controlLog.size)
        store.saveControls(e.state.controlLog)
        trial.saveControls(e.state.controlLog.take(1))
        val rebooted = engine()
        rebooted.loadControls(Store(app).loadControls())
        assertNull(rebooted.state.pendingControl)
        assertEquals(ControlResult.UNCONFIRMED, rebooted.state.controlLog.first().result)
        assertEquals(2000L, rebooted.state.controlLog.first().sentAtMs)
        store.clearRecords()
        assertTrue(store.loadControls().isEmpty())
        assertEquals(1, trial.loadControls().size)
    }
}

