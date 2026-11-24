package com.dongchyeon.chat.shared.message

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// 실시간 전달(ServerFrame.Message)과 이력 조회가 같은 형태를 쓴다
@Serializable
data class MessageResponse(
    val id: Long, // 전역 id. 방 안의 순서이자 커서 기준이지만 연속은 아니다 (ADR 0037)
    val roomId: Long,
    val type: MessageType,
    val senderId: Long, // JOIN/LEAVE는 입장·퇴장한 당사자
    val senderNickname: String, // 나간 사람은 참여자 목록에 없으므로 메시지에 함께 싣는다
    val content: String?, // JOIN/LEAVE는 null
    val createdAt: Instant,
)
