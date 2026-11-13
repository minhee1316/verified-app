package com.verified.incar.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 사용자가 등록한 장소 좌표. 차 안에만 저장하고 서버로 보내지 않는다. */
data class Pin(val lat: Double, val lon: Double)

object PlaceResolver {
    /** 두 좌표 사이 거리(m), 하버사인. */
    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /** 반경 안의 가장 가까운 등록 장소, 없으면 OTHER. */
    fun resolve(lat: Double, lon: Double, pins: Map<Place, Pin>, radiusM: Int): Place =
        pins.map { (place, pin) -> place to distanceM(lat, lon, pin.lat, pin.lon) }
            .filter { it.second <= radiusM }
            .minByOrNull { it.second }?.first ?: Place.OTHER
}
