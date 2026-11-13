package com.verified.incar

import com.verified.incar.core.*
import com.verified.incar.ui.homeStatus
import org.junit.Assert.*
import org.junit.Test

class HomeAreaAlertTest {
    @Test fun homeWarningCarriesTheExactSeatDestination() {
        val details = VehicleDetails(
            occupied = mapOf(SeatPart.REAR_RIGHT to AreaValue(true, 1_000)),
            belted = mapOf(SeatPart.REAR_RIGHT to AreaValue(false, 1_000)))
        val u = UiState(simAllowed = true, signals = Signals(details = details))
        assertEquals("VEHICLE_VIEW/seat/REAR_RIGHT", u.homeStatus().verdict.tab)
    }

    @Test fun homeWarningCarriesTheExactUnlockedDoorDestination() {
        val details = VehicleDetails(
            doorOpen = mapOf(DoorPart.FRONT_RIGHT to AreaValue(false, 1_000)),
            doorLocked = mapOf(DoorPart.FRONT_RIGHT to AreaValue(false, 1_000)))
        val u = UiState(simAllowed = true, signals = Signals(ignitionOn = false, driverSeated = false, details = details))
        assertEquals("VEHICLE_VIEW/door/FRONT_RIGHT", u.homeStatus().verdict.tab)
    }

    @Test fun disconnectedAndUnknownSeatsCannotProduceALocationWarning() {
        val detail = VehicleDetails(belted = mapOf(SeatPart.REAR_RIGHT to AreaValue(false, 1_000)))
        assertTrue(UiState(simAllowed = true, signals = Signals(details = detail)).homeStatus().alerts.isEmpty())
        val complete = detail.copy(occupied = mapOf(SeatPart.REAR_RIGHT to AreaValue(true, 1_000)))
        assertTrue(UiState(simAllowed = false, signals = Signals(details = complete)).homeStatus().alerts.isEmpty())
    }

    @Test fun focusRoutesRoundTripAndRejectMalformedTargets() {
        for (door in DoorPart.entries) {
            val f = VehicleFocus(door = door)
            assertEquals(f, VehicleFocus.fromRoute(f.route))
        }
        for (seat in SeatPart.entries) {
            val f = VehicleFocus(seat = seat)
            assertEquals(f, VehicleFocus.fromRoute(f.route))
        }
        assertNull(VehicleFocus.fromRoute("VEHICLE_VIEW/seat/UNKNOWN"))
        assertNull(VehicleFocus.fromRoute("VEHICLE_VIEW/seat/DRIVER/extra"))
    }

    @Test fun aFrontDoorWarningDoesNotHideAnIndependentTrunkWarning() {
        val u = UiState(simAllowed = true, signals = Signals(trunkOpen = true,
            details = VehicleDetails(doorOpen = mapOf(DoorPart.FRONT_LEFT to AreaValue(true, 1_000)))))
        assertEquals("확인 필요 2건", u.homeStatus().verdict.word)
        assertTrue(u.homeStatus().visibleChecks.any { it.name == "트렁크" && it.needsAttention == true })
    }
}
