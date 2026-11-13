package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceResolverTest {
    private val home = Pin(37.5665, 126.9780)
    private val school = Pin(37.5700, 126.9800)

    @Test fun distance_isAboutRight() {
        // 위도 0.001도 ≈ 111m
        val d = PlaceResolver.distanceM(37.5665, 126.9780, 37.5675, 126.9780)
        assertTrue(d in 105.0..117.0)
    }

    @Test fun resolve_picksNearestPinInsideRadius() {
        val pins = mapOf(Place.HOME to home, Place.SCHOOL to school)
        assertEquals(Place.HOME, PlaceResolver.resolve(37.5668, 126.9781, pins, 100))
        assertEquals(Place.SCHOOL, PlaceResolver.resolve(37.5699, 126.9800, pins, 100))
        assertEquals(Place.OTHER, PlaceResolver.resolve(37.6000, 127.0000, pins, 100))
        assertEquals(Place.OTHER, PlaceResolver.resolve(37.5668, 126.9781, emptyMap(), 100))
    }
}
