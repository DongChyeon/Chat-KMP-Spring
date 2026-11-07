package com.dongchyeon.chat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform