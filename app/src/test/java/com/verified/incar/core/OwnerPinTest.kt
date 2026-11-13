package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerPinTest {
    @Test fun pinFormat_isFourToSixDigits() {
        assertTrue(OwnerPin.valid("2468"))
        assertTrue(OwnerPin.valid("246810"))
        assertFalse(OwnerPin.valid("246"))
        assertFalse(OwnerPin.valid("1234567"))
        assertFalse(OwnerPin.valid("12a4"))
    }

    @Test fun hash_isSalted_andDoesNotContainPin() {
        val a = OwnerPin.newSalt()
        val b = OwnerPin.newSalt()
        assertNotEquals(OwnerPin.hash("2468", a), OwnerPin.hash("2468", b))
        assertEquals(OwnerPin.hash("2468", a), OwnerPin.hash("2468", a))
        assertFalse("2468" in OwnerPin.hash("2468", a))
    }

    @Test fun attempt_unlocks_thenLocksOutAfterFiveWrong_andWaitDoubles() {
        val salt = OwnerPin.newSalt()
        val saved = OwnerPin.hash("2468", salt)
        val (open, ok) = OwnerPin.attempt("2468", saved, salt, OwnerLock(), 0)
        assertNull(ok)
        assertEquals(OwnerPin.UNLOCK_MS, open.unlockedUntilMs)

        var lock = OwnerLock()
        repeat(4) { lock = OwnerPin.attempt("0000", saved, salt, lock, 1_000).first }
        assertEquals(0L, lock.lockedUntilMs)
        lock = OwnerPin.attempt("0000", saved, salt, lock, 1_000).first
        assertEquals(1_000 + OwnerPin.LOCKOUT_MS, lock.lockedUntilMs)
        // 잠긴 동안에는 맞는 PIN도 받지 않는다
        assertNotNull(OwnerPin.attempt("2468", saved, salt, lock, 2_000).second)

        val t = lock.lockedUntilMs
        repeat(5) { lock = OwnerPin.attempt("0000", saved, salt, lock, t).first }
        assertEquals(t + OwnerPin.LOCKOUT_MS * 2, lock.lockedUntilMs)
    }

    @Test fun csv_doesNotStartWithFormula() {
        assertEquals("'=HYPERLINK(\"x\")", VerdictEngine.csvSafe("=HYPERLINK(\"x\")"))
        assertEquals("'@SUM(1)", VerdictEngine.csvSafe("@SUM(1)"))
        assertEquals("집", VerdictEngine.csvSafe("집"))
        assertEquals("12.5", VerdictEngine.csvSafe("12.5"))
    }
}
