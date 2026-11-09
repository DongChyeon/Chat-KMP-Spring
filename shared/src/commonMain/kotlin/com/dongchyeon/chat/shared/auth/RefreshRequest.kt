package com.dongchyeon.chat.shared.auth

import kotlinx.serialization.Serializable

// deviceId는 받지 않는다. token_hash 하나로 기기 행을 찾는다 (ADR 0015)
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)
