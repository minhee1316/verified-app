package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 마이현대 불만(명령 실패를 늦게 앎, 메뉴 찾기 어려움)을 고친 기능. */
class MyHyundaiBetterTest {
    private fun engine() = VerdictEngine(Settings(personas = setOf(Persona.FAMILY_SHARE), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))

    @Test fun control_isConfirmedByVehicleSignal() {
        val e = engine()
        e.control(ControlKind.LOCK, Signals(doorsLocked = false), 0)
        assertEquals(ControlKind.LOCK, e.state.pendingControl?.kind)
        val fx = e.update(Signals(doorsLocked = true), 1_000)
        assertTrue(fx.any { it is Effect.Toast && "확인됨" in it.text })
        assertNull(e.state.pendingControl)
    }

    @Test fun control_noSignal_isUnconfirmed_notFailed_thenGivesUp() {
        val e = engine()
        e.update(Signals(acOn = true), 0)
        e.control(ControlKind.CLIMATE_OFF, Signals(acOn = true), 0)
        assertTrue(e.update(Signals(acOn = true), 5_000).none { it is Effect.Notify })
        // 신호가 아예 오지 않았으면 실패로 단정하지 않고 '결과 미확인'으로 더 기다린다
        val fx = e.update(Signals(acOn = true), VerdictEngine.verifyMs(ControlKind.CLIMATE_OFF))
        assertTrue(fx.any { it is Effect.Notify && it.level == Level.INFO && "결과 미확인" in it.title })
        assertTrue(fx.none { it is Effect.Notify && "실패" in it.title })
        assertEquals(true, e.state.pendingControl?.unconfirmed)
        assertEquals(ControlResult.UNCONFIRMED, e.state.controlLog.first().result)
        val end = e.update(Signals(acOn = true), VerdictEngine.CONTROL_LATE_MS)
        assertTrue(end.any { it is Effect.Notify && it.level == Level.WARN && "끝내 확인되지" in it.title })
        assertNull(e.state.pendingControl)
    }

    @Test fun control_oppositeFreshRead_isFailed() {
        val e = engine()
        e.control(ControlKind.LOCK, Signals(doorsLocked = false, seenAt = mapOf("lock" to 0L)), 1_000)
        // 보낸 뒤 차 신호를 다시 읽었는데(시각 9초) 여전히 잠금 해제면 실패
        val fx = e.update(Signals(doorsLocked = false, seenAt = mapOf("lock" to 10_000L)), 11_000)
        assertTrue(fx.any { it is Effect.Notify && "실패" in it.title && "잠금 해제" in it.title })
        assertNull(e.state.pendingControl)
        val ev = e.state.controlLog.first()
        assertEquals(ControlResult.FAILED, ev.result)
        assertTrue(ev.steps.first().second.contains("요청 보냄"))
        assertTrue(ev.steps.any { "1초 전 읽은 차 신호" in it.second || "방금 읽은 차 신호" in it.second })
    }

    @Test fun control_lateReflection_isReportedAsLateConfirmed() {
        val e = engine()
        e.control(ControlKind.LOCK, Signals(doorsLocked = false), 0)
        e.update(Signals(doorsLocked = false), VerdictEngine.verifyMs(ControlKind.LOCK))
        assertEquals(ControlResult.UNCONFIRMED, e.state.controlLog.first().result)
        val fx = e.update(Signals(doorsLocked = true, seenAt = mapOf("lock" to 40_000L)), 40_000)
        assertTrue(fx.any { it is Effect.Notify && "늦게 확인됨" in it.title })
        assertEquals(ControlResult.LATE, e.state.controlLog.first().result)
        assertNull(e.state.pendingControl)
    }

    @Test fun lend_reportKeepsProof_andUnreadSignalsAreNotFacts() {
        val e = engine()
        e.onCar = true
        // 실차에서 주행거리·시동·차속만 읽히고 창문·문·조향은 못 읽는 상황
        val seen = mapOf("odometer" to 1_000L, "ignition" to 1_000L, "speed" to 1_000L, "soc" to 1_000L)
        e.update(Signals(odometerKm = 100.0, seenAt = seen), 1_000)
        e.startLend(Signals(odometerKm = 100.0, seenAt = seen), 1_000)
        e.update(Signals(ignitionOn = false, odometerKm = 100.0, seenAt = seen), 2_000)
        e.update(Signals(ignitionOn = true, odometerKm = 100.0, seenAt = seen + ("ignition" to 3_000L)), 3_000)
        val fx = e.endLend(Signals(odometerKm = 112.6, seenAt = seen), 60_000)
        val r = e.state.lend.report.toMap()
        assertEquals("12.6km", r["이동 거리"])
        assertEquals("확인 불가", r["창문"])
        assertEquals("확인 불가", r["바퀴"])
        val p = e.state.lend.proof
        assertTrue(p.first().what.startsWith("넘김") && p.any { it.what.startsWith("돌려받음") })
        assertTrue(p.any { it.what == "시동 ON" && it.src.startsWith("차 신호") })
        assertTrue(fx.any { it is Effect.Notify && "근거" in it.body })
    }

    @Test fun horn_isNotWaitedFor() {
        val e = engine()
        e.control(ControlKind.HORN, Signals(), 0)
        assertNull(e.state.pendingControl)
    }

    @Test fun search_findsFeatureBySingleWord() {
        assertEquals(Module.DIAG.name, FeatureIndex.search("와이퍼").first().route)
        assertEquals(Module.VALET.name, FeatureIndex.search("대리").first().route)
        assertEquals("HAND_SERVICE", FeatureIndex.search("맡김").first().route)
        assertEquals("SERVICE", FeatureIndex.search("타이어 공기압").first().route)
        assertTrue(FeatureIndex.search("  ").isEmpty())
        assertTrue(FeatureIndex.search("우주선").isEmpty())
    }

    @Test fun situationDirectory_coversModulesAndSearchOpensTheSameDestinations() {
        val directory = FeatureIndex.ALL.filter { it.situation != null }
        assertEquals(Situation.entries.toSet(), directory.map { it.situation }.toSet())
        assertTrue(directory.map { it.route }.containsAll(Module.entries.map { it.name }))
        FeatureIndex.ALL.forEach { feature ->
            assertEquals(feature.route, FeatureIndex.search(feature.title).first().route)
        }
        assertEquals("HISTORY", FeatureIndex.search("맡김 · 이력서").first().route)
        assertEquals("HAND_SERVICE", FeatureIndex.search("정비 맡김").first().route)
        assertEquals("HAND_LEND", FeatureIndex.search("차 빌려주기").first().route)
    }
}
