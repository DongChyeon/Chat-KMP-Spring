package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.server.runConcurrently
import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.auth.UserResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.error.ErrorResponse
import com.dongchyeon.chat.shared.room.CreateDirectRoomRequest
import com.dongchyeon.chat.shared.room.RoomResponse
import com.dongchyeon.chat.shared.room.RoomType
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

// 동시성 테스트가 있으므로 @Transactional(롤백)을 쓰지 않고 테스트마다 새 사용자를 만든다 (ADR 0018)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class DirectRoomTest(
    @LocalServerPort private val port: Int,
) {
    private val client = HttpClient.newHttpClient()

    @Test
    fun `처음 만들면 201, 같은 상대와 다시 만들면 200과 같은 방`() {
        val a = newUser()
        val b = newUser()

        val first = createDirect(a, b.id)
        assertEquals(201, first.statusCode(), first.body())
        val room = Json.decodeFromString<RoomResponse>(first.body())
        assertEquals(RoomType.DIRECT, room.type)
        assertEquals(setOf(a.id, b.id), room.members.map { it.userId }.toSet())

        val again = createDirect(a, b.id)
        assertEquals(200, again.statusCode(), again.body())
        assertEquals(room.id, Json.decodeFromString<RoomResponse>(again.body()).id)

        // 반대 방향으로 만들어도 같은 방
        val reverse = createDirect(b, a.id)
        assertEquals(200, reverse.statusCode(), reverse.body())
        assertEquals(room.id, Json.decodeFromString<RoomResponse>(reverse.body()).id)
    }

    @Test
    fun `자기 자신과는 400, 없는 사용자와는 404`() {
        val a = newUser()
        assertError(createDirect(a, a.id), 400, ErrorCode.INVALID_REQUEST)
        assertError(createDirect(a, Long.MAX_VALUE), 404, ErrorCode.NOT_FOUND)
    }

    @Test
    fun `두 사람이 동시에 서로에게 만들어도 방은 하나`() {
        val a = newUser()
        val b = newUser()

        // 짝수 번째는 a → b, 홀수 번째는 b → a. 잠금 순서가 어긋나면 데드락이 난다 (ADR 0022)
        val responses = runConcurrently(THREADS) { i ->
            if (i % 2 == 0) createDirect(a, b.id) else createDirect(b, a.id)
        }

        val statuses = responses.map { it.statusCode() }
        assertEquals(1, statuses.count { it == 201 }, statuses.toString())
        assertEquals(THREADS - 1, statuses.count { it == 200 }, statuses.toString())
        assertEquals(1, responses.map { Json.decodeFromString<RoomResponse>(it.body()).id }.toSet().size)
    }

    private data class TestUser(val id: Long, val accessToken: String)

    private fun newUser(): TestUser {
        val email = "room-${UUID.randomUUID()}@test.com"
        val user = Json.decodeFromString<UserResponse>(post(ApiPaths.SIGNUP, SignupRequest(email, PASSWORD, "tester")).body())
        val tokens = Json.decodeFromString<TokenResponse>(post(ApiPaths.LOGIN, LoginRequest(email, PASSWORD, "phone")).body())
        return TestUser(user.id, tokens.accessToken)
    }

    private fun createDirect(user: TestUser, targetUserId: Long) =
        post(ApiPaths.DIRECT_ROOMS, CreateDirectRoomRequest(targetUserId), user.accessToken)

    private fun assertError(response: HttpResponse<String>, status: Int, code: ErrorCode) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, Json.decodeFromString<ErrorResponse>(response.body()).code)
    }

    private inline fun <reified T> post(path: String, body: T, accessToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body)))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val THREADS = 10
        private const val PASSWORD = "Passw0rd!123"
    }
}
