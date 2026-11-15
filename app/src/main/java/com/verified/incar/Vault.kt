package com.verified.incar

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 차 안 저장 기록(집 좌표·운행기록·시트 자세·대리 상태) 암호화. AES-256-GCM.
 * 키는 Android Keystore에 두어 앱 밖으로 꺼낼 수 없다. Keystore가 없는 곳(PC 테스트)에서는 평문으로 둔다.
 * 암호문은 "enc1:"로 시작하고, 이전 평문 기록도 그대로 읽어 다음 저장 때 암호문으로 바뀐다.
 */
class Vault(private val keyOf: () -> SecretKey?) {
    // 한 번 실패했다고 null을 계속 쓰지 않도록 키를 얻은 경우만 기억한다
    @Volatile private var cached: SecretKey? = null
    private val key get() = cached ?: keyOf()?.also { cached = it }

    fun seal(plain: String): String {
        val k = key ?: return plain
        val c = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, k) }
        return PREFIX + Base64.getEncoder().encodeToString(c.iv + c.doFinal(plain.toByteArray()))
    }

    /** 키가 없거나 기록이 변조돼 풀리지 않으면 예외를 던진다(Store가 기본값으로 시작하고 원본을 보관). */
    fun open(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        val k = checkNotNull(key) { "저장 기록 키가 없어요" }
        val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        val c = Cipher.getInstance(CIPHER).apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw, 0, IV)) }
        return String(c.doFinal(raw, IV, raw.size - IV))
    }

    companion object {
        private const val PREFIX = "enc1:"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val IV = 12
        private const val ALIAS = "verified_store_v1"

        val device by lazy { Vault(::keystoreKey) }

        /**
         * Keystore가 아예 없는 곳(PC 테스트)에서만 null(평문). Keystore는 있는데 키를 못 읽으면 예외를 던져,
         * 암호문 기록을 평문 기본값으로 덮어쓰지 않게 한다.
         */
        private fun keystoreKey(): SecretKey? {
            val ks = loadKeystore(!Security.getProviders("KeyStore.AndroidKeyStore").isNullOrEmpty()) {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            } ?: return null
            return (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256).build(),
                )
                generateKey()
            }
        }

        /** provider 자체가 없는 테스트 환경만 평문을 허용한다. 실기기 load 실패는 그대로 전파한다. */
        internal fun loadKeystore(providerAvailable: Boolean, load: () -> KeyStore): KeyStore? =
            if (providerAvailable) load() else null
    }
}
