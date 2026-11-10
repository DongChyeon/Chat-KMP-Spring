package com.dongchyeon.chat.shared.room

import kotlinx.serialization.Serializable

// 이미 방이 있으면 그 방을 돌려준다 (ADR 0022 추가 결정)
@Serializable
data class CreateDirectRoomRequest(
    val targetUserId: Long,
)

@Serializable
data class CreateGroupRoomRequest(
    val name: String,
)
