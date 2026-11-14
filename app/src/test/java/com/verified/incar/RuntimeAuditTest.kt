package com.verified.incar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Looper
import com.verified.incar.core.DriverVerdict
import com.verified.incar.core.Effect
import com.verified.incar.core.EngineState
import com.verified.incar.core.Gear
import com.verified.incar.core.LendState
import com.verified.incar.core.Level
import com.verified.incar.core.Module
import com.verified.incar.core.OwnerPin
import com.verified.incar.core.Pin
import com.verified.incar.core.Place
import com.verified.incar.core.Postures
import com.verified.incar.core.Settings
import com.verified.incar.core.TripState
import com.verified.incar.core.TrunkState
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.ValetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** 런타임 점검(저장·잠금·망 감지·시연 시나리오)에서 찾은 문제를 다시 생기지 않게 막는 테스트. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeAuditTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
    private fun rt() = VerifiedRuntime(app, simTicker = false)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun notice(title: String) = listOf(Effect.Notify("차주 폰", title, "본문", Level.INFO))

    @Test fun recoveredValet_doesNotCompleteOrNotifyAcrossRestarts() {
        // 예시 GPS가 자동 주입되는 debug 체험 대신, 실제 신호를 기다리는 제출용 환경을 사용한다.
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        Store(app).saveValet(ValetState(phase = ValetPhase.PRELIM, prelimAtMs = 1, moved = true))
        val first = rt()
        first.close()
        val r = rt()
        assertTrue(r.ui.value.notices.none { it.title == "① 대리 주차 완료" })
        assertEquals(ValetPhase.PRELIM, Store(app).loadValet().phase)
        assertNotNull(Store(app).loadValet().verificationPending)
        assertTrue(Store(app).loadHistory().isEmpty())
        r.close()
    }

    @Test fun wipe_doesNotResaveOldNotices() {
        val r = rt()
        r.act { _, _ -> notice("지울 알림") }
        r.settings { it.copy(extraModules = it.extraModules + Module.DRIVER) }
        // 지운 뒤 바로 '처음 보는 자세' 알림이 나오는 상황
        r.sim { it.copy(posture = Postures.STRANGER) }
        r.wipeMyData()
        assertTrue(rt().ui.value.notices.none { it.title == "지울 알림" })
    }

    @Test fun startsOfflineWithoutNetwork_thenSendsWhenBack() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        // 지하 주차장: 망 없이 앱이 켜진다
        shadowOf(cm).setDefaultNetworkActive(false)
        val r = rt()
        r.act { _, _ -> notice("지하") }
        assertTrue(!r.ui.value.online)
        assertTrue(!r.ui.value.notices.first().delivered)
        shadowOf(cm).setDefaultNetworkActive(true)
        shadowOf(cm).networkCallbacks.toList().forEach { it.onAvailable(cm.activeNetwork!!) }
        idle()
        assertTrue(r.ui.value.notices.first().delivered)
        assertTrue("1건" in r.ui.value.toast)
    }

    @Test fun keptNotices_areSentOnFirstNetwork_afterRestart() {
        val r = rt()
        r.setSimOffline(true)
        r.act { _, _ -> notice("보관 테스트") }
        // 연결된 채로 다시 켜짐: 실기기는 등록 직후 onAvailable을 부르지만 Robolectric은 부르지 않아 직접 부른다
        val again = rt()
        val cm = app.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).networkCallbacks.toList().forEach { it.onAvailable(cm.activeNetwork!!) }
        idle()
        assertTrue(again.ui.value.notices.first { it.title == "보관 테스트" }.delivered)
    }

    @Test fun recoveryCodeLockout_usesBootClock_andSurvivesRestart() {
        val r = rt()
        r.setOwnerPin("2468")
        r.lockOwner()
        repeat(OwnerPin.MAX_TRIES) { r.recoverOwner("000000000000") }
        val err = r.unlockOwner("2468")!!
        assertTrue("잠금 시간이 차 시계 기준으로 커지지 않음: $err", !Regex("\\d{4,}초").containsMatchIn(err))
        assertNotNull("강제로 다시 켜도 잠금이 이어짐", rt().unlockOwner("2468"))
    }

    @Test fun brokenSettingsField_keepsOwnerPin() {
        rt().setOwnerPin("2468")
        // 칸 하나만 어긋난 설정(schedule에 enabled 없음): 나머지는 기본값이어도 PIN은 남아야 한다
        val raw = org.json.JSONObject(prefs.getString("settings", null)!!).put("schedule", org.json.JSONObject())
        prefs.edit().putString("settings", raw.toString()).commit()
        val again = rt()
        assertTrue("기본값 경로를 탔는지", prefs.contains("settings_broken"))
        assertTrue(again.ownerLocked())
        assertNull("소금까지 읽어 맞는 PIN이 통한다", again.unlockOwner("2468"))
        assertTrue(again.ui.value.ownerPinSet)
    }

    @Test fun store_encryptsWithKey_andKeepsRecordWhenKeyFails() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        Store(app, Vault { key }).saveSettings(Settings(ownerPinHash = "h", ownerPinSalt = "s", pins = mapOf(Place.HOME to Pin(37.5, 127.0))))
        val sealed = prefs.getString("settings", null)!!
        assertTrue(sealed.startsWith("enc1:"))
        // 키를 못 읽는 날: 기본값으로 시작하되 암호문을 덮어쓰지 않는다
        val broken = Store(app, Vault { error("keystore") })
        assertEquals(Settings(), broken.loadSettings())
        broken.saveSettings(Settings())
        assertEquals(sealed, prefs.getString("settings", null))
        assertEquals(sealed, prefs.getString("settings_broken", null))
        val back = Store(app, Vault { key }).loadSettings()
        assertEquals(Pin(37.5, 127.0), back.pins[Place.HOME])
        assertEquals("h", back.ownerPinHash)
        // 두 번째로 깨져도 처음 원본을 지킨다
        prefs.edit().putString("settings", "{broken").commit()
        broken.loadSettings()
        assertEquals(sealed, prefs.getString("settings_broken", null))
    }

    @Test fun trips_moveToOwnFile_withoutLoss() {
        val trip = com.verified.incar.core.TripLog("2026-11-06", "차주", "집", "회사", 1.0, 9.0, "", true)
        Store(app).saveTrips(listOf(trip), now = true)
        val tripPrefs = app.getSharedPreferences("verified_trips", Context.MODE_PRIVATE)
        assertTrue(tripPrefs.contains("trips") && !prefs.contains("trips"))
        // 예전 버전처럼 'verified'에만 있는 운행기록도 옮겨 읽는다
        tripPrefs.edit().clear().commit()
        prefs.edit().putString("trips", """[{"date":"2026-11-06","driver":"차주","from":"집","to":"회사","startOdo":1.0,"endOdo":9.0,"purpose":"","business":true}]""").commit()
        assertEquals(listOf(trip), Store(app).loadTrips())
        assertTrue(!prefs.contains("trips") && tripPrefs.contains("trips"))
        Store(app).clearRecords()
        assertTrue(Store(app).loadTrips().isEmpty())
    }

    @Test fun trunkAndStreak_surviveAppRestart() {
        val store = Store(app)
        store.saveSession(TripState(), LendState(), TrunkState(loaded = true, loadedAt = "마트", reminded = true), streak = 3)
        val s = Store(app).loadSession()
        assertEquals(TrunkState(loaded = true, loadedAt = "마트", reminded = true), s.trunk)
        assertEquals(3, s.streak)
        // 런타임이 실제로 넘겨 저장하는지(기본 신호가 P단이라 바로 저장된다)
        prefs.edit().remove("session").commit()
        rt().act { _, _ -> confirmTrunk(true); emptyList() }
        assertTrue(Store(app).loadSession().trunk.loaded)
        // 예전 형식(트렁크·연속 기록 없음)도 세션을 잃지 않고 읽는다
        prefs.edit().putString("session", """{"lend":{"active":true,"brakes":0,"maxSpeed":0.0,"kind":"LEND"}}""").commit()
        assertTrue(Store(app).loadSession().lend.active)
    }

    @Test fun control_toastShowsConfirmed_notStuckOnChecking() {
        val r = rt()
        r.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        r.act { s, now -> control(com.verified.incar.core.ControlKind.LOCK, s, now) }
        idle()
        assertTrue(r.ui.value.signals.doorsLocked)
        assertTrue("안내줄이 '확인 중'에 머물지 않음: ${r.ui.value.toast}", "확인됨 · 차 신호로" in r.ui.value.toast)
    }

    @Test fun simulator_isBlockedOnCarOs_inReleaseBuild() {
        // 차 서비스 연결에 실패한 자동차 OS의 제출용 빌드(JVM에는 car_service가 없어 연결 실패 분기를 탄다)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val r = rt()
        assertTrue(!r.simAllowed && !r.ui.value.simAllowed)
        r.settings { it.copy(pins = mapOf(Place.HOME to Pin(37.0, 127.0))) }
        r.act { _, _ -> startValet(); emptyList() }
        r.act { s, now -> confirmValetConsent(s, now) }
        // 실차가 보낸 '운행 중' 신호를 시뮬레이터가 '집 도착·P단'이나 시간 경과로 덮어쓰지 못해야 한다
        r.signals.value = r.signals.value.copy(gear = Gear.D, speedKmh = 30f, place = Place.OTHER)
        val t = r.ui.value.nowMs
        r.sim { it.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOME) }
        r.runScenario("valet_quick_off")
        r.skipSeconds(60)
        assertEquals(Gear.D, r.signals.value.gear)
        assertEquals(ValetPhase.DRIVING, r.ui.value.engine.valet.phase)
        assertTrue(r.ui.value.nowMs - t < 60_000)
    }

    // ---- PT 시연 시나리오: 엔진 판정을 고쳐도 시연이 조용히 깨지지 않게. 시나리오마다 새 테스트(저장소가 이어지지 않게)
    private fun play(name: String): EngineState {
        val r = rt()
        val job = r.runScenario(name)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertTrue("$name 시나리오가 끝까지 돎", job.isCompleted && !job.isCancelled)
        return r.ui.value.engine
    }

    @Test fun scenario_valet() {
        val v = play("valet").valet
        assertEquals(ValetPhase.COMPLETE, v.phase)
        assertEquals("10초 정지 + 운전석 하차", v.confirmedBy)
    }

    @Test fun scenario_valetQuickOff() {
        val v = play("valet_quick_off").valet
        assertEquals(ValetPhase.COMPLETE, v.phase)
        assertEquals("시동 OFF", v.confirmedBy)
    }

    @Test fun scenario_valetOutside() = assertEquals(ValetPhase.INCOMPLETE, play("valet_outside").valet.phase)

    @Test fun scenario_stranger() = assertEquals(DriverVerdict.UNKNOWN, play("stranger").driver.verdict)

    @Test fun scenario_lend() {
        val report = play("lend").lend.report.toMap()
        assertEquals("18.4km", report["이동 거리"])
        assertTrue(report["이용 시간"]!!.endsWith("35분"))
        assertEquals("80% → 76%", report["배터리·연료"])
    }

    @Test fun scenario_service() {
        val report = play("service").lend.report.toMap()
        assertEquals("12.6km", report["이동 거리"])
        assertTrue(report["이용 시간"]!!.endsWith("25분"))
        assertEquals("80% → 77%", report["배터리·연료"])
    }
}
