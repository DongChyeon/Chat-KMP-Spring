package com.dongchyeon.chat.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
)
