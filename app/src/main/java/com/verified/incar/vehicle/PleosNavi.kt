package com.verified.incar.vehicle

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.verified.incar.core.Signals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * 현대 내비(Pleos NaviHelper SDK): 집 핀으로 목적지를 넣고, 안내 중 목적지·남은 거리·시간을 Signals.navi*에 넣는다(seenAt "navi").
 * 내비 서비스가 없는 폰·Robolectric·다른 차에서는 아무것도 하지 않는다.
 */
class PleosNavi(
    private val ctx: Context,
    private val signals: MutableStateFlow<Signals>,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val paused: () -> Boolean = { false },
) {
    val live: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var navi: NaviHelper? = null
    val connected get() = navi != null

    private val listener = object : NaviHelperEventListener {
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = drivingInfo.destination.let { put(it.name, it.distance, it.duration) }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = destinationInfo.let { put(it.name, it.remainDistance, it.remainTime) }
        override fun onRouteEnded() = clear()
        override fun onRouteCancelled() = clear()
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = clear()
    }

    fun connect(): Boolean {
        if (navi != null) return true
        if (!PleosBridge.available(ctx, NAVI_ACTION, NAVI_PKG)) return false
        val n = runCatching { NaviHelper(ctx) }.getOrNull() ?: return false
        // 리스너는 연결된 뒤에 건다(SDK 안내). 이미 안내 중인 목적지를 한 번 물어 둔다
        runCatching { n.initialize({ runCatching { n.addListener(listener); n.getDestinationInfo() } }, {}) }.getOrElse { return false }
        navi = n
        return true
    }

    fun disconnect() {
        runCatching { navi?.removeListener(listener) }
        runCatching { navi?.release() }
        navi = null
        live.clear()
    }

    /** 현대 내비에 목적지를 넣는다. 내비가 없거나 요청이 실패하면 false(결과는 onRouteStarted·onError로 온다). */
    fun routeHome(lat: Double, lon: Double, name: String): Boolean {
        val n = navi ?: return false
        // shortcut: POI가 아닌 좌표 목적지라 poiId·주소를 비워 보냄, 내비가 거부하면 즐겨찾기(BookmarkType.HOME) 조회로 바꾼다
        return runCatching {
            n.requestRoute(RouteInfo(latitude = lat, longitude = lon, displayLatitude = null, displayLongitude = null, poiName = name, poiId = "", address = "", poiSubId = "", routeOption = RouteOption.RECOMMENDED))
        }.isSuccess
    }

    private fun put(dest: String, remainM: Int, remainSec: Int) {
        if (paused()) return
        live += "navi"
        runCatching { signals.update { it.copy(seenAt = it.seenAt + ("navi" to clock())).let { s -> naviSignals(s, dest, remainM, remainSec) } } }
    }

    private fun clear() {
        if (paused()) return
        runCatching { signals.update { it.copy(naviDest = null, naviRemainKm = null, naviRemainMin = null, seenAt = it.seenAt + ("navi" to clock())) } }
    }

    companion object {
        const val NAVI_PKG = "ai.pleos.playground.service.navi"
        const val NAVI_ACTION = "ai.pleos.playground.intent.action.NAVI_START"

        /** 내비 값(m·초 [확인 필요: 단위는 Pleos 에뮬레이터에서 확인]) → Signals. 남은 시간은 올림 분. */
        fun naviSignals(s: Signals, dest: String, remainM: Int, remainSec: Int) =
            s.copy(naviDest = dest.ifBlank { null }, naviRemainKm = remainM.coerceAtLeast(0) / 1000f, naviRemainMin = (remainSec.coerceAtLeast(0) + 59) / 60)
    }
}
