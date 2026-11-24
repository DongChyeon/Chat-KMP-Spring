package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.shared.ApiPaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

// ADR 0030: STOMP 브로커 없이 핸들러 하나를 엔드포인트에 직접 연결한다
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val realtimeHandler: RealtimeWebSocketHandler,
) : WebSocketConfigurer {

    // 기본 설정은 Origin 헤더가 있으면 같은 출처만 허용한다. 앱(OkHttp, URLSession)은 Origin을 보내지 않아 통과한다
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(realtimeHandler, ApiPaths.REALTIME)
    }
}

// 위 설정은 핸들러를 주입받으므로, 핸들러가 쓰는 스케줄러를 같은 클래스에 두면 순환 의존이 된다
@Configuration
class RealtimeSchedulerConfig {

    @Bean(REALTIME_SCHEDULER)
    fun realtimeTaskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            setPoolSize(1)
            setThreadNamePrefix("realtime-")
        }

    companion object {
        // @EnableWebSocket도 TaskScheduler 빈(SockJS용)을 등록하므로 이름으로 고른다
        const val REALTIME_SCHEDULER = "realtimeTaskScheduler"
    }
}
