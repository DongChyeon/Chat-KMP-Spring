package com.dongchyeon.chat.shared.room

import kotlinx.serialization.Serializable

// 내 채팅방 목록의 한 줄 (ADR 0029). 참여자 목록은 담지 않는다
@Serializable
data class RoomSummaryResponse(
    val id: Long,
    val type: RoomType,
    val title: String, // GROUP 방은 방 이름, DIRECT 방은 상대 닉네임
)
