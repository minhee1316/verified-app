package com.verified.incar

import com.verified.incar.ui.PhoneText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 참고 시안의 큰 글자 위계와 역변환·단조 증가를 유지한다. */
class PhoneTextTest {
    @Test fun mapsCarSizesToPhoneScale() {
        assertEquals(14f, PhoneText.phoneSp(14f), 0.01f)     // 보조 설명
        assertEquals(16f, PhoneText.phoneSp(16f), 0.01f)     // 본문
        assertEquals(22f, PhoneText.phoneSp(23f), 0.01f)     // 카드 제목
        assertEquals(32f, PhoneText.phoneSp(40f), 0.01f)     // 화면 큰 제목
        // 커질수록 덜 커지고 순서는 그대로
        var prev = 0f
        for (car in 8..130) {
            val p = PhoneText.phoneSp(car.toFloat())
            assertTrue("$car → $p", p > prev && p <= car)
            prev = p
            assertEquals(car.toFloat(), PhoneText.carSp(p), 0.01f)
        }
    }
}
