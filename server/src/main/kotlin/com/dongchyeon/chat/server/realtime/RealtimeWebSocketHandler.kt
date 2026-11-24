package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.server.auth.AccessTokenVerifier
import com.dongchyeon.chat.server.auth.JwtAuthenticationException
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.realtime.ClientFrame
import com.dongchyeon.chat.shared.realtime.RealtimeCloseCode
import com.dongchyeon.chat.shared.realtime.ServerFrame
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

// 연결 하나의 상태: 인증 전 → 인증됨 (ADR 0031)
@Component
class RealtimeWebSocketHandler(
    private val accessTokenVerifier: AccessTokenVerifier,
    private val properties: RealtimeProperties,
    @Qualifier(RealtimeSchedulerConfig.REALTIME_SCHEDULER) private val scheduler: TaskScheduler,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("ws connected: session={}", session.id)
        // TODO(learn): properties.authTimeout 안에 AUTH가 오지 않으면 RealtimeCloseCode.AUTH_TIMEOUT으로 연결을 닫기
        //  — 힌트: scheduler.schedule(작업, 실행 시각), 실행 시점에 아직 인증 전인지 확인(principal), 이미 닫혔다면?
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val frame = try {
            json.decodeFromString<ClientFrame>(message.payload)
        } catch (e: SerializationException) {
            if (session.principal() == null) {
                rejectAuth(session, ErrorCode.INVALID_REQUEST)
            } else {
                send(session, ServerFrame.Error(ErrorCode.INVALID_REQUEST))
            }
            return
        }

        val principal = session.principal()
        if (principal == null) {
            if (frame is ClientFrame.Auth) authenticate(session, frame) else rejectAuth(session, ErrorCode.UNAUTHORIZED)
            return
        }

        when (frame) {
            is ClientFrame.Auth -> send(session, ServerFrame.Error(ErrorCode.INVALID_REQUEST)) // 이미 인증된 연결
            // 메시지 전송(Issue #9 작업 목록 5번)에서 저장 후 전달을 붙인다
            is ClientFrame.Send -> send(session, ServerFrame.Error(ErrorCode.INVALID_REQUEST, frame.requestId))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        log.info("ws closed: session={}, userId={}, status={}", session.id, session.principal()?.userId, status)
    }

    private fun authenticate(session: WebSocketSession, frame: ClientFrame.Auth) {
        val verified = try {
            accessTokenVerifier.verify(frame.accessToken)
        } catch (e: JwtAuthenticationException) {
            rejectAuth(session, e.code)
            return
        }
        // ADR 0033: 로그아웃 때 이 기기의 연결만 닫으려면 deviceId가 있어야 한다
        val deviceId = verified.deviceId ?: run {
            rejectAuth(session, ErrorCode.INVALID_TOKEN)
            return
        }

        session.attributes[PRINCIPAL] = RealtimePrincipal(verified.userId, deviceId)
        send(session, ServerFrame.AuthOk(verified.userId))
        log.info("ws authenticated: session={}, userId={}, deviceId={}", session.id, verified.userId, deviceId)
    }

    // 인증 전 실패는 원인을 ERROR 프레임으로 알린 뒤 닫는다. close code만으로는 만료와 위조를 구분할 수 없다
    private fun rejectAuth(session: WebSocketSession, code: ErrorCode) {
        send(session, ServerFrame.Error(code))
        session.close(CloseStatus(RealtimeCloseCode.AUTH_FAILED))
    }

    // WebSocketSession.sendMessage는 동시에 부르면 예외가 난다. 연결 관리(4번)에서 세션 단위 직렬화로 바꾼다 (ADR 0036)
    private fun send(session: WebSocketSession, frame: ServerFrame) {
        synchronized(session) {
            if (session.isOpen) session.sendMessage(TextMessage(json.encodeToString(frame)))
        }
    }

    private fun WebSocketSession.principal(): RealtimePrincipal? = attributes[PRINCIPAL] as RealtimePrincipal?

    companion object {
        private const val PRINCIPAL = "realtime.principal"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

data class RealtimePrincipal(val userId: Long, val deviceId: String)
