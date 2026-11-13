package com.verified.incar

import com.verified.incar.ui.PhoneText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 폰 글자 크기 표: 기준(Material 3·Now in Android)에 맞고, 커질수록 덜 커지며, 거꾸로 바꿔도 같은 값이 나온다. */
class PhoneTextTest {
    @Test fun mapsCarSizesToPhoneScale() {
        assertEquals(13.5f, PhoneText.phoneSp(14f), 0.01f)   // 칩·버튼
        assertEquals(14.5f, PhoneText.phoneSp(16f), 0.01f)   // 본문
        assertEquals(17.5f, PhoneText.phoneSp(23f), 0.01f)   // 카드 제목
        assertEquals(24f, PhoneText.phoneSp(40f), 0.01f)     // 화면 큰 제목
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
