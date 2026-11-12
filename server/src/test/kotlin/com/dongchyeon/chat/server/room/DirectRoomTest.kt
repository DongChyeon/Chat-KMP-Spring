package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.server.runConcurrently
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.room.RoomResponse
import com.dongchyeon.chat.shared.room.RoomType
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals

// 동시성 테스트가 있으므로 @Transactional(롤백)을 쓰지 않고 테스트마다 새 사용자를 만든다 (ADR 0018)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class DirectRoomTest(
    @LocalServerPort port: Int,
) {
    private val api = RoomTestClient(port)

    @Test
    fun `처음 만들면 201, 같은 상대와 다시 만들면 200과 같은 방`() {
        val a = api.newUser()
        val b = api.newUser()

        val first = api.createDirect(a, b.id)
        assertEquals(201, first.statusCode(), first.body())
        val room = Json.decodeFromString<RoomResponse>(first.body())
        assertEquals(RoomType.DIRECT, room.type)
        assertEquals(setOf(a.id, b.id), room.members.map { it.userId }.toSet())

        val again = api.createDirect(a, b.id)
        assertEquals(200, again.statusCode(), again.body())
        assertEquals(room.id, Json.decodeFromString<RoomResponse>(again.body()).id)

        // 반대 방향으로 만들어도 같은 방
        val reverse = api.createDirect(b, a.id)
        assertEquals(200, reverse.statusCode(), reverse.body())
        assertEquals(room.id, Json.decodeFromString<RoomResponse>(reverse.body()).id)
    }

    @Test
    fun `자기 자신과는 400, 없는 사용자와는 404`() {
        val a = api.newUser()
        api.assertError(api.createDirect(a, a.id), 400, ErrorCode.INVALID_REQUEST)
        api.assertError(api.createDirect(a, Long.MAX_VALUE), 404, ErrorCode.NOT_FOUND)
    }

    @Test
    fun `두 사람이 동시에 서로에게 만들어도 방은 하나`() {
        val a = api.newUser()
        val b = api.newUser()

        // 짝수 번째는 a → b, 홀수 번째는 b → a. 잠금 순서가 어긋나면 데드락이 난다 (ADR 0022)
        val responses = runConcurrently(THREADS) { i ->
            if (i % 2 == 0) api.createDirect(a, b.id) else api.createDirect(b, a.id)
        }

        val statuses = responses.map { it.statusCode() }
        assertEquals(1, statuses.count { it == 201 }, statuses.toString())
        assertEquals(THREADS - 1, statuses.count { it == 200 }, statuses.toString())
        assertEquals(1, responses.map { Json.decodeFromString<RoomResponse>(it.body()).id }.toSet().size)
    }

    companion object {
        private const val THREADS = 10
    }
}
