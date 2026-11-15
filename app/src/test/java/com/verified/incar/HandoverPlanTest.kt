package com.verified.incar

import com.verified.incar.core.Consent
import com.verified.incar.core.Effect
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.HandKind
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import com.verified.incar.core.Proof
import com.verified.incar.core.Settings
import com.verified.incar.core.Signals
import com.verified.incar.core.VerdictEngine
import com.verified.incar.ui.filterHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 반납 예정·맡김 메모, 이력서 한 건의 근거·메모 저장, 기록 검색·필터, 맡김 기록 PDF 문구. */
@RunWith(RobolectricTestRunner::class)
class HandoverPlanTest {
    private val parked = Signals(gear = Gear.P, speedKmh = 0f, place = Place.HOME)
    private fun engine() = VerdictEngine(Settings(personas = setOf(Persona.NIGHT_OUT), flags = Flag.entries.toSet(), consents = Consent.entries.toSet()))
    private fun List<Effect>.notices(word: String) = filterIsInstance<Effect.Notify>().filter { word in it.title }

    @Test fun dueAlertOnce_andRecordKeepsMemoDueAndProof() {
        val e = engine()
        e.update(parked, 0)
        e.startLend(parked, 1_000, HandKind.SERVICE, memo = "  타이어 교체  ", dueMs = 3_600_000)
        assertEquals("타이어 교체", e.state.lend.memo)
        assertTrue(e.update(parked, 3_000_000).notices("반납 예정").isEmpty())
        assertTrue(e.lendOverdue(3_700_000))
        assertEquals(1, e.update(parked, 3_700_000).notices("반납 예정 시각이 지났어요").size)
        assertFalse(e.lendOverdue(3_800_000))
        assertTrue(e.update(parked, 3_800_000).notices("반납 예정").isEmpty())
        e.endLend(parked, 4_000_000)
        val r = e.state.history.single()
        assertEquals("타이어 교체", r.memo)
        assertEquals(3_600_000L, r.dueMs)
        assertTrue(r.proof.any { "지남" in it.what })
        assertTrue(r.proof.any { it.what.startsWith("넘김") } && r.proof.any { it.what.startsWith("돌려받음") })
    }

    @Test fun duePlanChange_rearmsAlert_andPastDueIsIgnored() {
        val e = engine()
        e.update(parked, 0)
        e.startLend(parked, 10_000, HandKind.LEND, dueMs = 5_000)
        assertEquals(null, e.state.lend.dueMs)
        e.setLendPlan("동생 여행", 60_000)
        e.update(parked, 70_000)
        assertTrue(e.state.lend.dueWarned)
        e.setLendPlan("동생 여행", 120_000)
        assertFalse(e.state.lend.dueWarned)
        assertEquals(1, e.update(parked, 130_000).notices("반납 예정 시각이 지났어요").size)
    }

    @Test fun recordMemo_isEditableAndSavedWithProof() {
        val e = engine()
        e.update(parked, 0)
        e.startLend(parked, 1_000, HandKind.LEND)
        e.endLend(parked, 2_000)
        val start = e.state.history.single().startMs
        e.setRecordMemo(start, "기존 오른쪽 문 흠집")
        assertEquals("기존 오른쪽 문 흠집", e.state.history.single().memo)
        val store = Store(RuntimeEnvironment.getApplication())
        store.saveHistory(e.state.history, now = true)
        val loaded = store.loadHistory().single()
        assertEquals("기존 오른쪽 문 흠집", loaded.memo)
        assertEquals(e.state.history.single().proof, loaded.proof)
    }

    @Test fun filterHistory_byKindCheckPeriodAndText() {
        val day = 86_400_000L
        val now = 40 * day
        val h = listOf(
            HandoverRecord("대리", 1 * day, 1 * day + 1, 4.8, 0f, 0, emptyList(), ok = true),
            HandoverRecord("맡김", 35 * day, 35 * day + 1, 3.2, 40f, 1, listOf("타이어 저압 새로 표시"), ok = false, memo = "엔진오일 교체"),
            HandoverRecord("빌려주기", 39 * day, 39 * day + 1, 32.0, 80f, 2, emptyList(), ok = true, proof = listOf(Proof(39 * day, "급감속 · 80km/h", "시뮬레이터"))),
        )
        assertEquals(3, filterHistory(h, null, false, null, "", now).size)
        assertEquals(listOf("맡김"), filterHistory(h, "맡김", false, null, "", now).map { it.kind })
        assertEquals(listOf("맡김"), filterHistory(h, null, true, null, "", now).map { it.kind })
        assertEquals(listOf("맡김", "빌려주기"), filterHistory(h, null, false, 7, "", now).map { it.kind })
        assertEquals(listOf("맡김"), filterHistory(h, null, false, null, "오일", now).map { it.kind })
        assertEquals(listOf("빌려주기"), filterHistory(h, null, false, null, "급감속", now).map { it.kind })
    }

    @Test fun recordPdfLines_separateCarValuesMemoAndDemo() {
        val r = HandoverRecord("맡김", 0, 2 * 3_600_000L, 3.2, 40f, 1, listOf("주행거리 +3.2km"), ok = true,
            memo = "타이어 교체", dueMs = 3_600_000L)
        val lines = RecordPdf.lines(r, demo = true)
        assertTrue(lines.first().startsWith("※ 예시 차량"))
        assertTrue(lines.any { it == "종류: 정비 맡김" })
        assertTrue(lines.any { "예정보다 60분 늦게" in it })
        assertTrue(lines.indexOf("# 차주 메모") < lines.indexOf("타이어 교체"))
        assertFalse(RecordPdf.lines(r, demo = false).any { it.startsWith("※") })
        assertTrue(RecordPdf.fileName(r, demo = true).startsWith("시연_맡김기록_정비맡김_"))
    }
}
