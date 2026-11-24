package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.server.auth.AccessTokenVerifier
import com.dongchyeon.chat.server.auth.JwtProperties
import com.dongchyeon.chat.server.auth.JwtProvider
import com.dongchyeon.chat.shared.realtime.ClientFrame
import com.dongchyeon.chat.shared.realtime.RealtimeCloseCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

// ADR 0044: 타임아웃과 AUTH의 실행 순서를 테스트가 직접 정한다. 스레드 경쟁을 기다리지 않고 결과를 확인한다
class AuthTimeoutRaceTest {
    private val jwtProvider = JwtProvider(JwtProperties("test-secret-0123456789-0123456789-0123", Duration.ofMinutes(15), Duration.ofDays(30)))
    private val scheduler = mock(TaskScheduler::class.java).also {
        `when`(it.schedule(any(Runnable::class.java), any(Instant::class.java))).thenAnswer { mock(ScheduledFuture::class.java) }
    }
    private val handler = RealtimeWebSocketHandler(AccessTokenVerifier(jwtProvider), RealtimeProperties(Duration.ofSeconds(5)), scheduler)

    // 서버가 close를 불러도 mock 세션은 열린 채로 남는다. "close를 정했지만 아직 닫히기 전"에 AUTH가 도착한 상태다
    private fun openSession(): WebSocketSession = mock(WebSocketSession::class.java).also {
        `when`(it.id).thenReturn("s1")
        `when`(it.isOpen).thenReturn(true)
        `when`(it.attributes).thenReturn(mutableMapOf())
    }

    private fun connectAndCaptureTimeout(session: WebSocketSession): Runnable {
        val task = ArgumentCaptor.forClass(Runnable::class.java)
        handler.afterConnectionEstablished(session)
        verify(scheduler).schedule(task.capture(), any(Instant::class.java))
        return task.value
    }

    private fun auth(session: WebSocketSession) =
        handler.handleMessage(session, TextMessage(Json.encodeToString<ClientFrame>(ClientFrame.Auth(jwtProvider.createAccessToken(1, "phone")))))

    @Test
    fun `타임아웃이 먼저 이기면 뒤늦은 AUTH에 AUTH_OK를 보내지 않는다`() {
        val session = openSession()
        val timeout = connectAndCaptureTimeout(session)

        timeout.run()
        auth(session)

        verify(session).close(CloseStatus(RealtimeCloseCode.AUTH_TIMEOUT, "auth timeout"))
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `AUTH가 먼저 이기면 타임아웃은 닫지 않는다`() {
        val session = openSession()
        val timeout = connectAndCaptureTimeout(session)

        auth(session)
        timeout.run()

        verify(session).sendMessage(any()) // AUTH_OK
        verify(session, never()).close(any())
    }
}
