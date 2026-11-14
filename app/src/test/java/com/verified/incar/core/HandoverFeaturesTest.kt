package com.verified.incar.core

import android.content.Context
import com.verified.incar.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 인수인계서·대리 내비·현대 기능 체크리스트·정비 이탈·이력서·정차 배터리(엔진). */
class HandoverFeaturesTest {
    private fun settings(pins: Map<Place, Pin> = emptyMap()) =
        Settings(personas = setOf(Persona.NIGHT_OUT), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR, pins = pins)

    private fun engine(pins: Map<Place, Pin> = emptyMap()) = VerdictEngine(settings(pins))

    private val driving = Signals(gear = Gear.D, speedKmh = 30f, place = Place.OTHER)
    private val parkedHome = driving.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOME)

    private fun valet(e: VerdictEngine, s: Signals = driving): List<Effect> {
        e.update(s, 0)
        e.startValet()
        return e.confirmValetConsent(s, 0)
    }

    private fun List<Effect>.notices(word: String) = filterIsInstance<Effect.Notify>().filter { word in it.title }

    @Test fun handover_duplicateActionsPreserveTheOriginalRecordAndNotifyOnce() {
        val e = engine()
        assertEquals(1, e.startLend(parkedHome, 100, HandKind.SERVICE).notices("정비 맡기기 시작").size)
        assertTrue(e.startLend(driving, 200).filterIsInstance<Effect.Notify>().isEmpty())
        assertTrue(e.startValet().any { it is Effect.Toast })
        e.confirmValetConsent(driving, 300)
        assertEquals(ValetPhase.IDLE, e.state.valet.phase)
        assertEquals(HandKind.SERVICE, e.state.lend.kind)
        assertEquals(100L, e.state.lend.start!!.timeMs)
        assertEquals(1, e.endLend(parkedHome, 400).notices("인수 리포트").size)
        assertTrue(e.endLend(parkedHome, 500).isEmpty())
        assertEquals(1, e.state.history.size)

        e.startValet()
        e.startLend(parkedHome, 600)
        assertFalse(e.state.lend.active)
        assertEquals(1, e.confirmValetConsent(parkedHome, 700).notices("대리운전 시작").size)
        assertTrue(e.confirmValetConsent(driving, 800).isEmpty())
        assertEquals(700L, e.state.valet.handover!!.timeMs)
    }

    @Test fun lateFirstReading_isNeitherTravelNorEnergyConsumption() {
        val e = engine()
        e.onCar = true
        val initial = Signals()
        e.update(initial, 0)
        e.startLend(initial, 0, HandKind.SERVICE)
        val observed = initial.copy(odometerKm = 80_000.0, socPct = 50, seenAt = mapOf("odometer" to 1_000L, "soc" to 1_000L))
        assertTrue(e.update(observed, 1_000).notices("정비소를 벗어났어요").isEmpty())
        e.endLend(observed, 2_000)
        val report = e.state.lend.report.toMap()
        assertEquals("확인 불가", report["이동 거리"])
        assertEquals("확인 불가", report["배터리·연료"])
        assertEquals("확인 불가", report["급감속"])
        assertNull(report["정차 중 배터리 사용"])
        assertNull(e.state.history.single().km)
    }

    @Test fun ownerCanReturnWithoutVehicleSignals_withoutCertifyingParking() {
        val e = engine()
        e.onCar = true
        e.startValet()
        e.confirmValetConsent(parkedHome, 1_000)
        e.update(driving.copy(seenAt = mapOf("speed" to 2_000L)), 2_000)
        e.update(parkedHome.copy(ignitionOn = false), 3_000)
        assertEquals(ValetPhase.DRIVING, e.state.valet.phase)
        assertTrue(e.manualValetReturn(driving, 4_000).notices("반납").isEmpty())
        assertEquals(1, e.manualValetReturn(parkedHome, 5_000).notices("차주 직접 확인").size)
        assertFalse(e.state.valet.active)
        assertFalse(e.state.history.single().ok)
        assertTrue(e.state.history.single().changes.any { "차주가 직접" in it })
        assertTrue(e.manualValetReturn(parkedHome, 6_000).isEmpty())
    }

    // ---------------------------------------------------------------- 2. 인수인계서
    @Test fun valetHandoverSheet_showsChanges_toOwner_proof_andHistory() {
        val e = engine()
        valet(e)
        assertNotNull(e.state.valet.checkIn)
        e.update(driving, 1_000)
        val arrived = parkedHome.copy(odometerKm = 14832.8, socPct = 71, warnings = setOf("엔진"))
        e.update(arrived, 2_000)
        val fx = e.update(arrived.copy(ignitionOn = false), 4_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        val diff = VerdictEngine.diffChecks(e.state.valet.checkIn!!, e.state.valet.checkOut!!)
        assertTrue(diff.toString(), "경고등 새로 켜짐: 엔진" in diff)
        assertTrue(diff.toString(), "주행거리 +12.4km" in diff)
        assertTrue(diff.toString(), "배터리 80% → 71%" in diff)
        assertTrue(fx.notices("대리 주차 완료").single().body.contains("바뀐 것: 경고등 새로 켜짐: 엔진"))
        assertTrue(e.state.valet.proof.any { it.what.startsWith("바뀐 것:") })
        val h = e.state.history.single()
        assertEquals("대리", h.kind)
        assertEquals(12.4, h.km!!, 0.01)
        // 새 경고등이 켜졌으면 '문제 없이 돌려받음'으로 세지 않는다
        assertFalse(h.ok)
    }

    @Test fun diff_noChange_and_unreadSignalsOnCarAreHonestlyUnknown() {
        val c = CarCheck(0, 100.0, 80, emptySet(), false, false, false, false, false, true)
        assertEquals(listOf(VerdictEngine.NO_CHANGE), VerdictEngine.diffChecks(c, c.copy(atMs = 1)))
        val e = engine()
        e.onCar = true
        val out = e.carCheck(Signals(odometerKm = 112.0, windowOpen = true, seenAt = mapOf("odometer" to 0L, "soc" to 0L)), 5)
        assertNull(out.warnings)
        assertNull(out.windowOpen)
        assertEquals(80, out.socPct)
        val d = VerdictEngine.diffChecks(c, out)
        assertTrue(d.toString(), "주행거리 +12.0km" in d)
        assertTrue(d.toString(), d.any { it.startsWith("확인 불가:") && "경고등" in it && "창문" in it && "트렁크" in it })
        // 못 읽은 창문을 '열림'으로 적지 않는다
        assertTrue(d.none { it.startsWith("창문") })
    }

    @Test fun lendHandoverSheet_inNotifyAndProof() {
        val e = engine()
        e.update(Signals(), 0)
        e.startLend(Signals(), 0)
        val fx = e.endLend(Signals(tirePressureLow = true, trunkOpen = true), 60_000)
        val l = e.state.lend
        assertNotNull(l.checkIn)
        assertNotNull(l.checkOut)
        val n = fx.filterIsInstance<Effect.Notify>().single()
        assertTrue(n.body, "타이어 저압 새로 표시" in n.body && "트렁크 닫힘 → 열림" in n.body)
        assertTrue(l.proof.any { it.what.startsWith("바뀐 것:") })
        assertEquals("빌려주기", e.state.history.single().kind)
        assertFalse(e.state.history.single().ok)
    }

    @Test fun incompleteThenOwnerClose_isRecordedNotOk() {
        val e = engine()
        valet(e)
        e.update(driving, 1_000)
        val fx = e.update(driving.copy(gear = Gear.P, speedKmh = 0f, driverSeated = false, driverDoorOpen = true), 2_000)
        assertEquals(ValetPhase.INCOMPLETE, e.state.valet.phase)
        assertTrue(fx.notices("집이 아닌 곳").single().body.contains("바뀐 것:"))
        assertTrue(e.state.history.isEmpty())
        e.ownerCloseValet()
        assertFalse(e.state.history.single().ok)
    }

    // ---------------------------------------------------------------- 3. 대리 귀가 내비
    @Test fun valetNavi_routesHome_eta_offRouteOnce_andEtaVsActual() {
        assertTrue(valet(engine()).none { it is Effect.NaviHome })
        val home = Pin(37.5, 127.0)
        val e = engine(mapOf(Place.HOME to home))
        assertEquals(home, valet(e).filterIsInstance<Effect.NaviHome>().single().home)
        val n = driving.copy(naviRemainKm = 12f, naviRemainMin = 18)
        val eta = e.update(n, 1_000).notices("도착 예정").single()
        assertTrue("현대 내비" in eta.title)
        assertNotNull(e.state.valet.naviEtaMs)
        assertTrue(e.update(n.copy(naviRemainMin = 15), 2_000).notices("도착 예정").isEmpty())
        e.update(n.copy(naviRemainKm = 10f), 3_000)
        // 가장 가까울 때(10km)보다 1.5km 넘게 늘어난 채 1분 머물러야 이탈
        e.update(n.copy(naviRemainKm = 11.6f), 4_000)
        assertTrue(e.needsTick())
        assertTrue(e.update(n.copy(naviRemainKm = 11.7f), 30_000).notices("벗어났").isEmpty())
        val off = e.update(n.copy(naviRemainKm = 11.8f), 64_000).notices("벗어났").single()
        assertEquals(Level.WARN, off.level)
        assertTrue(e.update(n.copy(naviRemainKm = 14f), 200_000).notices("벗어났").isEmpty())
        e.update(parkedHome, 201_000)
        e.update(parkedHome.copy(ignitionOn = false), 202_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
        assertTrue(e.state.valet.proof.any { it.what.startsWith("예정 ") && "실제 " in it.what })
    }

    @Test fun valetNavi_backOnRoute_resetsTimer() {
        val e = engine()
        valet(e)
        val n = driving.copy(naviRemainKm = 10f, naviRemainMin = 15)
        e.update(n, 1_000)
        e.update(n.copy(naviRemainKm = 12f), 2_000)
        e.update(n.copy(naviRemainKm = 10.5f), 30_000)
        assertNull(e.state.valet.naviOffSinceMs)
        assertTrue(e.update(n.copy(naviRemainKm = 12f), 70_000).notices("벗어났").isEmpty())
    }

    // ---------------------------------------------------------------- 4. 현대 기능 체크리스트
    @Test fun prepChecklist_isOwnerConfirmation_withProof_andCamWindow() {
        val e = engine()
        e.update(driving, 0)
        e.startValet()
        // 동의 대기 중에 체크한 것은 인수 뒤에도 남는다
        e.togglePrep(PrepItem.VALET_MODE, 0)
        e.confirmValetConsent(driving, 1_000)
        assertTrue(PrepItem.VALET_MODE in e.state.valet.prep)
        assertTrue(e.state.valet.proof.any { it.what == "발레 모드 켬 · 차주 확인" && "차주 확인" in it.src })
        e.togglePrep(PrepItem.VALET_MODE, 2_000)
        assertFalse(PrepItem.VALET_MODE in e.state.valet.prep)
        assertTrue(e.prepLabel(PrepItem.CAM_SAVE).endsWith("~지금"))

        val l = engine()
        assertTrue(l.togglePrep(PrepItem.KEY_REVOKE, 0, valet = false).any { it is Effect.Toast })
        l.startLend(Signals(), 0)
        l.endLend(Signals(), 3_600_000)
        // 돌려받은 뒤(리포트가 있을 때) 돌려받을 때 항목을 체크한다
        l.togglePrep(PrepItem.CAM_SAVE, 3_700_000, valet = false)
        assertTrue(PrepItem.CAM_SAVE in l.state.lend.prep)
        val label = l.prepLabel(PrepItem.CAM_SAVE, valet = false)
        assertTrue(label, VerdictEngine.hhmm(0) in label && VerdictEngine.hhmm(3_600_000) in label && "~" in label)
        assertEquals(PrepItem.KEY_REVOKE.label, l.prepLabel(PrepItem.KEY_REVOKE, valet = false))
    }

    // ---------------------------------------------------------------- 5. 정비 맡김 이탈
    @Test fun serviceRoam_byLocationAndDrivingHeldForThirtySeconds_warnsOnce() {
        val e = engine()
        val s0 = Signals(lat = 37.5, lon = 127.0)
        e.update(s0, 0)
        e.startLend(s0, 0, HandKind.SERVICE)
        assertTrue(e.update(s0.copy(odometerKm = s0.odometerKm + 4.9), 1_000).notices("정비소").isEmpty())
        val outside = s0.copy(odometerKm = s0.odometerKm + 5.2, lat = 37.52)
        for (t in listOf(2_000L, 12_000L, 22_000L)) assertTrue(e.update(outside, t).notices("정비소").isEmpty())
        val n = e.update(outside, 32_000).notices("정비소").single()
        assertEquals(Level.WARN, n.level)
        assertTrue(n.body, "주행 5.2km" in n.body)
        assertTrue(e.update(outside.copy(odometerKm = s0.odometerKm + 9.0), 33_000).notices("정비소").isEmpty())
        assertTrue(e.state.lend.proof.any { it.what.startsWith("정비소 벗어남") })
        e.endLend(s0, 34_000)
        assertEquals("맡김", e.state.history.single().kind)
        assertFalse(e.state.history.single().ok)
    }

    @Test fun serviceRoam_missingOdometerCannotConfirmDeparture() {
        val e = engine()
        e.onCar = true
        val seen = mapOf("gps" to 0L)
        val s0 = Signals(lat = 37.5, lon = 127.0, seenAt = seen)
        e.update(s0, 0)
        e.startLend(s0, 0, HandKind.SERVICE)
        // 0.01도 ≈ 1.1km
        assertTrue(e.update(s0.copy(lat = 37.51, odometerKm = s0.odometerKm + 50), 1_000).notices("정비소").isEmpty())
        // 주행거리·GPS를 모두 못 읽으면 판단하지 않는다
        val blind = engine()
        blind.onCar = true
        blind.update(Signals(), 0)
        blind.startLend(Signals(), 0, HandKind.SERVICE)
        assertTrue(blind.update(Signals(odometerKm = 99_999.0), 1_000).notices("정비소").isEmpty())
        // 빌려주기는 멀리 가는 것이 정상이라 알리지 않는다
        val lend = engine()
        lend.update(s0, 0)
        lend.startLend(Signals(lat = 37.5, lon = 127.0), 0)
        assertTrue(lend.update(Signals(lat = 38.0, lon = 127.0, odometerKm = 15_000.0), 1_000).notices("정비소").isEmpty())
    }

    // ---------------------------------------------------------------- 6. 이력서
    @Test fun resume_countsAndCsv_andKeepsFiveYears() {
        val h = listOf(
            HandoverRecord("대리", 0, 1_000, 12.4, 80f, 1, listOf(VerdictEngine.NO_CHANGE), true),
            HandoverRecord("맡김", 0, 1_000, null, 60f, 2, listOf("경고등 새로 켜짐: 엔진"), false),
            HandoverRecord("빌려주기", 0, 1_000, 3.0, 50f, 0, listOf("=cmd"), true),
        )
        val r = MyHyundai.resume(h)
        assertEquals(3, r.total)
        assertEquals(2, r.ok)
        assertEquals(15.4, r.km, 0.001)
        assertEquals(1, r.kmUnknown)
        assertEquals(2, r.brakes)
        assertEquals("총 3회 맡김 · 문제 없이 돌려받음 2회", r.lines[0])
        assertTrue(r.lines[1], "확인 불가 1회 제외" in r.lines[1])
        val csv = MyHyundai.historyCsv(h)
        assertTrue(csv.startsWith("﻿"))
        assertTrue("\"확인 불가\"" in csv)
        assertTrue("수식으로 실행되지 않게", "\"'=cmd\"" in csv)
        assertTrue(MyHyundai.RESUME_NOTE in csv)
        val now = System.currentTimeMillis()
        val kept = MyHyundai.keepHistory(listOf(h[0], h[0].copy(endMs = now)), LocalDate.now())
        assertEquals(listOf(now), kept.map { it.endMs })
    }

    @Test fun search_findsResume_withoutStealingOldWords() {
        assertEquals("HISTORY", FeatureIndex.search("이력서").first().route)
        assertEquals("HISTORY", FeatureIndex.search("중고차").first().route)
        assertEquals("HAND_SERVICE", FeatureIndex.search("맡김").first().route)
        assertEquals("HAND_SERVICE", FeatureIndex.search("정비").first().route)
        assertEquals(Module.DRIVER.name, FeatureIndex.search("보험").first().route)
    }

    // ---------------------------------------------------------------- 7. 정차 중 배터리
    @Test fun parkedBatteryUse_onlyWhileParked_noFlickerDoubleCount() {
        val e = engine()
        val p = Signals(socPct = 80)
        e.update(p, 0)
        e.startLend(p, 0)
        e.update(p.copy(socPct = 79), 1_000)
        e.update(p.copy(socPct = 80), 2_000)
        e.update(p.copy(socPct = 79), 3_000)
        e.update(p.copy(socPct = 77), 4_000)
        // 주행 중에 준 배터리는 넣지 않는다
        e.update(p.copy(gear = Gear.D, speedKmh = 30f, socPct = 70), 5_000)
        e.update(p.copy(socPct = 70), 6_000)
        e.endLend(p.copy(socPct = 70), 7_000)
        assertEquals("3% (V2L·유틸리티 모드·공조 등)", e.state.lend.report.toMap()["정차 중 배터리 사용"])
    }

    @Test fun parkedBatteryUse_onCarWithUnreadSoc_isNotReported() {
        val e = engine()
        e.onCar = true
        e.update(Signals(socPct = 80), 0)
        e.startLend(Signals(socPct = 80), 0)
        e.update(Signals(socPct = 60), 1_000)
        e.endLend(Signals(socPct = 60), 2_000)
        val r = e.state.lend.report.toMap()
        assertNull(r["정차 중 배터리 사용"])
        assertEquals("확인 불가", r["배터리·연료"])
    }
}

/** 새 칸 저장·복원과 예전 저장분 호환. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HandoverStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun newFields_roundTrip_andOldDataStillLoads() {
        val check = CarCheck(1, 100.0, null, setOf("엔진"), true, null, false, null, true, false)
        val v = ValetState(phase = ValetPhase.DRIVING, moved = true, checkIn = check, prep = setOf(PrepItem.VALET_MODE), naviEtaMs = 99, naviOffWarned = true, verificationPending = "위치 신호 대기")
        Store(app).saveValet(v)
        val lv = Store(app).loadValet()
        assertEquals(check, lv.checkIn)
        assertEquals(setOf(PrepItem.VALET_MODE), lv.prep)
        assertEquals(99L, lv.naviEtaMs)
        assertTrue(lv.naviOffWarned)
        assertEquals("위치 신호 대기", lv.verificationPending)

        val l = LendState(active = true, kind = HandKind.SERVICE, checkIn = check, prep = setOf(PrepItem.CAM_SAVE), startLat = 37.5, startLon = 127.0, roamWarned = true, parkedSocUsed = 4)
        Store(app).saveSession(TripState(), l)
        assertEquals(l, Store(app).loadSession().lend)

        val h = listOf(HandoverRecord("대리", 1, 2, null, 30f, 1, listOf("확인 불가: 창문"), true), HandoverRecord("맡김", 3, 4, 5.5, 60f, 0, emptyList(), false))
        Store(app).saveHistory(h)
        assertEquals(h.map { it.respectingValetConsent() }, Store(app).loadHistory())

        val prefs = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
        prefs.edit().putString("valet", """{"phase":"COMPLETE","confirmedBy":"시동 OFF"}""").commit()
        Store(app).loadValet().let { assertEquals(ValetPhase.COMPLETE, it.phase); assertNull(it.checkIn); assertTrue(it.prep.isEmpty()) }
        prefs.edit().putString("session", """{"lend":{"active":true,"brakes":0,"maxSpeed":0.0,"kind":"SERVICE"}}""").commit()
        Store(app).loadSession().lend.let { assertTrue(it.active); assertNull(it.startLat); assertEquals(0, it.parkedSocUsed) }

        Store(app).clearRecords()
        assertTrue(Store(app).loadHistory().isEmpty())
    }
}
