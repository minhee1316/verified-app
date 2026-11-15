package com.verified.incar.vehicle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Build
import com.verified.incar.core.Pin
import com.verified.incar.core.Place
import com.verified.incar.core.PlaceResolver
import com.verified.incar.core.Signals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차의 GPS로 등록 장소(집·학교·병원·마트)를 판정해 Signals.place에 넣는다.
 * Pleos FusedLocation SDK가 '준비 중'이라 AAOS 표준 LocationManager를 쓴다. 승인되면 이 클래스만 바꾼다.
 */
class PlaceTracker(
    private val ctx: Context,
    private val signals: MutableStateFlow<Signals>,
    /** 판정 시계(Runtime.now). 위치를 받은 시각을 Signals.seenAt["gps"]에 남긴다. */
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val pins: () -> Pair<Map<Place, Pin>, Int>,
) {
    var last: Location? = null
        private set
    private var lastFixMs = 0L
    private var lm: LocationManager? = null
    private val listener = LocationListener { onFix(it) }

    val running get() = lm != null
    /** 시연 시나리오 재생 중이면 true. 실제 위치가 재생 값(장소·GPS 끊김)을 덮어쓰지 않게 한다. */
    var paused = false

    fun start(): Boolean {
        // 설정에서 권한을 바꾼 뒤 돌아온 경우, 예전 등록 핸들만 보고 실행 중이라고 표시하지 않는다.
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stop()
            return false
        }
        if (running) return true
        val m = ctx.getSystemService(LocationManager::class.java) ?: return false
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val provider = providers.firstOrNull { m.isProviderEnabled(it) } ?: return false
        return runCatching {
            // 거리 조건 0: 서 있어도 5초마다 받아야 신호 대기·주차를 '지하(끊김)'로 오판하지 않는다
            m.requestLocationUpdates(provider, 5_000L, 0f, listener, Looper.getMainLooper())
            m.getLastKnownLocation(provider)?.let { last = it }
            lm = m
            true
        }.getOrDefault(false)
    }

    fun stop() {
        val hadLocation = lm != null || last != null
        // 권한이 사라진 기기에서도 정리가 실패해 앱 종료로 이어지지 않게 한다.
        runCatching { lm?.removeUpdates(listener) }
        lm = null
        last = null
        lastFixMs = 0L
        if (hadLocation) signals.update { it.copy(gpsLost = true, lat = null, lon = null, gpsAccuracyM = null, seenAt = it.seenAt - "gps") }
    }

    private fun onFix(loc: Location) {
        last = loc
        // 차 시계를 바꿔도 끊김 판정이 틀어지지 않도록 부팅 후 경과 시간으로 잰다
        lastFixMs = android.os.SystemClock.elapsedRealtime()
        if (paused) return
        val (p, radius) = pins()
        // 집 핀이 없어도 정비 맡김의 위치·정확도·수신 시각을 남긴다. 등록 장소 판정은 핀이 있을 때만 한다.
        signals.update { it.copy(
            place = if (p.isEmpty()) Place.OTHER else PlaceResolver.resolve(loc.latitude, loc.longitude, p, radius),
            gpsLost = false, lat = loc.latitude, lon = loc.longitude,
            gpsAccuracyM = loc.accuracy.takeIf { _ -> loc.hasAccuracy() },
            seenAt = it.seenAt + ("gps" to clock()),
        ) }
    }

    /** 30초 넘게 위치가 안 들어오면 지하로 들어간 것으로 보고 엔진의 '마지막 위치' 보정을 켠다. */
    fun checkLost() {
        if (!paused && running && lastFixMs > 0 && android.os.SystemClock.elapsedRealtime() - lastFixMs > LOST_MS) signals.update { if (it.gpsLost) it else it.copy(gpsLost = true) }
    }

    companion object {
        const val LOST_MS = 30_000L
    }
}
