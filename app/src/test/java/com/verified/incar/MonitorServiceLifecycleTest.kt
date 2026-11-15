package com.verified.incar

import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class MonitorServiceLifecycleTest {
    @Test fun phoneBootAndExplicitStart_doNotStartBackgroundMonitor() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        MonitorService.start(app, ui = true)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun carBoot_startsMonitorButUnrelatedBroadcastDoesNot() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        assertNull(shadowOf(app).nextStartedService)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(MonitorService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    /** 업데이트 설치는 실행 중이던 감시 프로세스를 끝낸다. 차에서는 업데이트 직후 다시 시작하고 폰에서는 시작하지 않는다. */
    @Test fun packageReplaced_restartsMonitorOnlyOnCar() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, false)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertNull(shadowOf(app).nextStartedService)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(MonitorService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }
}
