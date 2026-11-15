package com.verified.incar

import com.verified.incar.core.Level
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PhoneRelayTest {
    @Test fun generatedTopicAndPayloadNeverIncludePrivateRecord() {
        val topic = PhoneRelay.newTopic()
        assertTrue(PhoneRelay.validTopic(topic))
        assertNotEquals(topic, PhoneRelay.newTopic())
        assertFalse(PhoneRelay.validTopic("https://other.test/$topic"))
        val notice = SentNotice(0, "차주 폰", "김민수 · 수리비 30만원", "집 좌표 37.5,126.9", Level.WARN, phoneExample = true)
        assertEquals("예시 차량 · 차량에서 확인할 사항이 있어요. 자세한 내용은 현대 카가드 앱에서 확인하세요.", PhoneRelay.message(notice))
    }

    @Test fun postsUtf8WithStableSequenceAndHonorsHttpResults() {
        val body = "현대 카가드 시험 알림이 도착했어요."
        val sequence = "84640030-1cb0-41ea-aab2-603acf2747e7"
        val worker = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5_000
                val endpoint = URL("http://127.0.0.1:${server.localPort}/paired")
                for ((code, expected) in listOf(200 to PhoneDelivery.ACCEPTED, 429 to PhoneDelivery.QUEUED,
                    503 to PhoneDelivery.QUEUED, 403 to PhoneDelivery.FAILED, 302 to PhoneDelivery.FAILED)) {
                    val received = worker.submit<Unit> {
                        server.accept().use { client ->
                            client.soTimeout = 5_000
                            val stream = DataInputStream(client.getInputStream().buffered())
                            @Suppress("DEPRECATION")
                            fun header() = checkNotNull(stream.readLine()) // ASCII HTTP headers
                            assertEquals("POST /paired HTTP/1.1", header())
                            val headers = generateSequence(::header).takeWhile { it.isNotEmpty() }
                                .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                            val bytes = ByteArray(headers.getValue("content-length").toInt())
                            stream.readFully(bytes)
                            assertEquals(body, bytes.toString(Charsets.UTF_8))
                            assertEquals("Hyundai CarGuard", headers["x-title"])
                            assertEquals(sequence, headers["x-sequence-id"])
                            val response = "HTTP/1.1 $code Test\r\nConnection: close\r\nRetry-After: 30\r\nLocation: /unexpected\r\nContent-Length: 0\r\n\r\n"
                            client.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                        }
                    }
                    val result = PhoneRelay.post(endpoint, body, sequence)
                    received.get(5, TimeUnit.SECONDS)
                    assertEquals(expected, result.status)
                    if (expected == PhoneDelivery.QUEUED) assertEquals(30_000L, result.retryAfterMs)
                }
            }
        } finally {
            worker.shutdownNow()
        }
    }
}
