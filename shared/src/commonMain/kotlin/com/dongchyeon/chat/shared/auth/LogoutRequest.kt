package com.dongchyeon.chat.shared.auth

import kotlinx.serialization.Serializable

// userId는 Access Token에서 꺼내고, 지울 기기만 본문으로 받는다 (ADR 0016)
@Serializable
data class LogoutRequest(
    val deviceId: String,
)
