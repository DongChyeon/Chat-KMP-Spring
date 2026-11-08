package com.dongchyeon.chat.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String,
)
