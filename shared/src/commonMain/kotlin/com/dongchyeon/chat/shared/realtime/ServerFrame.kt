package com.dongchyeon.chat.shared.realtime

import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.message.MessageResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 서버 → 클라이언트 WebSocket 프레임. JSON의 "type" 필드로 종류를 구분한다
@Serializable
sealed interface ServerFrame {

    @Serializable
    @SerialName("AUTH_OK")
    data class AuthOk(val userId: Long) : ServerFrame

    // 커밋이 끝난 뒤 보낸 사람에게만 보낸다 (ADR 0035)
    @Serializable
    @SerialName("SEND_ACK")
    data class SendAck(
        val requestId: String,
        val message: MessageResponse,
    ) : ServerFrame

    // 방 참여자에게 한 번만 시도하는 전달. 빠진 것은 이력 조회로 메운다 (ADR 0036)
    @Serializable
    @SerialName("MESSAGE")
    data class Message(val message: MessageResponse) : ServerFrame

    // requestId가 있으면 그 SEND의 실패, 없으면 연결 단위 실패(인증 등)
    @Serializable
    @SerialName("ERROR")
    data class Error(
        val code: ErrorCode,
        val requestId: String? = null,
    ) : ServerFrame
}
