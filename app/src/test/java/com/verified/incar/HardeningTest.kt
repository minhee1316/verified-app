package com.verified.incar

import android.content.Context
import com.verified.incar.core.Settings
import com.verified.incar.core.ValetPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 앱 안에서 막아야 하는 약점: 차주 전용 조작, 진행 중 판정 덮어쓰기, 깨진 저장 기록. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardeningTest {
    private fun rt() = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)

    @Test fun ownerOnly_waitsForPin_andRelocksWhenValetStarts() {
        val r = rt()
        var ran = 0
        r.ownerOnly { ran++ }
        assertEquals("PIN이 없으면 바로 실행", 1, ran)
        r.setOwnerPin("2468")
        r.lockOwner()
        r.ownerOnly { ran++ }
        assertEquals(1, ran)
        assertNotNull(r.ownerRequest.value)
        assertNotNull(r.unlockOwner("1111"))
        assertEquals(1, ran)
        assertNull(r.unlockOwner("2468"))
        assertEquals("맞는 PIN이면 기다리던 조작을 실행", 2, ran)
        r.ownerOnly { ran++ }
        assertEquals(3, ran)
        r.act { _, _ -> startValet(); emptyList() }
        r.ownerOnly { ran++ }
        assertEquals("대리 시작과 함께 다시 잠김", 3, ran)
        assertTrue(r.ui.value.ownerPinSet)
    }

    @Test fun voice_doesNotResetValetInProgress() {
        val r = rt()
        r.act { _, _ -> startValet(); emptyList() }
        r.act { s, now -> confirmValetConsent(s, now) }
        // 기본 신호가 P단 정차라 동의 직후 바로 '1차 완료'가 된다
        val before = r.ui.value.engine.valet.phase
        assertTrue(before == ValetPhase.DRIVING || before == ValetPhase.PRELIM)
        val reply = Gleo.handle(r, Gleo.START_VALET, null)
        assertEquals("success", reply.status)
        assertEquals(before, r.ui.value.engine.valet.phase)
    }

    @Test fun brokenSavedData_startsWithDefaults_andKeepsCopy() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
        prefs.edit().putString("settings", "{broken").putString("trips", "[1,").commit()
        val store = Store(app)
        assertEquals(Settings(), store.loadSettings())
        assertTrue(store.loadTrips().isEmpty())
        assertEquals("{broken", prefs.getString("settings_broken", null))
    }

    @Test fun vault_encryptsWithKey_andStillReadsOldPlainRecords() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val v = Vault { key }
        val json = """{"pins":{"HOME":{"lat":37.5612,"lon":126.9801}}}"""
        val sealed = v.seal(json)
        assertTrue(sealed.startsWith("enc1:"))
        assertTrue("37.5612" !in sealed)
        assertEquals(json, v.open(sealed))
        assertEquals("이전 평문 기록", v.open("이전 평문 기록"))
        // 다른 키(다른 차·초기화된 Keystore)로는 열리지 않는다
        val other = Vault { javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertTrue(runCatching { other.open(sealed) }.isFailure)
    }

    @Test fun recoveryCode_turnsOffPin_andRunsWaitingAction() {
        val r = rt()
        var ran = 0
        val code = r.setOwnerPin("2468")!!
        assertTrue(com.verified.incar.core.OwnerPin.validRecovery(code))
        r.lockOwner()
        r.ownerOnly { ran++ }
        assertNotNull(r.recoverOwner("000000000000"))
        assertEquals(0, ran)
        assertNull(r.recoverOwner(code))
        assertEquals(1, ran)
        assertTrue(!r.ui.value.ownerPinSet)
    }

    @Test fun pinLockout_survivesAppRestart() {
        val r = rt()
        r.setOwnerPin("2468")
        r.lockOwner()
        repeat(com.verified.incar.core.OwnerPin.MAX_TRIES) { r.unlockOwner("0000") }
        // 앱을 강제로 껐다 켜도(새 런타임) 잠금이 이어져 맞는 PIN도 바로 받지 않는다
        val again = rt()
        assertTrue(again.ownerLocked())
        assertNotNull(again.unlockOwner("2468"))
    }

    @Test fun pendingTripAndLend_surviveAppRestart() {
        val app = RuntimeEnvironment.getApplication()
        val store = Store(app)
        val pending = com.verified.incar.core.TripLog("2026-11-06", "차주", "집", "회사", 1.0, 9.0, "", true)
        val lend = com.verified.incar.core.LendState(active = true, kind = com.verified.incar.core.HandKind.SERVICE, starts = 2)
        store.saveSession(com.verified.incar.core.TripState(pending = pending), lend)
        val (p, open, l) = Store(app).loadSession()
        assertEquals(pending, p)
        assertNull(open)
        assertEquals(lend, l)
    }

    @Test fun wipe_removesBrokenCopies() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
        prefs.edit().putString("trips", "[1,").commit()
        Store(app).loadTrips()
        // 운행기록은 따로 둔 파일로 옮겨 읽으므로 깨진 사본도 그 파일에 남는다
        val tripPrefs = app.getSharedPreferences("verified_trips", Context.MODE_PRIVATE)
        assertTrue(tripPrefs.contains("trips_broken"))
        Store(app).clearRecords()
        assertTrue(!tripPrefs.contains("trips_broken") && !prefs.contains("trips_broken"))
    }

    @Test fun offline_notificationsAreKeptThenSent_andSurviveRestart() {
        val r = rt()
        r.setSimOffline(true)
        r.act { _, _ -> listOf(com.verified.incar.core.Effect.Notify("차주 폰", "보관 테스트", "본문", com.verified.incar.core.Level.INFO)) }
        assertTrue(!r.ui.value.notices.first().delivered)
        assertTrue(!r.ui.value.online)
        // 앱이 다시 켜져도 보관한 알림이 남는다
        assertTrue(rt().ui.value.notices.any { it.title == "보관 테스트" && !it.delivered })
        r.setSimOffline(false)
        assertTrue(r.ui.value.notices.first().delivered)
        assertTrue("1건" in r.ui.value.toast)
    }

    @Test fun pinIsSavedAsHashOnly() {
        val r = rt()
        r.setOwnerPin("2468")
        val raw = RuntimeEnvironment.getApplication().getSharedPreferences("verified", Context.MODE_PRIVATE).getString("settings", "")!!
        assertTrue(raw.contains("ownerPinHash"))
        assertTrue(!raw.contains("2468"))
    }
}
