package com.verified.incar.core

import org.junit.Assert.*
import org.junit.Test

class VehicleDetailsTest {
    @Test fun oneDoorReadingDoesNotMarkOtherDoorsLocked_andFailureInvalidatesOnlyItsArea() {
        var s = Signals().recordArea(DetailChannel.LOCK, 0x1, true, 10L)
            .recordArea(DetailChannel.LOCK, 0x4, false, 11L)
            .recordArea(DetailChannel.DOOR, 0x4, false, 12L)
        assertEquals("닫힘 · 잠금 해제", s.details.doorLabel(DoorPart.FRONT_RIGHT))
        assertEquals("열림 확인 불가 · 잠금 확인 불가", s.details.doorLabel(DoorPart.REAR_RIGHT))
        s = s.recordArea(DetailChannel.LOCK, 0x4, null, 20L)
        assertEquals(null, s.details.doorLocked[DoorPart.FRONT_RIGHT]?.value)
        assertEquals(20L, s.details.doorLocked[DoorPart.FRONT_RIGHT]?.atMs)
        assertEquals(true, s.details.doorLocked[DoorPart.FRONT_LEFT]?.value)
        assertEquals(s, s.recordArea(DetailChannel.LOCK, 0, true, 30L))
    }

    @Test fun beltsAreAssociatedWithTheSameOccupiedSeat() {
        val s = Signals().recordArea(DetailChannel.OCCUPANCY, 0x10, true, 1)
            .recordArea(DetailChannel.OCCUPANCY, 0x40, true, 1)
            .recordArea(DetailChannel.BELT, 0x10, true, 2)
            .recordArea(DetailChannel.BELT, 0x40, false, 2)
            .recordArea(DetailChannel.OCCUPANCY, 0x4, false, 3)
        assertEquals("착석 감지 · 벨트 체결", s.details.seatLabel(SeatPart.REAR_LEFT))
        assertEquals("착석 감지 · 벨트 미체결", s.details.seatLabel(SeatPart.REAR_RIGHT))
        assertEquals("빈 좌석", s.details.seatLabel(SeatPart.PASSENGER))
        assertEquals("착석 확인 불가", s.details.seatLabel(SeatPart.REAR_CENTER))
    }

    @Test fun cabinBoundsAndCoolingAreIndependentOfHeatingPower() {
        val heating = CabinSetting(true, false, 99f, 99, false, true, -2).applyTo(Signals())
        assertTrue(heating.hvacPowerOn)
        assertFalse(heating.acOn)
        assertEquals(30f, heating.hvacTempC)
        assertEquals(7, heating.fanSpeed)
        assertEquals(0, heating.seatHeatDriver)
        val invalid = CabinSetting.from(heating).copy(temperatureC = Float.NaN).validated()
        assertEquals(22f, invalid.temperatureC)
    }
}
