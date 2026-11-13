package com.verified.incar

import com.verified.incar.core.Consent
import com.verified.incar.core.EscortPhase
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Persona
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.Wiper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GleoTest {
    private fun rt() = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false).also { r ->
        r.settings { it.copy(personas = Persona.entries.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet()) }
    }

    @Test fun schema_followsPleosRules() {
        val o = JSONObject(File("src/main/assets/schema.json").readText())
        assertEquals("com.verified.incar", o.getString("packageName"))
        val calls = o.getJSONArray("actionCalls")
        assertTrue("actionCalls 최대 3개", calls.length() <= 3)
        for (i in 0 until calls.length()) {
            val f = calls.getJSONObject(i).getJSONObject("function")
            assertTrue(Regex("[A-Z]+(_[A-Z]+)*").matches(f.getString("name")))
            assertTrue(f.getString("component") in setOf("activity", "service"))
        }
    }

    @Test fun voice_startsModulesAndReturnsCallback() {
        val r = rt()
        val v = Gleo.handle(r, Gleo.START_VALET, null)
        assertEquals("success", v.status)
        assertEquals(ValetPhase.CONSENT, r.ui.value.engine.valet.phase)
        assertEquals("START_VALET_MODE", JSONObject(v.json()).getString("functionName"))
        Gleo.handle(r, Gleo.START_ESCORT, null)
        assertEquals(EscortPhase.BOARDING, r.ui.value.engine.escort.phase)
    }

    @Test fun voice_refusesWhileDriving() {
        val r = rt()
        r.sim { it.copy(gear = Gear.D, speedKmh = 40f) }
        assertEquals("fail", Gleo.handle(r, Gleo.START_VALET, null).status)
    }

    @Test fun oddity_answersSettingMatchingSymptom() {
        val r = rt()
        r.sim { it.copy(wiper = Wiper.AUTO, recircOn = true, acOn = false) }
        r.skipSeconds(0)
        val msg = Gleo.handle(r, Gleo.CHECK_ODDITY, """{"symptom":"와이퍼가 혼자 움직여"}""").message
        assertTrue(msg.contains("wiper"))
        assertFalse(msg.contains("recirculation"))
        assertTrue(msg.contains("cannot read warning lights"))
    }

    @Test fun oddity_doesNotBlameUnrelatedSetting() {
        val r = rt()
        // 와이퍼는 AUTO가 아니고 뒷좌석 벨트만 풀려 있음
        r.sim { it.copy(row2Seated = true, row2Belt = false) }
        r.skipSeconds(0)
        val msg = Gleo.handle(r, Gleo.CHECK_ODDITY, """{"symptom":"와이퍼가 혼자 움직여"}""").message
        assertTrue(msg.contains("no related"))
        assertFalse(msg.contains("seat belt"))
    }
}
