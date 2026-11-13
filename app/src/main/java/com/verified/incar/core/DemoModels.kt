package com.verified.incar.core

/** 체험·시연 화면의 예시 충전소. [km]는 출발지부터의 거리, [bolts]는 충전 속도 단계, [free]는 빈자리. */
data class EvCharger(val km: Double, val bolts: Int, val free: Int, val x: Float, val y: Float)

/** 체험·시연용 전기차 경로 계산. 고도는 앱이 읽을 수 없어 예시 값이고, 소모 계수는 [제안값]이다. */
object EvRoute {
    const val TOTAL_KM = 180.0
    /** 15km 간격 고도(m). */
    val ELEVATION = listOf(120, 180, 260, 420, 610, 540, 700, 860, 640, 480, 330, 250, 200)
    private const val STEP_KM = 15.0
    private const val CLIMB_PCT_PER_M = 0.01
    private const val REGEN_PCT_PER_M = 0.005
    val CHARGERS = listOf(
        EvCharger(32.0, 1, 2, 0.24f, 0.62f), EvCharger(62.0, 3, 0, 0.40f, 0.42f),
        EvCharger(95.0, 2, 1, 0.56f, 0.30f), EvCharger(140.0, 3, 3, 0.80f, 0.24f),
    )

    /** [ELEVATION] 지점마다의 배터리 %. 0 아래로도 계산해 부족한 정도를 판단할 수 있게 둔다. */
    fun batteryAlong(startPct: Double, cold: Boolean): List<Double> {
        val range = MyHyundai.RANGE_KM_FULL * (if (cold) MyHyundai.COLD_RANGE_FACTOR else 1.0)
        return ELEVATION.zipWithNext().runningFold(startPct) { pct, (a, b) ->
            val rise = (b - a).toDouble()
            pct - STEP_KM / range * 100 - (if (rise > 0) rise * CLIMB_PCT_PER_M else rise * REGEN_PCT_PER_M)
        }
    }

    /** 맑은 날 평지 기준보다 더 쓰는 %. */
    fun extraUse(startPct: Double, cold: Boolean) = startPct - batteryAlong(startPct, cold).last() - TOTAL_KM / MyHyundai.RANGE_KM_FULL * 100

    fun batteryAt(along: List<Double>, km: Double): Double {
        val pos = (km / STEP_KM).coerceIn(0.0, (along.size - 1).toDouble())
        val i = pos.toInt().coerceAtMost(along.size - 2)
        return along[i] + (along[i + 1] - along[i]) * (pos - i)
    }

    fun level(pct: Double) = when {
        pct >= 50 -> Level.OK
        pct >= 20 -> Level.WARN
        else -> Level.DANGER
    }

    /** 빈자리가 있고 배터리 10% 이상으로 닿는 충전소 중 가장 먼 곳. */
    fun suggestedStop(along: List<Double>) = CHARGERS.lastOrNull { it.free > 0 && batteryAt(along, it.km) >= MyHyundai.TRIP_LOW_PCT }

    fun nextCharger() = CHARGERS.first { it.free > 0 }
}

enum class Fence { INSIDE, GPS_NOISE, BREACH }

/** 시동이 켜져 있고 주행거리가 늘며 반경을 벗어났을 때만 이탈로 본다. 나머지 이탈은 GPS 오차로 다룬다. */
fun fenceVerdict(outside: Boolean, ignitionOn: Boolean, movedKm: Double) = when {
    !outside -> Fence.INSIDE
    ignitionOn && movedKm > 0 -> Fence.BREACH
    else -> Fence.GPS_NOISE
}
