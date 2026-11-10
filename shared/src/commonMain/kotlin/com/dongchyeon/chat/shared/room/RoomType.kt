package com.dongchyeon.chat.shared.room

import kotlinx.serialization.Serializable

// ADR 0019
@Serializable
enum class RoomType { DIRECT, GROUP }
