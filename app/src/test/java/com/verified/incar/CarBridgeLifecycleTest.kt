package com.verified.incar

import android.content.pm.PackageManager
import com.verified.incar.core.AreaValue
import com.verified.incar.core.DoorPart
import com.verified.incar.core.Signals
import com.verified.incar.core.VehicleDetails
import com.verified.incar.vehicle.CarBridge
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class CarBridgeLifecycleTest {
    @Test fun phoneWithoutCarService_connectDoesNotLoadCarApiOrChangeExample() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false)
        val signals = MutableStateFlow(Signals())
        val before = signals.value
        val bridge = CarBridge(app, signals)
        assertFalse(bridge.connect())
        assertFalse(bridge.connected)
        assertEquals(before, signals.value)
    }

    @Test fun disconnect_invalidatesReceivedVehicleReadingsAndCapabilities() {
        val signals = MutableStateFlow(Signals(
            seenAt = mapOf("speed" to 10L, "doors" to 10L, "lockStatus" to 10L, "gps" to 12L),
            details = VehicleDetails(doorLocked = mapOf(DoorPart.FRONT_LEFT to AreaValue(true, 10L))),
        ))
        val bridge = CarBridge(RuntimeEnvironment.getApplication(), signals)
        bridge.live += setOf("speed", "doors")
        bridge.disconnect()
        assertFalse(bridge.connected)
        assertTrue(bridge.live.isEmpty())
        assertTrue(signals.value.details.doorLocked.isEmpty())
        assertEquals(mapOf("gps" to 12L), signals.value.seenAt)
        assertFalse(bridge.caps.speed)
        assertFalse(bridge.caps.seatHeat)
        // 두 번 정리해도 남의 GPS 타임스탬프나 미수신 상태를 바꾸지 않는다.
        bridge.disconnect()
        assertEquals(mapOf("gps" to 12L), signals.value.seenAt)
    }
}
