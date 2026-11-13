package com.verified.incar.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RetentionTest {
    private fun log(date: String) = TripLog(date, "차주", "집", "회사", 0.0, 10.0, "출퇴근", true)

    @Test fun tripLogs_keepFiveYears_andKeepUnreadableDates() {
        val today = LocalDate.of(2026, 11, 5)
        val kept = VerdictEngine.keepRecent(listOf(log("2026-11-01"), log("2021-11-05"), log("2021-11-04"), log("날짜없음")), today)
        assertEquals(listOf("2026-11-01", "2021-11-05", "날짜없음"), kept.map { it.date })
    }
}
