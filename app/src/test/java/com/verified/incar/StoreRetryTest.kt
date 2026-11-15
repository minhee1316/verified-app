package com.verified.incar

import android.content.Context
import com.verified.incar.core.CarModel
import com.verified.incar.core.Settings
import com.verified.incar.core.TripLog
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreRetryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    @Test fun encryptionFailure_retriesOnlyLatestStateWithoutPlaintextWrite() {
        val ns = "_retry_key"
        val original = Settings(carModel = CarModel.AVANTE)
        Store(app, Vault { key }, ns).saveSettings(original)
        val preferences = app.getSharedPreferences("verified$ns", Context.MODE_PRIVATE)
        val encrypted = preferences.getString("settings", null)
        var unavailable = true
        val store = Store(app, Vault { if (unavailable) throw IOException("keystore temporarily unavailable") else key }, ns)
        store.saveSettings(original.copy(carModel = CarModel.TUCSON))
        store.saveSettings(original.copy(carModel = CarModel.GRANDEUR))
        assertTrue(store.hasPendingWrites)
        assertEquals(setOf("settings"), store.failedWriteKeys)
        assertEquals(encrypted, preferences.getString("settings", null))
        assertFalse(store.retryPendingWrites())
        unavailable = false
        assertTrue(store.retryPendingWrites())
        assertFalse(store.hasPendingWrites)
        assertTrue(store.failedWriteKeys.isEmpty())
        assertTrue(preferences.getString("settings", "")!!.startsWith("enc1:"))
        assertEquals(CarModel.GRANDEUR, Store(app, Vault { key }, ns).loadSettings().carModel)
    }

    @Test fun failedCommit_isRetriedEvenWhenCallerDoesNotChangeStateAgain() {
        val ns = "_retry_disk"
        var reject = true
        val store = Store(app, Vault { null }, ns, commitWrite = { editor -> if (reject) false else editor.commit() })
        store.saveSettings(Settings(carModel = CarModel.TUCSON))
        assertEquals(setOf("settings"), store.failedWriteKeys)
        assertTrue(store.hasPendingWrites)
        reject = false
        assertTrue(store.retryPendingWrites())
        assertEquals(CarModel.TUCSON, Store(app, Vault { null }, ns).loadSettings().carModel)
    }

    @Test fun unreadableCiphertext_doesNotQueueStartupDefaultsOverOriginal() {
        val ns = "_unreadable"
        val original = Settings(carModel = CarModel.AVANTE, ownerPinHash = "original_pin", ownerPinSalt = "salt")
        Store(app, Vault { key }, ns).saveSettings(original)
        val preferences = app.getSharedPreferences("verified$ns", Context.MODE_PRIVATE)
        val ciphertext = preferences.getString("settings", null)
        var unavailable = true
        val store = Store(app, Vault { if (unavailable) throw IOException("keystore unavailable") else key }, ns)
        val fallback = store.loadSettings()
        assertEquals(setOf("settings"), store.readFailedKeys)
        store.saveSettings(fallback)
        unavailable = false
        assertFalse(store.retryPendingWrites())
        assertEquals(ciphertext, preferences.getString("settings", null))
        assertEquals(original, store.loadSettings())
        assertTrue(store.readFailedKeys.isEmpty())
        assertTrue(store.retryPendingWrites())
        store.saveSettings(original.copy(carModel = CarModel.GRANDEUR))
        assertEquals(CarModel.GRANDEUR, Store(app, Vault { key }, ns).loadSettings().carModel)
    }

    @Test fun clearingRecords_dropsFailedPayloadInsteadOfResurrectingDeletedTrips() {
        val ns = "_cleared_queue"
        var reject = true
        val store = Store(app, Vault { null }, ns, commitWrite = { editor -> if (reject) false else editor.commit() })
        store.saveTrips(listOf(TripLog("2026-11-15", "차주", "집", "회사", 10.0, 15.0, "업무", true)), now = true)
        assertTrue(store.hasPendingWrites)
        store.clearRecords()
        reject = false
        assertTrue(store.retryPendingWrites())
        assertTrue(Store(app, Vault { null }, ns).loadTrips().isEmpty())
    }

    @Test fun invalidJson_keepsOriginalBackupAndRecoverableOwnerPin() {
        val ns = "_json_recovery"
        val preferences = app.getSharedPreferences("verified$ns", Context.MODE_PRIVATE)
        val damaged = """{"ownerPinHash":"keep_pin","ownerPinSalt":"salt","profiles":[{"invalid":"profile"}]}"""
        preferences.edit().putString("settings", damaged).commit()
        val store = Store(app, Vault { null }, ns)
        val recovered = store.loadSettings()
        assertEquals("keep_pin", recovered.ownerPinHash)
        assertEquals(damaged, preferences.getString("settings_broken", null))
        assertTrue(store.readFailedKeys.isEmpty())
        store.saveSettings(recovered)
        assertFalse(store.hasPendingWrites)
    }

    @Test fun keystoreLoadFailure_isNotTreatedLikeMissingTestProvider() {
        assertNull(Vault.loadKeystore(false) { throw AssertionError("provider is absent") })
        assertThrows(IOException::class.java) {
            Vault.loadKeystore(true) { throw IOException("real provider failed to load") }
        }
        val faulted = Vault {
            Vault.loadKeystore(true) { throw IOException("real provider failed to load") }
            null
        }
        assertThrows(IOException::class.java) { faulted.seal("private data") }
    }
}
