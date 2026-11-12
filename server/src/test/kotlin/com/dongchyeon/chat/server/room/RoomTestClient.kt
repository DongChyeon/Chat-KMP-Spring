package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.auth.UserResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.error.ErrorResponse
import com.dongchyeon.chat.shared.room.CreateDirectRoomRequest
import com.dongchyeon.chat.shared.room.CreateGroupRoomRequest
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals

data class TestUser(val id: Long, val accessToken: String)

// 채팅방 테스트가 함께 쓰는 HTTP 호출. 실제 서버(RANDOM_PORT)에 요청한다
class RoomTestClient(private val port: Int) {
    private val client = HttpClient.newHttpClient()

    fun newUser(): TestUser {
        val email = "room-${UUID.randomUUID()}@test.com"
        val user = Json.decodeFromString<UserResponse>(post(ApiPaths.SIGNUP, SignupRequest(email, PASSWORD, "tester")).body())
        val tokens = Json.decodeFromString<TokenResponse>(post(ApiPaths.LOGIN, LoginRequest(email, PASSWORD, "phone")).body())
        return TestUser(user.id, tokens.accessToken)
    }

    fun createDirect(user: TestUser, targetUserId: Long) =
        post(ApiPaths.DIRECT_ROOMS, CreateDirectRoomRequest(targetUserId), user.accessToken)

    fun createGroup(user: TestUser, name: String) =
        post(ApiPaths.GROUP_ROOMS, CreateGroupRoomRequest(name), user.accessToken)

    fun join(user: TestUser, roomId: Long) = send(ApiPaths.roomJoin(roomId), "", user.accessToken)

    fun assertError(response: HttpResponse<String>, status: Int, code: ErrorCode) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, Json.decodeFromString<ErrorResponse>(response.body()).code)
    }

    private inline fun <reified T> post(path: String, body: T, accessToken: String? = null) =
        send(path, Json.encodeToString(body), accessToken)

    private fun send(path: String, body: String, accessToken: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val PASSWORD = "Passw0rd!123"
    }
}
