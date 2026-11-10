package com.dongchyeon.chat.shared.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomResponse(
    val id: Long,
    val type: RoomType,
    val name: String?, // DIRECT 방은 null. 앱은 상대 닉네임을 보여준다
    val members: List<RoomMemberResponse>,
)

@Serializable
data class RoomMemberResponse(
    val userId: Long,
    val nickname: String,
)
