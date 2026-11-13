package com.verified.incar

import android.content.Context
import com.verified.incar.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValetPrivacyMigrationTest {
    @Test fun legacyHistoryAndActiveValetAreRewrittenAndExportsExcludeStatistics() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
        val old = JSONObject().put("kind", "대리").put("start", 1).put("end", 2).put("km", 3.0)
            .put("max", 141).put("brakes", 8).put("speedKnown", true).put("ok", true)
            .put("changes", JSONArray(listOf("인수 때와 달라진 것 없음", "정차 중 시동 12분")))
        prefs.edit().putString("history", JSONArray().put(old).toString())
            .putString("valet", JSONObject().put("phase", "DRIVING").put("idleMs", 720_000).put("idleSince", 1).toString()).commit()
        val store = Store(app)
        val h = store.loadHistory().single()
        assertEquals(0f, h.maxSpeed)
        assertEquals(0, h.brakes)
        assertFalse(h.speedKnown)
        assertEquals(listOf("인수 때와 달라진 것 없음"), h.changes)
        store.loadValet()
        assertFalse(prefs.getString("history", "")!!.contains("\"max\""))
        assertFalse(prefs.getString("valet", "")!!.contains("\"idleMs\""))
        assertEquals(h, store.loadHistory().single())
        val rawRecord = HandoverRecord("대리", 1, 2, 3.0, 141f, 8, listOf("정차 중 시동 12분"), true)
        val csv = MyHyundai.historyCsv(listOf(rawRecord))
        assertTrue(csv.contains("대리 기록 제외"))
        assertFalse(csv.contains("141") || csv.contains("12분"))
        assertEquals(0, MyHyundai.resume(listOf(rawRecord)).brakes)
    }
}
