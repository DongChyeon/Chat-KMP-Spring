package com.dongchyeon.chat.shared.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 클라이언트 → 서버 WebSocket 프레임 (ADR 0030). JSON의 "type" 필드로 종류를 구분한다
@Serializable
sealed interface ClientFrame {

    // 연결 직후 첫 프레임. 인증 전에는 이것만 받는다 (ADR 0031)
    @Serializable
    @SerialName("AUTH")
    data class Auth(val accessToken: String) : ClientFrame

    // requestId는 클라이언트가 매기고, 서버는 SendAck/Error에 그대로 돌려준다 (ADR 0034)
    @Serializable
    @SerialName("SEND")
    data class Send(
        val requestId: String,
        val roomId: Long,
        val content: String,
    ) : ClientFrame
}
