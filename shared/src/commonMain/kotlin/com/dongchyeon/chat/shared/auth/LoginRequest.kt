package com.dongchyeon.chat.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    // 기기별로 Refresh Token을 저장한다 (ADR 0010). 앱이 설치될 때 만든 값을 계속 보낸다.
    val deviceId: String,
)
