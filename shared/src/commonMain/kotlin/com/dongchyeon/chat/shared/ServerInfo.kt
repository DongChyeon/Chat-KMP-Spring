package com.dongchyeon.chat.shared

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val name: String,
    val version: String,
)
