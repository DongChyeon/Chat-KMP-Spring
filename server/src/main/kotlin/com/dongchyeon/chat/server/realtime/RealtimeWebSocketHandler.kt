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
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference

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
        session.attributes[AUTH_STATE] = AtomicReference(AuthState.PENDING)
        // ADR 0031: authTimeout 안에 AUTH가 오지 않으면 닫는다. 인증 없이 자리만 차지하는 연결을 막는다
        val timeoutTask = scheduler.schedule({
            if (!session.isOpen) return@schedule
            // ADR 0044: 확인과 표시를 한 번에 한다. 실패했다면 AUTH가 먼저 AUTHENTICATED로 바꿨다
            if (!session.authState().compareAndSet(AuthState.PENDING, AuthState.TIMED_OUT)) return@schedule

            log.info("ws auth timeout: session={}", session.id)
            try {
                session.close(CloseStatus(RealtimeCloseCode.AUTH_TIMEOUT, "auth timeout"))
            } catch (e: IOException) {
                log.warn("ws close failed: session={}", session.id, e)
            }
        }, Instant.now().plus(properties.authTimeout))

        session.attributes[AUTH_TIMEOUT_TASK] = timeoutTask
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
        session.timeoutTask()?.cancel(false) // 인증 전에 끊긴 연결의 예약도 정리한다
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

        // ADR 0044: 실패했다면 타임아웃이 먼저 TIMED_OUT으로 바꿨다. 닫히는 중이므로 AUTH_OK를 보내지 않는다
        if (!session.authState().compareAndSet(AuthState.PENDING, AuthState.AUTHENTICATED)) {
            log.info("ws auth lost to timeout: session={}", session.id)
            return
        }
        // 이긴 뒤에는 타임아웃 작업이 할 일이 없다. 시작 전이면 예약을 지워 스케줄러 큐에서 뺀다
        session.timeoutTask()?.cancel(false)
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

    private fun WebSocketSession.timeoutTask(): ScheduledFuture<*>? = attributes[AUTH_TIMEOUT_TASK] as ScheduledFuture<*>?

    @Suppress("UNCHECKED_CAST")
    private fun WebSocketSession.authState(): AtomicReference<AuthState> = attributes[AUTH_STATE] as AtomicReference<AuthState>

    companion object {
        private const val PRINCIPAL = "realtime.principal"
        private const val AUTH_TIMEOUT_TASK = "realtime.authTimeoutTask"
        private const val AUTH_STATE = "realtime.authState"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

// ADR 0044: 타임아웃과 AUTH 중 먼저 PENDING에서 벗어난 쪽만 이긴다
enum class AuthState { PENDING, AUTHENTICATED, TIMED_OUT }

data class RealtimePrincipal(val userId: Long, val deviceId: String)
