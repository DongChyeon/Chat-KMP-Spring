package com.dongchyeon.chat.shared.message

import kotlinx.serialization.Serializable

// ADR 0041: 입장/퇴장은 문구 없이 종류만 저장하고, 앱이 종류와 당사자(sender)로 문구를 만든다
@Serializable
enum class MessageType { TEXT, JOIN, LEAVE }
