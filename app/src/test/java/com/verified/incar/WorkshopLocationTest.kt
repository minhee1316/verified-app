package com.verified.incar

import android.location.Location
import com.verified.incar.core.Place
import com.verified.incar.core.Signals
import com.verified.incar.vehicle.PlaceTracker
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkshopLocationTest {
    @Test fun workshopGpsGetsAccuracyAndTimeWithoutAHomePin() {
        val signals = MutableStateFlow(Signals(place = Place.HOME))
        val tracker = PlaceTracker(RuntimeEnvironment.getApplication(), signals, { 1_000L }) { emptyMap<com.verified.incar.core.Place, com.verified.incar.core.Pin>() to 100 }
        val fix = Location("gps").apply { latitude = 37.5; longitude = 127.0; accuracy = 8f }
        val callback = PlaceTracker::class.java.getDeclaredMethod("onFix", Location::class.java).apply { isAccessible = true }
        callback.invoke(tracker, fix)
        assertEquals(8f, signals.value.gpsAccuracyM)
        assertEquals(1_000L, signals.value.seenAt["gps"])
        assertEquals(Place.OTHER, signals.value.place)
        tracker.paused = true
        callback.invoke(tracker, Location(fix).apply { latitude = 38.0 })
        assertEquals(37.5, signals.value.lat!!, .0001)
    }
}
