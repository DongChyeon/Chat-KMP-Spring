package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.LogoutRequest
import com.dongchyeon.chat.shared.auth.RefreshRequest
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.auth.UserResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.error.ErrorResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// 테스트끼리 데이터가 섞이지 않도록 매번 다른 이메일을 쓴다 (ADR 0018 나중에 알게 된 것)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AuthFlowTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtProvider: JwtProvider,
) {

    @Test
    fun `가입, 로그인, 재발급, 로그아웃 흐름`() {
        val email = uniqueEmail()
        assertEquals(201, signup(email).response.status)

        val tokens = login(email).body<TokenResponse>()
        assertEquals(email, getMe(tokens.accessToken).body<UserResponse>().email)

        val refreshed = refresh(tokens.refreshToken).body<TokenResponse>()
        assertNotEquals(tokens.accessToken, refreshed.accessToken)
        assertEquals(tokens.refreshToken, refreshed.refreshToken) // ADR 0010: 교체하지 않음
        assertEquals(200, getMe(refreshed.accessToken).response.status)

        assertEquals(204, logout(refreshed.accessToken, DEVICE_ID).response.status)
        refresh(tokens.refreshToken).assertError(401, ErrorCode.REFRESH_FAILED)
    }

    @Nested
    inner class 회원가입과_로그인 {
        @Test
        fun `같은 이메일로 다시 가입하면 409 DUPLICATE_EMAIL`() {
            val email = uniqueEmail()
            signup(email)
            signup(email).assertError(409, ErrorCode.DUPLICATE_EMAIL)
        }

        @Test
        fun `비밀번호가 틀린 경우와 없는 이메일은 같은 응답`() {
            val email = uniqueEmail()
            signup(email)
            login(email, password = "WrongPass!123").assertError(401, ErrorCode.INVALID_CREDENTIALS)
            login(uniqueEmail()).assertError(401, ErrorCode.INVALID_CREDENTIALS)
        }
    }

    @Nested
    inner class 인증_필터 {
        @Test
        fun `토큰 없이 보호 API를 호출하면 UNAUTHORIZED`() {
            mockMvc.get(ApiPaths.ME).andReturn().assertError(401, ErrorCode.UNAUTHORIZED)
        }

        @Test
        fun `만료된 Access Token은 TOKEN_EXPIRED`() {
            val userId = signupAndLogin().userId
            val expired = jwtProvider.createAccessToken(userId, now = Instant.now().minus(Duration.ofHours(1)))
            getMe(expired).assertError(401, ErrorCode.TOKEN_EXPIRED)
        }

        @Test
        fun `서명이 변조된 토큰과 Refresh Token은 INVALID_TOKEN`() {
            val tokens = signupAndLogin().tokens
            getMe(tamper(tokens.accessToken)).assertError(401, ErrorCode.INVALID_TOKEN)
            getMe(tokens.refreshToken).assertError(401, ErrorCode.INVALID_TOKEN)
        }
    }

    // ADR 0014: 원인과 관계없이 같은 응답
    @Nested
    inner class 재발급_실패 {
        @Test
        fun `Access Token을 넣으면 REFRESH_FAILED`() {
            refresh(signupAndLogin().tokens.accessToken).assertError(401, ErrorCode.REFRESH_FAILED)
        }

        @Test
        fun `서명이 변조되면 REFRESH_FAILED`() {
            refresh(tamper(signupAndLogin().tokens.refreshToken)).assertError(401, ErrorCode.REFRESH_FAILED)
        }

        @Test
        fun `만료되면 REFRESH_FAILED`() {
            val expired = jwtProvider.createRefreshToken(signupAndLogin().userId, now = Instant.now().minus(Duration.ofDays(31)))
            refresh(expired.value).assertError(401, ErrorCode.REFRESH_FAILED)
        }

        @Test
        fun `같은 기기에서 다시 로그인하면 이전 Refresh Token은 REFRESH_FAILED`() {
            val (email, _, first) = signupAndLogin()
            login(email)
            refresh(first.refreshToken).assertError(401, ErrorCode.REFRESH_FAILED)
        }
    }

    @Nested
    inner class 로그아웃 {
        @Test
        fun `다른 기기의 Refresh Token은 그대로 유효하다`() {
            val (email, _, phone) = signupAndLogin()
            val tablet = login(email, deviceId = "tablet").body<TokenResponse>()

            logout(phone.accessToken, DEVICE_ID)

            refresh(phone.refreshToken).assertError(401, ErrorCode.REFRESH_FAILED)
            assertEquals(200, refresh(tablet.refreshToken).response.status)
        }

        @Test
        fun `이미 로그아웃한 기기를 다시 로그아웃해도 204`() {
            val tokens = signupAndLogin().tokens
            logout(tokens.accessToken, DEVICE_ID)
            assertEquals(204, logout(tokens.accessToken, DEVICE_ID).response.status)
        }

        @Test
        fun `토큰 없이 호출하면 UNAUTHORIZED`() {
            mockMvc.post(ApiPaths.LOGOUT) { json(LogoutRequest(DEVICE_ID)) }.andReturn()
                .assertError(401, ErrorCode.UNAUTHORIZED)
        }
    }

    // --- helpers ---

    private data class LoggedIn(val email: String, val userId: Long, val tokens: TokenResponse)

    private fun signupAndLogin(): LoggedIn {
        val email = uniqueEmail()
        val userId = signup(email).body<UserResponse>().id
        return LoggedIn(email, userId, login(email).body())
    }

    private fun signup(email: String) =
        mockMvc.post(ApiPaths.SIGNUP) { json(SignupRequest(email, PASSWORD, "tester")) }.andReturn()

    private fun login(email: String, password: String = PASSWORD, deviceId: String = DEVICE_ID) =
        mockMvc.post(ApiPaths.LOGIN) { json(LoginRequest(email, password, deviceId)) }.andReturn()

    private fun refresh(refreshToken: String) =
        mockMvc.post(ApiPaths.REFRESH) { json(RefreshRequest(refreshToken)) }.andReturn()

    private fun logout(accessToken: String, deviceId: String) =
        mockMvc.post(ApiPaths.LOGOUT) {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            json(LogoutRequest(deviceId))
        }.andReturn()

    private fun getMe(accessToken: String) =
        mockMvc.get(ApiPaths.ME) { header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken") }.andReturn()

    private inline fun <reified T> org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: T) {
        contentType = MediaType.APPLICATION_JSON
        content = Json.encodeToString(body)
    }

    private inline fun <reified T> MvcResult.body(): T = Json.decodeFromString(response.getContentAsString(Charsets.UTF_8))

    private fun MvcResult.assertError(status: Int, code: ErrorCode) {
        assertEquals(status, response.status, response.getContentAsString(Charsets.UTF_8))
        assertEquals(code, body<ErrorResponse>().code)
    }

    // 서명 부분의 중간 글자를 바꾼다 (마지막 글자는 패딩 비트라 바꿔도 같은 값일 수 있다)
    private fun tamper(token: String): String {
        val i = token.lastIndexOf('.') + 5
        return token.replaceRange(i, i + 1, if (token[i] == 'A') "B" else "A")
    }

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@test.com"

    companion object {
        private const val PASSWORD = "Passw0rd!123"
        private const val DEVICE_ID = "phone"
    }
}
