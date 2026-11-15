package com.verified.incar

import android.Manifest
import android.location.Location
import android.location.LocationManager
import com.verified.incar.core.Pin
import com.verified.incar.core.Place
import com.verified.incar.core.Signals
import com.verified.incar.vehicle.PlaceTracker
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class LocationPermissionLifecycleTest {
    @Test fun permissionDeniedBeforeStart_keepsDemoSignalsIntact() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val signals = MutableStateFlow(Signals())
        val before = signals.value
        val tracker = PlaceTracker(app, signals) { emptyMap<Place, Pin>() to 100 }
        assertFalse(tracker.start())
        assertFalse(tracker.running)
        assertEquals(before, signals.value)
    }

    @Test fun permissionRevokedWhileRegistered_invalidatesFixAndCanRestart() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val signals = MutableStateFlow(Signals())
        val tracker = PlaceTracker(app, signals, { 1_000L }) { emptyMap<Place, Pin>() to 100 }
        assertTrue(tracker.start())
        val fix = Location(LocationManager.GPS_PROVIDER).apply { latitude = 37.5; longitude = 127.0; accuracy = 5f }
        val callback = PlaceTracker::class.java.getDeclaredMethod("onFix", Location::class.java).apply { isAccessible = true }
        callback.invoke(tracker, fix)
        assertEquals(1_000L, signals.value.seenAt["gps"])
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertFalse(tracker.start())
        assertFalse(tracker.running)
        assertNull(tracker.last)
        assertNull(signals.value.lat)
        assertNull(signals.value.seenAt["gps"])
        assertTrue(signals.value.gpsLost)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(tracker.start())
        assertNull(signals.value.seenAt["gps"])
        tracker.stop()
    }
}
