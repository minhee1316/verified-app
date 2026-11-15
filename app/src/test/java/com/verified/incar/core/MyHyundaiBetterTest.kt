package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** 반납 때 근거를 한도에 맞게 줄여도 첫 줄 '넘김'(인수 상태)은 남는다. */
    @Test fun lendReport_keepsHandoverLineEvenWithManyProofLines() {
        val e = engine()
        e.startLend(Signals(), 1_000)
        // 맡긴 동안 근거 줄을 한도보다 많이 쌓는다(체크리스트를 켰다 껐다)
        repeat(2 * VerdictEngine.PROOF_MAX) { i -> e.togglePrep(PrepItem.BUILTIN_CAM, 2_000L + i, valet = false) }
        e.endLend(Signals(), 60_000)
        val record = e.state.history.single()
        assertTrue(record.proof.first().what.startsWith("넘김"))
        assertTrue(record.proof.any { it.what.startsWith("돌려받음") })
        assertTrue(record.proof.size <= VerdictEngine.PROOF_MAX)
        assertTrue(e.state.lend.proof.first().what.startsWith("넘김"))
    }

    /** 반납 확인·대리 완료 뒤에 한 '돌려받을 때' 체크도 그 이력서 기록(상세·PDF)에 남는다. */
    @Test fun returnChecklist_afterRecordIsWritten_isKeptInTheRecord() {
        val e = engine()
        e.startLend(Signals(), 1_000, HandKind.SERVICE)
        e.endLend(Signals(), 60_000)
        e.togglePrep(PrepItem.CAM_SAVE, 70_000, valet = false)
        assertEquals(1, e.state.history.single().proof.count { it.what == "${PrepItem.CAM_SAVE.done} · 차주 확인" })
        // 체크를 풀어도 이미 남긴 확인 기록은 지우지 않는다(맡긴 동안과 같은 규칙)
        e.togglePrep(PrepItem.CAM_SAVE, 71_000, valet = false)
        assertEquals(1, e.state.history.single().proof.count { it.what.startsWith(PrepItem.CAM_SAVE.done) })

        // 근거가 한도(20줄)까지 찬 기록에 체크를 더해도 이미 남긴 근거 줄은 지우지 않고, 같은 확인은 한 번만 남긴다
        val full = engine()
        full.startLend(Signals(), 1_000)
        repeat(2 * VerdictEngine.PROOF_MAX) { i -> full.togglePrep(PrepItem.BUILTIN_CAM, 2_000L + i, valet = false) }
        full.endLend(Signals(), 60_000)
        val saved = full.state.history.single().proof
        repeat(3) { i -> full.togglePrep(PrepItem.CAM_SAVE, 70_000L + i, valet = false) }
        val after = full.state.history.single().proof
        assertEquals(saved, after.dropLast(1))
        assertEquals(1, after.count { it.what.startsWith(PrepItem.CAM_SAVE.done) })

        val v = engine()
        v.startValet()
        v.confirmValetConsent(Signals(), 1_000)
        v.update(Signals(gear = Gear.D, speedKmh = 30f, place = Place.OTHER), 2_000)
        v.update(Signals(place = Place.HOME), 3_000)
        v.update(Signals(place = Place.HOME, ignitionOn = false), 4_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, v.state.valet.phase)
        v.togglePrep(PrepItem.KEY_REVOKE, 5_000, valet = true)
        assertTrue(v.state.history.single { it.kind == "대리" }.proof.any { it.what == "${PrepItem.KEY_REVOKE.done} · 차주 확인" })
    }

    /** 시연 앱을 다시 켜 예시 신호가 기본값(차주 착석)으로 돌아가도 진행 중인 빌려주기를 혼자 반납 처리하지 않는다. */
    @Test fun restoredTrialLend_isNotAutoReturnedByResetExampleSignals() {
        val e = engine()
        e.loadSession(null, null, LendState(active = true, kind = HandKind.LEND, otherSeen = true, start = Snapshot(1_000, 80, 100.0, false, 0f)))
        e.update(Signals(), 10_000)
        assertTrue(e.state.lend.active)
        assertTrue(e.state.history.isEmpty())
        // 실제로 내렸다가 차주가 다시 앉으면 지금처럼 반납으로 본다
        e.update(Signals(driverSeated = false), 11_000)
        e.update(Signals(), 12_000)
        assertFalse(e.state.lend.active)
        assertEquals(1, e.state.history.size)
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
