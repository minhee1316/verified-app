package com.verified.incar

import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import com.verified.incar.core.Signals
import com.verified.incar.vehicle.PleosBridge
import com.verified.incar.vehicle.PleosNavi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pleos SDK 연결: Pleos 서비스가 없는 곳(폰·JVM)에서는 아무것도 하지 않고, SDK 값은 정해진 대로 Signals로 옮긴다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PleosBridgeTest {
    @Test fun noPleosService_connectIsNoOp() {
        val ctx = RuntimeEnvironment.getApplication()
        val signals = MutableStateFlow(Signals())
        val before = signals.value
        val car = PleosBridge(ctx, signals, { 1L })
        val navi = PleosNavi(ctx, signals, { 1L })
        assertFalse(car.connect())
        assertFalse(navi.connect())
        assertFalse(navi.routeHome(37.5, 127.0, "집"))
        car.disconnect(); navi.disconnect()
        assertFalse(car.connected || navi.connected)
        assertTrue(car.live.isEmpty() && navi.live.isEmpty())
        assertEquals(before, signals.value)
    }

    /** 끊긴 Pleos·내비 연결에서 받은 값은 '받음'으로 남기지 않는다. CarBridge가 읽은 값은 건드리지 않는다. */
    @Test fun disconnect_forgetsReadingsFromTheClosedConnection() {
        val ctx = RuntimeEnvironment.getApplication()
        val signals = MutableStateFlow(Signals(naviDest = "집", naviRemainKm = 3f, naviRemainMin = 5,
            seenAt = mapOf("doors" to 1L, "soc" to 1L, "navi" to 1L, "speed" to 1L)))
        val pleos = PleosBridge(ctx, signals, { 1L })
        val navi = PleosNavi(ctx, signals, { 1L })
        pleos.live += setOf("doors", "soc")
        navi.live += "navi"
        pleos.disconnect(); navi.disconnect()
        val s = signals.value
        assertEquals(setOf("speed"), s.seenAt.keys)
        assertNull(s.naviDest)
        assertNull(s.naviRemainKm)
        assertNull(s.naviRemainMin)
        assertTrue(pleos.live.isEmpty() && navi.live.isEmpty())
    }

    @Test fun telltales_mapToWarnings_tpmsSeparate() {
        fun t(type: TelltaleType, st: TelltaleState) = TelltaleData(type, st, 0, 0, 0)
        val list = listOf(
            t(TelltaleType.ENGINE, TelltaleState.ON), t(TelltaleType.AIRBAG, TelltaleState.BLINK), t(TelltaleType.ABS, TelltaleState.OFF),
            // 평소에도 켜지는 표시등과 TPMS는 경고등 목록에 넣지 않는다
            t(TelltaleType.PARKING_BRAKE, TelltaleState.ON), t(TelltaleType.TPMS, TelltaleState.ON),
        )
        assertEquals(setOf("엔진", "에어백"), PleosBridge.warnings(list))
        assertTrue(PleosBridge.tpmsOn(list))
        assertFalse(PleosBridge.tpmsOn(list.take(3)))
    }

    @Test fun oilLevel_unknownStaysNull() {
        assertEquals(true, PleosBridge.oilLow(OilLevelState.CRITICALLY_LOW))
        assertEquals(false, PleosBridge.oilLow(OilLevelState.NORMAL))
        assertNull(PleosBridge.oilLow(OilLevelState.ERROR))
        assertNull(PleosBridge.oilLow(null))
    }

    @Test fun navi_metersSecondsToKmMin() {
        val s = PleosNavi.naviSignals(Signals(), "집", 12_400, 1_030)
        assertEquals("집", s.naviDest)
        assertEquals(12.4f, s.naviRemainKm!!, 0.001f)
        assertEquals(18, s.naviRemainMin)
        assertNull(PleosNavi.naviSignals(Signals(), " ", 0, 0).naviDest)
    }
}
