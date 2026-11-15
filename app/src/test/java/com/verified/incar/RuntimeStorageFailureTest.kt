package com.verified.incar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Unreadable owner settings must not become an unlocked, writable default profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeStorageFailureTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val preferences get() = app.getSharedPreferences("verified", Context.MODE_PRIVATE)
    private val ciphertext = "enc1:invalid"

    @Before fun seedUnreadableRealVehicleSettings() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        assertTrue(TrialMode.set(app, false))
        assertTrue(preferences.edit().putString("settings", ciphertext).commit())
    }

    private fun runtime() = VerifiedRuntime(app, false, PhoneRelay::publish) { _, _ -> ConnectedWithoutReadings() }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun assertOriginalSettingsPreserved() = assertEquals(ciphertext, preferences.getString("settings", null))

    /** 읽지 못한 알림 암호문은 새 알림 저장으로 덮지 않고, 저장하지 못한 새 알림도 화면·전송 대기열에서 버리지 않는다. */
    @Test fun unreadableNotices_keepNewAlertWithoutOverwritingCiphertext() {
        assertTrue(preferences.edit().putString("notices", ciphertext).commit())
        val vm = runtime()
        try {
            idle()
            vm.act { _, _ -> listOf(com.verified.incar.core.Effect.Notify("차주 폰", "시험 알림", "본문", com.verified.incar.core.Level.WARN)) }
            idle()
            assertTrue("저장하지 못해도 새 알림은 남는다", vm.ui.value.notices.any { it.title == "시험 알림" })
            assertEquals(ciphertext, preferences.getString("notices", null))
        } finally { vm.close() }
        assertEquals(ciphertext, preferences.getString("notices", null))
    }

    @Test fun unreadableOwnerSettings_keepOwnerActionsLockedAndPreserveCiphertext() {
        val vm = runtime()
        try {
            idle()
            assertTrue("The test must exercise the real vehicle store", vm.ui.value.carConnected)
            assertFalse(vm.trial)
            assertFalse(vm.simAllowed)
            assertTrue(vm.ownerLocked())
            assertFalse(vm.ui.value.ownerUnlocked)
            assertOriginalSettingsPreserved()

            var executed = false
            vm.ownerOnly { executed = true }
            idle()
            assertFalse("An unreadable PIN must not authorize the owner action", executed)
            assertNull("Do not queue a PIN action against unreadable settings", vm.ownerRequest.value)
            assertTrue(vm.ownerLocked())
            assertFalse(vm.ui.value.ownerUnlocked)
            assertOriginalSettingsPreserved()
        } finally { vm.close() }
        assertOriginalSettingsPreserved()
    }

    @Test fun unreadableOwnerSettings_rejectPinReplacementAndRemovalAcrossRestart() {
        val vm = runtime()
        try {
            assertTrue(vm.ownerLocked())
            assertNull("Do not issue a recovery code for a PIN that was not saved", vm.setOwnerPin("2468"))
            idle()
            assertTrue(vm.ownerLocked())
            assertOriginalSettingsPreserved()

            assertNull(vm.setOwnerPin(null))
            idle()
            assertTrue("Removing the unreadable PIN must not unlock the owner", vm.ownerLocked())
            assertFalse(vm.ui.value.ownerUnlocked)
            assertOriginalSettingsPreserved()
        } finally { vm.close() }
        assertOriginalSettingsPreserved()

        val restarted = runtime()
        try {
            idle()
            assertTrue(restarted.ownerLocked())
            assertFalse(restarted.ui.value.ownerUnlocked)
            assertOriginalSettingsPreserved()
        } finally { restarted.close() }
        assertOriginalSettingsPreserved()
    }
}
