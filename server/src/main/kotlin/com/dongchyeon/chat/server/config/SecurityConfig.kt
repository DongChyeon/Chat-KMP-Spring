package com.dongchyeon.chat.server.config

import com.dongchyeon.chat.server.auth.JwtAuthenticationEntryPoint
import com.dongchyeon.chat.server.auth.JwtAuthenticationFilter
import com.dongchyeon.chat.server.auth.JwtProvider
import com.dongchyeon.chat.shared.ApiPaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val entryPoint: JwtAuthenticationEntryPoint,
) {

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
            // 등록하지 않으면 기본값 Http403ForbiddenEntryPoint가 403과 Spring 기본 본문을 보낸다 (ADR 0013)
            exceptionHandling { authenticationEntryPoint = entryPoint }
            // 아이디/비밀번호 폼 로그인 필터 자리 앞에 끼워 넣는다. 인가 필터보다 먼저 실행되어야 한다
            addFilterBefore<UsernamePasswordAuthenticationFilter>(JwtAuthenticationFilter(jwtProvider, entryPoint))
        }
        return http.build()
    }

    // ADR 0007: 해시 앞에 {bcrypt} 접두사를 붙여 저장한다. 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다.
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
