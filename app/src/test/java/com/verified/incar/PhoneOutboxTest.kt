package com.verified.incar

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Looper
import com.verified.incar.core.Effect
import com.verified.incar.core.Level
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneOutboxTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val sent = CopyOnWriteArrayList<String>()

    private fun runtime(status: PhoneDelivery = PhoneDelivery.ACCEPTED): VerifiedRuntime {
        shadowOf(app.getSystemService(ConnectivityManager::class.java)).setDefaultNetworkActive(true)
        return VerifiedRuntime(app, false) { _, n ->
            sent.add(checkNotNull(n.phoneId))
            PhoneRelay.Result(status, retryAfterMs = 60_000)
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val timeout = System.nanoTime() + 5_000_000_000
        while (!condition() && System.nanoTime() < timeout) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(condition())
    }

    @Test fun releaseHandsetRecordsManualHandoverWithoutSampleMeasurements() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val runtime = runtime()
        assertFalse(runtime.ui.value.simAllowed)
        runtime.act { _, _ -> startValet() }
        assertFalse(runtime.ui.value.engine.valet.active)
        runtime.act { s, now -> startLend(s, now) }
        val check = checkNotNull(runtime.ui.value.engine.lend.checkIn)
        assertNull(check.odometerKm)
        assertNull(check.socPct)
        assertNull(check.doorsLocked)
        assertFalse(runtime.ui.value.canControl)
        runtime.act { s, now -> endLend(s, now) }
        assertNull(runtime.ui.value.engine.history.last().km)
        runtime.close()
    }

    @Test fun onlyPairedOwnerEventsAreSent_andAcceptanceSurvivesRestart() {
        val first = runtime()
        first.act { _, _ -> listOf(Effect.Notify("차주 폰", "연결 전", "본문", Level.OK)) }
        first.enablePhoneNotifications()
        first.setSimOffline(true)
        first.act { _, _ -> listOf(Effect.Notify("보호자", "미지원 수신인", "본문", Level.OK),
            Effect.Notify("차주 폰", "예약 요청 (목업)", "본문", Level.INFO)) }
        first.sendPhoneTest()
        val saved = Store(app).loadNotices()
        val queued = saved.single { it.phoneStatus != null }
        assertEquals(PhoneDelivery.QUEUED, queued.phoneStatus)
        assertTrue(sent.isEmpty())
        first.close()

        val second = runtime()
        waitFor { second.ui.value.notices.any { it.phoneStatus == PhoneDelivery.ACCEPTED } }
        second.close()
        val third = runtime()
        assertEquals(listOf(queued.phoneId), sent.toList())
        assertEquals(PhoneDelivery.ACCEPTED, third.ui.value.notices.first { it.phoneId == queued.phoneId }.phoneStatus)
        third.close()
    }

    @Test fun rateLimitSurvivesRestart_andDisconnectClearsPendingWork() {
        val first = runtime(PhoneDelivery.QUEUED)
        first.enablePhoneNotifications()
        first.sendPhoneTest()
        waitFor { first.ui.value.notices.any { it.phoneAttempts == 1 } }
        val pending = Store(app).loadNotices().first { it.phoneStatus == PhoneDelivery.QUEUED }
        assertTrue(pending.phoneRetryAt > System.currentTimeMillis())
        first.close()
        val second = runtime()
        assertEquals(pending.phoneRetryAt, second.ui.value.notices.first { it.phoneId == pending.phoneId }.phoneRetryAt)
        val oldTopic = second.ui.value.phoneTopic
        second.disablePhoneNotifications()
        second.enablePhoneNotifications()
        assertNotEquals(oldTopic, second.ui.value.phoneTopic)
        assertTrue(Store(app).loadNotices().none { it.phoneStatus == PhoneDelivery.QUEUED })
        assertEquals(1, sent.size)
        second.wipeMyData()
        assertNull(Store(app).loadPhoneTopic())
        second.close()
    }
}
