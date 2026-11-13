package com.verified.incar.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 차주 잠금 상태. 차에는 대리기사·빌린 사람 같은 다른 사람이 앉기 때문에,
 * 설정 변경·대리 종료 승인·기록 지우기·키 공유·시뮬레이터는 차주 PIN을 넣어야 한다.
 * [rounds]는 연속 잠금 횟수로, 잠길 때마다 기다리는 시간이 두 배가 된다.
 */
data class OwnerLock(val tries: Int = 0, val lockedUntilMs: Long = 0, val unlockedUntilMs: Long = 0, val rounds: Int = 0)

object OwnerPin {
    const val MAX_TRIES = 5
    /** 첫 잠금 시간과 한 번 풀면 열려 있는 시간 [제안값]. */
    const val LOCKOUT_MS = 30_000L
    const val UNLOCK_MS = 5 * 60_000L
    private const val ITERATIONS = 20_000

    fun valid(pin: String) = pin.length in 4..6 && pin.all { it in '0'..'9' }

    const val RECOVERY_LEN = 12

    fun validRecovery(code: String) = code.length == RECOVERY_LEN && code.all { it in '0'..'9' }

    /** PIN을 잊었을 때 쓰는 12자리 숫자. PIN을 정할 때 한 번만 보여 주고 해시만 저장한다. */
    fun newRecoveryCode(): String = SecureRandom().let { r -> (1..RECOVERY_LEN).joinToString("") { r.nextInt(10).toString() } }

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

    /** PIN은 저장하지 않고 소금을 친 PBKDF2 해시만 저장한다. */
    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }

    /** PIN을 한 번 넣어 본다. 돌려주는 문장이 null이면 성공. */
    fun attempt(pin: String, savedHash: String, salt: String, lock: OwnerLock, nowMs: Long): Pair<OwnerLock, String?> {
        if (nowMs < lock.lockedUntilMs) return lock to "잠시 뒤 다시 시도해 주세요 (${(lock.lockedUntilMs - nowMs + 999) / 1000}초)"
        // 시간 차이로 PIN을 추측하지 못하게 고정 시간 비교를 쓴다
        if (MessageDigest.isEqual(hash(pin, salt).toByteArray(), savedHash.toByteArray())) return OwnerLock(unlockedUntilMs = nowMs + UNLOCK_MS) to null
        val tries = lock.tries + 1
        if (tries < MAX_TRIES) return lock.copy(tries = tries) to "PIN이 맞지 않아요 (${MAX_TRIES - tries}번 남음)"
        val wait = LOCKOUT_MS shl lock.rounds.coerceAtMost(6)
        return OwnerLock(lockedUntilMs = nowMs + wait, rounds = lock.rounds + 1) to "PIN이 ${MAX_TRIES}번 틀려 ${wait / 1000}초 동안 잠겼어요"
    }
}
