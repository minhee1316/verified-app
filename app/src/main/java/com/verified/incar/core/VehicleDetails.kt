package com.verified.incar.core

enum class DoorPart(val areaId: Int, val label: String) {
    FRONT_LEFT(0x1, "운전석 문"),
    FRONT_RIGHT(0x4, "조수석 문"),
    REAR_LEFT(0x10, "뒷좌석 왼쪽 문"),
    REAR_RIGHT(0x40, "뒷좌석 오른쪽 문"),
    TRUNK(0x20000000, "트렁크"),
}

enum class SeatPart(val areaId: Int, val label: String) {
    DRIVER(0x1, "운전석"),
    PASSENGER(0x4, "조수석"),
    REAR_LEFT(0x10, "뒷좌석 왼쪽"),
    REAR_CENTER(0x20, "뒷좌석 가운데"),
    REAR_RIGHT(0x40, "뒷좌석 오른쪽"),
}

/** null은 해당 영역의 조회 실패 또는 지원 여부를 확인할 수 없다는 뜻이다. */
data class AreaValue(val value: Boolean?, val atMs: Long)

enum class DetailChannel { DOOR, LOCK, OCCUPANCY, BELT }

internal fun Signals.recordArea(channel: DetailChannel, areaId: Int, value: Boolean?, atMs: Long): Signals {
    val reading = AreaValue(value, atMs)
    val next = when (channel) {
        DetailChannel.DOOR -> {
            val part = DoorPart.entries.firstOrNull { it.areaId == areaId } ?: return this
            details.copy(doorOpen = details.doorOpen + (part to reading))
        }
        DetailChannel.LOCK -> {
            val part = DoorPart.entries.firstOrNull { it.areaId == areaId } ?: return this
            details.copy(doorLocked = details.doorLocked + (part to reading))
        }
        DetailChannel.OCCUPANCY -> {
            val part = SeatPart.entries.firstOrNull { it.areaId == areaId } ?: return this
            details.copy(occupied = details.occupied + (part to reading))
        }
        DetailChannel.BELT -> {
            val part = SeatPart.entries.firstOrNull { it.areaId == areaId } ?: return this
            details.copy(belted = details.belted + (part to reading))
        }
    }
    return copy(details = next)
}

data class VehicleDetails(
    val doorOpen: Map<DoorPart, AreaValue> = emptyMap(),
    val doorLocked: Map<DoorPart, AreaValue> = emptyMap(),
    val occupied: Map<SeatPart, AreaValue> = emptyMap(),
    val belted: Map<SeatPart, AreaValue> = emptyMap(),
) {
    fun seatLabel(seat: SeatPart): String = when (occupied[seat]?.value) {
        false -> "빈 좌석"
        true -> when (belted[seat]?.value) {
            true -> "착석 감지 · 벨트 체결"
            false -> "착석 감지 · 벨트 미체결"
            null -> "착석 감지 · 벨트 확인 불가"
        }
        null -> "착석 확인 불가"
    }

    fun doorLabel(door: DoorPart): String {
        val position = when (doorOpen[door]?.value) {
            true -> "열림"
            false -> "닫힘"
            null -> "열림 확인 불가"
        }
        val lock = when (doorLocked[door]?.value) {
            true -> "잠김"
            false -> "잠금 해제"
            null -> "잠금 확인 불가"
        }
        return "$position · $lock"
    }

    companion object {
        /** 실차가 없는 체험 화면에서만 사용한다. 묶음 신호의 위치 배정도 예시다. */
        fun example(s: Signals, atMs: Long): VehicleDetails {
            fun observed(value: Boolean) = AreaValue(value, atMs)
            val generated = VehicleDetails(
                doorOpen = DoorPart.entries.associateWith { observed(when (it) {
                    DoorPart.FRONT_LEFT -> s.driverDoorOpen
                    DoorPart.FRONT_RIGHT -> false
                    DoorPart.REAR_LEFT -> s.row2LeftDoorOpen
                    DoorPart.REAR_RIGHT -> s.row2RightDoorOpen
                    DoorPart.TRUNK -> s.trunkOpen
                }) },
                doorLocked = DoorPart.entries.associateWith { observed(s.doorsLocked) },
                occupied = SeatPart.entries.associateWith { observed(when (it) {
                    SeatPart.DRIVER -> s.driverSeated
                    SeatPart.PASSENGER -> s.passengerSeated
                    SeatPart.REAR_RIGHT -> s.row2Seated
                    else -> false
                }) },
                belted = SeatPart.entries.associateWith { observed(when (it) {
                    SeatPart.DRIVER -> true
                    SeatPart.PASSENGER -> s.passengerBelt
                    SeatPart.REAR_RIGHT -> s.row2Belt
                    else -> false
                }) },
            )
            return generated.copy(
                doorOpen = generated.doorOpen + s.details.doorOpen,
                doorLocked = generated.doorLocked + s.details.doorLocked,
                occupied = generated.occupied + s.details.occupied,
                belted = generated.belted + s.details.belted,
            )
        }
    }
}

data class CabinSetting(
    val power: Boolean,
    val cooling: Boolean,
    val temperatureC: Float,
    val fanSpeed: Int,
    val recirculate: Boolean,
    val defrost: Boolean,
    val driverHeat: Int,
) {
    fun validated() = copy(
        temperatureC = temperatureC.takeIf { it.isFinite() }?.coerceIn(16f, 30f) ?: 22f,
        fanSpeed = fanSpeed.coerceIn(0, 7),
        driverHeat = driverHeat.coerceIn(0, 3),
    )

    fun applyTo(s: Signals): Signals {
        val p = validated()
        return s.copy(hvacPowerOn = p.power, acOn = p.cooling, hvacTempC = p.temperatureC,
            fanSpeed = p.fanSpeed, recircOn = p.recirculate, defrosterOn = p.defrost,
            seatHeatDriver = p.driverHeat)
    }

    companion object {
        fun from(s: Signals) = CabinSetting(s.hvacPowerOn, s.acOn, s.hvacTempC, s.fanSpeed,
            s.recircOn, s.defrosterOn, s.seatHeatDriver)
    }
}
