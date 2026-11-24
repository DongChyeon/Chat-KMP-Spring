package com.dongchyeon.chat.server.realtime

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("realtime")
data class RealtimeProperties(
    val authTimeout: Duration,
)
