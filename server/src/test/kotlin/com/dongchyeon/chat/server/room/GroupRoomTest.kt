package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.server.runConcurrently
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.room.RoomResponse
import com.dongchyeon.chat.shared.room.RoomRules
import com.dongchyeon.chat.shared.room.RoomType
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 동시성 테스트가 있으므로 @Transactional(롤백)을 쓰지 않고 테스트마다 새 사용자를 만든다 (ADR 0018)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class GroupRoomTest(
    @LocalServerPort port: Int,
) {
    private val api = RoomTestClient(port)

    @Test
    fun `그룹 방을 만들면 만든 사람이 참여자다`() {
        val owner = api.newUser()

        val response = api.createGroup(owner, "스터디")
        assertEquals(201, response.statusCode(), response.body())
        val room = Json.decodeFromString<RoomResponse>(response.body())
        assertEquals(RoomType.GROUP, room.type)
        assertEquals("스터디", room.name)
        assertEquals(listOf(owner.id), room.members.map { it.userId })
    }

    @Test
    fun `방 이름이 비었거나 너무 길면 400`() {
        val owner = api.newUser()
        api.assertError(api.createGroup(owner, " "), 400, ErrorCode.INVALID_ROOM_NAME)
        api.assertError(api.createGroup(owner, "가".repeat(RoomRules.NAME_MAX_LENGTH + 1)), 400, ErrorCode.INVALID_ROOM_NAME)
    }

    @Test
    fun `참여하면 참여자 목록에 들어가고, 다시 참여하면 409`() {
        val owner = api.newUser()
        val guest = api.newUser()
        val roomId = Json.decodeFromString<RoomResponse>(api.createGroup(owner, "스터디").body()).id

        val joined = api.join(guest, roomId)
        assertEquals(200, joined.statusCode(), joined.body())
        assertEquals(setOf(owner.id, guest.id), Json.decodeFromString<RoomResponse>(joined.body()).members.map { it.userId }.toSet())

        api.assertError(api.join(guest, roomId), 409, ErrorCode.ALREADY_JOINED)
        api.assertError(api.join(owner, roomId), 409, ErrorCode.ALREADY_JOINED)
    }

    // ADR 0026: 나갔던 방에 다시 들어오면 기존 행의 left_at을 비우고 200
    @Test
    fun `나갔던 그룹 방에 다시 참여하면 200`() {
        val owner = api.newUser()
        val guest = api.newUser()
        val roomId = Json.decodeFromString<RoomResponse>(api.createGroup(owner, "스터디").body()).id
        api.join(guest, roomId)
        assertEquals(204, api.leave(guest, roomId).statusCode())

        val rejoined = api.join(guest, roomId)
        assertEquals(200, rejoined.statusCode(), rejoined.body())
    }

    // ADR 0026: 참여자 응답에는 left_at is null인 사람만 담는다
    @Test
    fun `나간 사람은 참여자 목록에 보이지 않는다`() {
        val owner = api.newUser()
        val guest = api.newUser()
        val third = api.newUser()
        val roomId = Json.decodeFromString<RoomResponse>(api.createGroup(owner, "스터디").body()).id
        api.join(guest, roomId)
        api.leave(guest, roomId)

        val joined = api.join(third, roomId)
        assertEquals(200, joined.statusCode(), joined.body())
        assertEquals(setOf(owner.id, third.id), Json.decodeFromString<RoomResponse>(joined.body()).members.map { it.userId }.toSet())
    }

    @Test
    fun `1대1 방에는 참여할 수 없고, 없는 방은 404`() {
        val a = api.newUser()
        val b = api.newUser()
        val outsider = api.newUser()
        val directId = Json.decodeFromString<RoomResponse>(api.createDirect(a, b.id).body()).id

        api.assertError(api.join(outsider, directId), 403, ErrorCode.DIRECT_ROOM_NOT_JOINABLE)
        api.assertError(api.join(outsider, Long.MAX_VALUE), 404, ErrorCode.NOT_FOUND)
    }

    @Test
    fun `같은 사용자가 동시에 여러 번 참여해도 참여는 한 번`() {
        val owner = api.newUser()
        val guest = api.newUser()
        val roomId = Json.decodeFromString<RoomResponse>(api.createGroup(owner, "스터디").body()).id

        val statuses = runConcurrently(THREADS) { api.join(guest, roomId).statusCode() }

        assertEquals(1, statuses.count { it == 200 }, statuses.toString())
        assertEquals(THREADS - 1, statuses.count { it == 409 }, statuses.toString())
    }

    // ADR 0027: 동시 재참여는 막지 않는다. 200이 하나라는 보장은 없고, 최종 상태(참여 중)만 보장한다
    @Test
    fun `나갔던 사용자가 동시에 여러 번 재참여하면 200이 여러 개일 수 있지만 결국 참여 중이다`() {
        val owner = api.newUser()
        val guest = api.newUser()
        val roomId = Json.decodeFromString<RoomResponse>(api.createGroup(owner, "스터디").body()).id
        api.join(guest, roomId)
        api.leave(guest, roomId)

        val statuses = runConcurrently(THREADS) { api.join(guest, roomId).statusCode() }

        assertTrue(statuses.all { it == 200 || it == 409 }, statuses.toString())
        assertTrue(statuses.any { it == 200 }, statuses.toString())
        api.assertError(api.join(guest, roomId), 409, ErrorCode.ALREADY_JOINED)
    }

    companion object {
        private const val THREADS = 10
    }
}
