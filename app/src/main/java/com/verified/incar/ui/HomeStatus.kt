package com.verified.incar.ui

import com.verified.incar.UiState
import com.verified.incar.core.FeedCard
import com.verified.incar.core.HomeVerdict
import com.verified.incar.core.Level
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.DoorPart
import com.verified.incar.core.SeatPart
import com.verified.incar.core.VehicleDetails
import com.verified.incar.core.VehicleFocus

internal data class HomeCheck(
    val signal: String,
    val name: String,
    val value: String,
    val needsAttention: Boolean?,
    val route: String,
    val readAtMs: Long?,
    val id: String = signal,
    val covers: Set<String> = setOf(signal),
)

internal data class HomeStatus(val checks: List<HomeCheck>, val verdict: HomeVerdict, val alerts: List<HomeCheck> = emptyList()) {
    val visibleChecks get() = checks.filter { check -> alerts.none { check.signal in it.covers } } + alerts
    val checklistSummary: String
        get() {
            val issues = visibleChecks.count { it.needsAttention == true }
            val unknown = visibleChecks.count { it.needsAttention == null }
            return when {
                issues > 0 -> "확인 필요 ${issues}건"
                unknown > 0 -> "확인 불가 ${unknown}건"
                else -> "모두 정상"
            }
        }
}

private fun UiState.homeReadAt(key: String): Long? {
    if (!carConnected) return null
    fun read(group: String, reading: String = group) = signals.seenAt[reading].takeIf { group in liveSignals }
    return when (key) {
        "lock" -> read("lock", "lockStatus")
        "window" -> read("window", "windowStatus")
        "doors" -> read("doors", "trunk")
        "driverSeat" -> read("row2Occupancy", "driverSeat")
        "tire" -> listOfNotNull(read("tire"), read("tirePressure").takeIf {
            signals.tirePressureLow || signals.tireKpa.size == 4
        }).maxOrNull()
        else -> read(key)
    }
}

private fun UiState.canReadHomeSignal(key: String) = simAllowed || homeReadAt(key) != null

internal fun UiState.homeReading(key: String, value: String) = if (canReadHomeSignal(key)) value else "확인 불가"

internal fun UiState.homeStatus(): HomeStatus {
    val s = signals
    fun check(key: String, name: String, value: String, issue: Boolean?, route: String): HomeCheck =
        HomeCheck(key, name, homeReading(key, value), issue.takeIf { canReadHomeSignal(key) }, route, homeReadAt(key))

    // 시동이나 착석이 실제로 확인되었을 때만 잠금 해제를 탑승 중 상태로 해석한다.
    val inUse = (canReadHomeSignal("ignition") && s.ignitionOn) || (canReadHomeSignal("driverSeat") && s.driverSeated)
    val leftCar = canReadHomeSignal("ignition") && canReadHomeSignal("driverSeat") && !s.ignitionOn && !s.driverSeated
    val lockIssue = when {
        s.doorsLocked || inUse -> false
        leftCar -> true
        else -> null
    }
    val detail = when {
        carConnected -> s.details
        simAllowed -> s.details
        else -> VehicleDetails()
    }
    val alerts = buildList {
        DoorPart.entries.forEach { door ->
            val open = detail.doorOpen[door]
            val locked = detail.doorLocked[door]
            if (open?.value == true || (locked?.value == false && leftCar)) {
                val covers = buildSet {
                    if (locked?.value == false && leftCar) add("lock")
                    if (door == DoorPart.TRUNK && open?.value == true) add("doors")
                }
                add(HomeCheck("areaDoor_${door.name}", door.label, detail.doorLabel(door), true,
                    VehicleFocus(door = door).route, listOfNotNull(open, locked).maxOfOrNull { it.atMs },
                    id = "door_${door.name}", covers = covers))
            }
        }
        SeatPart.entries.forEach { seat ->
            val occupied = detail.occupied[seat]
            val belt = detail.belted[seat]
            if (occupied?.value == true && belt?.value == false) {
                add(HomeCheck("areaSeat_${seat.name}", seat.label, detail.seatLabel(seat), true,
                    VehicleFocus(seat = seat).route, listOfNotNull(occupied, belt).maxOfOrNull { it.atMs },
                    id = "seat_${seat.name}"))
            }
        }
    }
    val checks = listOf(
        check("lock", "문 잠금", when {
            s.doorsLocked -> "잠김"
            inUse -> "잠금 해제 · 사용 중"
            lockIssue == null -> "잠금 해제 · 탑승 미확인"
            else -> "잠금 해제"
        }, lockIssue, "CONTROL"),
        check("window", "창문", if (s.windowOpen) "열림" else "닫힘", s.windowOpen, "TRUNK"),
        check("doors", "트렁크", if (s.trunkOpen) "열림" else "닫힘", s.trunkOpen, "TRUNK"),
        check("tire", "타이어", if (s.tirePressureLow) "공기압 낮음" else "정상", s.tirePressureLow, "SERVICE"),
        check("warnings", "경고등", s.warnings.joinToString(" · ").ifEmpty { "없음" }, s.warnings.isNotEmpty(), "SERVICE"),
    )
    val covered = alerts.flatMap { it.covers }.toSet()
    val visibleFeed = feed.filter { (it.signal == null || canReadHomeSignal(it.signal)) && it.signal !in covered }
    val signalsWithAlerts = visibleFeed.filter { it.level == Level.WARN || it.level == Level.DANGER }.mapNotNull { it.signal }.toSet()
    val extraAlerts = (alerts + checks.filter { it.signal !in covered }).filter { it.needsAttention == true && it.signal !in signalsWithAlerts }.map {
        FeedCard("${it.name} ${it.value}", "", Level.WARN, it.route, it.signal)
    }
    val readings = (checks + alerts).mapNotNull { it.readAtMs } + listOfNotNull(homeReadAt("soc")) +
        visibleFeed.mapNotNull { it.signal?.let { key -> homeReadAt(key) } }
    val verdict = MyHyundai.verdict(visibleFeed + extraAlerts, readings.maxOrNull(), carConnected, simAllowed,
        checks.count { it.needsAttention == null })
    return HomeStatus(checks, verdict, alerts)
}
