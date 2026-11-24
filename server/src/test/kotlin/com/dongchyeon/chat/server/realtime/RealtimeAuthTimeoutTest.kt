package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.shared.realtime.RealtimeCloseCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.time.Duration
import kotlin.test.assertEquals

// ADR 0031: AUTH를 보내지 않는 연결은 타임아웃으로 닫는다.
// 서버가 동시에 받는 연결 수를 MAX_CONNECTIONS로 줄여, 닫지 않으면 무슨 일이 생기는지 보이게 한다
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "realtime.auth-timeout=1s",
        "server.tomcat.max-connections=${RealtimeAuthTimeoutTest.MAX_CONNECTIONS}",
    ],
)
@Import(TestcontainersConfiguration::class)
class RealtimeAuthTimeoutTest(@LocalServerPort private val port: Int) {
    private val clients = mutableListOf<RealtimeTestClient>()

    @AfterEach
    fun closeAll() = clients.forEach { it.close() }

    private fun connect(timeout: Duration = Duration.ofSeconds(3)) =
        RealtimeTestClient.connect(port, timeout).also { clients.add(it) }

    @Test
    fun `AUTH를 보내지 않으면 타임아웃 뒤 AUTH_TIMEOUT으로 닫힌다`() {
        val client = connect()

        assertEquals(RealtimeCloseCode.AUTH_TIMEOUT, client.awaitClose(Duration.ofSeconds(3)))
    }

    @Test
    fun `AUTH 없이 버티는 연결이 자리를 다 차지해도 타임아웃 뒤에는 새 연결이 들어온다`() {
        repeat(MAX_CONNECTIONS) { connect() }
        println("===== AUTH 없는 연결 ${MAX_CONNECTIONS}개로 자리를 채움. 새 연결 시도 =====")

        // 타임아웃(1초)이 지나 자리가 비면 연결된다. 자리가 비지 않으면 connect가 시간 초과로 실패한다
        connect(timeout = Duration.ofSeconds(4))
        println("===== 새 연결 성공 =====")
    }

    companion object {
        const val MAX_CONNECTIONS = 5
    }
}
