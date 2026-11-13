package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈 맨 위 한 줄 판정: 주의·위험 카드만 세고, 무엇을 근거로 언제 판정했는지 밝힌다. */
class HomeVerdictTest {
    private val ok = FeedCard("오늘은 챙길 것이 없어요", "", Level.OK, "SERVICE")
    private val info = FeedCard("② 빌려준 차 반납 리포트", "", Level.INFO, "DRIVER")
    private val warn = FeedCard("왼쪽 뒤 바퀴만 천천히 빠지고 있어요", "", Level.WARN, "SERVICE")
    private val danger = FeedCard("비상등을 켜셨어요 · 먼저 대피하세요", "", Level.DANGER, "CARLIFE")

    @Test fun infoOnly_isAllVerified_andSaysSimulator() {
        val v = MyHyundai.verdict(listOf(ok, info), null, carConnected = false, simAllowed = true, unread = 0)
        assertEquals("모두 확인됨", v.word)
        assertEquals(Level.OK, v.level)
        assertNull(v.tab)
        assertEquals("차량 연결 전 · 예시 값 기준", v.basis)
    }

    @Test fun warningsAreCounted_dangerWins_andOpensFirstIssue() {
        val v = MyHyundai.verdict(listOf(danger, warn, info), null, carConnected = false, simAllowed = true, unread = 0)
        assertEquals("확인 필요 2건", v.word)
        assertEquals(Level.DANGER, v.level)
        assertEquals("CARLIFE", v.tab)
        assertEquals(danger.title, v.detail)
    }

    @Test fun onCar_showsLastSignalTime_andUnreadCount() {
        val v = MyHyundai.verdict(listOf(ok), 5_000L, carConnected = true, simAllowed = false, unread = 3)
        assertTrue(v.basis, v.basis.startsWith("최근 차 신호 ") && v.basis.endsWith("확인 불가 3건"))
        // 차에 붙었는데 아직 신호가 하나도 안 왔으면 '모두 확인됨'이라고 말하지 않는다
        val waiting = MyHyundai.verdict(listOf(ok), null, carConnected = true, simAllowed = false, unread = 0)
        assertEquals("확인 중", waiting.word)
    }
}
