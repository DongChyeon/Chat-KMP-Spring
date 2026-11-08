package com.dongchyeon.chat.server.config

import com.dongchyeon.chat.shared.ApiPaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

// 5번(인증 필터)에서 JWT 필터를 추가하면서 다시 다룬다
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(ApiPaths.SIGNUP, permitAll)
                authorize(ApiPaths.LOGIN, permitAll)
                authorize(ApiPaths.INFO, permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }

    // ADR 0007: 해시 앞에 {bcrypt} 접두사를 붙여 저장한다. 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다.
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
