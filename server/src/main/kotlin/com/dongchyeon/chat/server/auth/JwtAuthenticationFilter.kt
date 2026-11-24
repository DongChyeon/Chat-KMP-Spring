package com.dongchyeon.chat.server.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// 요청마다 Authorization 헤더의 Access Token을 검증한다 (ADR 0013, 방식 D).
// @Component를 붙이지 않는다. 붙이면 Spring Boot가 이 필터를 서블릿 필터로도 자동 등록해서,
// Security 필터 체인 밖에서 한 번 더 실행된다. SecurityConfig에서 직접 만들어 체인에만 넣는다.
class JwtAuthenticationFilter(
    private val accessTokenVerifier: AccessTokenVerifier,
    private val entryPoint: JwtAuthenticationEntryPoint,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)

        // 토큰이 없으면 인증 정보 없이(익명) 넘긴다. 들여보낼지는 AuthorizationFilter가 경로 규칙으로 정한다
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        // 토큰이 있는데 잘못됐으면 경로와 관계없이 여기서 401로 끝낸다 (permitAll 경로도 마찬가지)
        val userId = try {
            accessTokenVerifier.verify(token).userId
        } catch (e: JwtAuthenticationException) {
            entryPoint.commence(request, response, e)
            return // filterChain.doFilter를 부르지 않으므로 뒤의 필터와 컨트롤러로 가지 않는다
        }

        // 인증 정보를 등록한다. 컨트롤러에서 @AuthenticationPrincipal userId: Long 으로 꺼낼 수 있다
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList())
        filterChain.doFilter(request, response)
    }

    // "Authorization: Bearer <token>" 형식이 아니거나 값이 비어 있으면 토큰이 없는 것으로 본다
    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.removePrefix(BEARER_PREFIX).trim().ifEmpty { null }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
