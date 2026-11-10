package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.server.runConcurrently
import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.LogoutRequest
import com.dongchyeon.chat.shared.auth.RefreshRequest
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals

// 실제 포트로 띄워 요청마다 Tomcat 스레드에서 처리되게 한다.
// @Transactional(테스트 롤백)을 붙이지 않는다: 커밋 전 데이터는 다른 스레드에 보이지 않아 거짓 통과한다 (ADR 0018)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class AuthConcurrencyTest(
    @LocalServerPort private val port: Int,
) {
    private val client = HttpClient.newHttpClient()

    @Test
    fun `같은 이메일로 동시에 가입하면 1건만 성공한다`() {
        val email = uniqueEmail()

        val statuses = runConcurrently(THREADS) { post(ApiPaths.SIGNUP, SignupRequest(email, PASSWORD, "racer")).statusCode() }

        assertEquals(1, statuses.count { it == 201 }, statuses.toString())
        assertEquals(THREADS - 1, statuses.count { it == 409 }, statuses.toString())
    }

    @Test
    fun `같은 기기 로그아웃을 동시에 보내도 모두 204`() {
        val email = uniqueEmail()
        post(ApiPaths.SIGNUP, SignupRequest(email, PASSWORD, "racer"))
        val tokens = Json.decodeFromString<TokenResponse>(post(ApiPaths.LOGIN, LoginRequest(email, PASSWORD, DEVICE_ID)).body())

        val statuses = runConcurrently(THREADS) {
            post(ApiPaths.LOGOUT, LogoutRequest(DEVICE_ID), tokens.accessToken).statusCode()
        }

        assertEquals(List(THREADS) { 204 }, statuses)
        assertEquals(401, post(ApiPaths.REFRESH, RefreshRequest(tokens.refreshToken)).statusCode())
    }

    private inline fun <reified T> post(path: String, body: T, accessToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body)))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun uniqueEmail() = "racer-${UUID.randomUUID()}@test.com"

    companion object {
        private const val THREADS = 10
        private const val PASSWORD = "Passw0rd!123"
        private const val DEVICE_ID = "phone"
    }
}
