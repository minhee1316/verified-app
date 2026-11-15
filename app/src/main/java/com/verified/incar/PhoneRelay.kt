package com.verified.incar

import com.verified.incar.core.Level
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class PhoneDelivery { QUEUED, ACCEPTED, FAILED }

internal object PhoneRelay {
    private val topicPattern = Regex("verified-[a-f0-9]{32}")
    const val TEST_TITLE = "휴대폰 시험 알림"
    fun newTopic() = "verified-" + UUID.randomUUID().toString().replace("-", "")
    fun validTopic(topic: String) = topicPattern.matches(topic)

    // 자유 입력·위치·차량 식별자가 든 원문은 차에만 남긴다.
    fun message(n: SentNotice): String {
        if (n.title == TEST_TITLE) return "현대 카가드 시험 알림이 도착했어요."
        val summary = when {
            n.level == Level.DANGER || n.level == Level.WARN -> "차량에서 확인할 사항이 있어요."
            "반납" in n.title || "인수 리포트" in n.title || "대리 주차 완료" in n.title -> "차를 돌려받은 기록이 준비됐어요."
            listOf("맡기기 시작", "빌려주기 시작", "대리운전 시작").any { it in n.title } -> "차를 맡긴 기록이 생겼어요."
            else -> "차량 알림이 있어요."
        }
        return (if (n.phoneExample) "예시 차량 · " else "") + summary + " 자세한 내용은 현대 카가드 앱에서 확인하세요."
    }

    data class Result(val status: PhoneDelivery, val retryAfterMs: Long = 0)

    fun publish(topic: String, notice: SentNotice): Result {
        val sequence = notice.phoneId ?: return Result(PhoneDelivery.FAILED)
        if (!validTopic(topic) || runCatching { UUID.fromString(sequence).toString() }.getOrNull() != sequence)
            return Result(PhoneDelivery.FAILED)
        return post(URL("https://ntfy.sh/$topic"), message(notice), sequence)
    }

    internal fun post(url: URL, message: String, sequence: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            val request = (url.openConnection() as HttpURLConnection).also { connection = it }
            request.apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setRequestProperty("X-Title", "Hyundai CarGuard")
                setRequestProperty("X-Sequence-ID", sequence)
                setFixedLengthStreamingMode(bytes.size)
                outputStream.use { it.write(bytes) }
            }
            when (request.responseCode) {
                in 200..299 -> Result(PhoneDelivery.ACCEPTED)
                408, 425, 429, in 500..599 -> Result(PhoneDelivery.QUEUED,
                    (request.getHeaderField("Retry-After")?.toLongOrNull() ?: 0).coerceIn(0, 86_400) * 1000)
                else -> Result(PhoneDelivery.FAILED)
            }
        } catch (_: IOException) {
            Result(PhoneDelivery.QUEUED)
        } finally {
            connection?.disconnect()
        }
    }
}
