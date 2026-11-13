package com.verified.incar.core

/** 경고의 위치를 화면 이동과 차주 인증 후에도 그대로 전달한다. */
data class VehicleFocus(val door: DoorPart? = null, val seat: SeatPart? = null) {
    val route: String get() = when {
        door != null -> "VEHICLE_VIEW/door/${door.name}"
        seat != null -> "VEHICLE_VIEW/seat/${seat.name}"
        else -> "VEHICLE_VIEW"
    }
    val entryDoor: DoorPart get() = door ?: when (seat) {
        SeatPart.PASSENGER -> DoorPart.FRONT_RIGHT
        SeatPart.REAR_LEFT -> DoorPart.REAR_LEFT
        SeatPart.REAR_CENTER, SeatPart.REAR_RIGHT -> DoorPart.REAR_RIGHT
        else -> DoorPart.FRONT_LEFT
    }

    companion object {
        fun fromRoute(route: String): VehicleFocus? {
            if (route == "VEHICLE_VIEW") return VehicleFocus()
            val pieces = route.split('/')
            if (pieces.size != 3 || pieces[0] != "VEHICLE_VIEW") return null
            return when (pieces[1]) {
                "door" -> DoorPart.entries.firstOrNull { it.name == pieces[2] }?.let { VehicleFocus(door = it) }
                "seat" -> SeatPart.entries.firstOrNull { it.name == pieces[2] }?.let { VehicleFocus(seat = it) }
                else -> null
            }
        }
    }
}
