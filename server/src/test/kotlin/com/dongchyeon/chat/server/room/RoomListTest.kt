package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.shared.CursorPage
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.room.RoomResponse
import com.dongchyeon.chat.shared.room.RoomRules
import com.dongchyeon.chat.shared.room.RoomSummaryResponse
import com.dongchyeon.chat.shared.room.RoomType
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 내 채팅방 목록 (ADR 0023, 0024, 0029). 테스트마다 새 사용자를 만들어 다른 테스트의 방이 섞이지 않는다
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class RoomListTest(
    @LocalServerPort port: Int,
) {
    private val api = RoomTestClient(port)

    @Test
    fun `내 방만 최신순으로 보이고 나간 방은 빠진다`() {
        val me = api.newUser()
        val other = api.newUser()
        val first = createGroup(me, "첫 방")
        val second = createGroup(me, "둘째 방")
        createGroup(other, "남의 방")
        val left = createGroup(me, "나간 방")
        api.leave(me, left)

        val page = page(api.myRooms(me))

        assertEquals(listOf(second, first), page.items.map { it.id })
        assertEquals(listOf("둘째 방", "첫 방"), page.items.map { it.title })
        assertNull(page.nextCursor)
    }

    @Test
    fun `커서로 다음 페이지를 이어서 받고 마지막 페이지의 커서는 null`() {
        val me = api.newUser()
        val ids = (1..5).map { createGroup(me, "방 $it") } // 오름차순으로 생성

        val first = page(api.myRooms(me, size = 2))
        assertEquals(ids.reversed().subList(0, 2), first.items.map { it.id })
        val firstCursor = assertNotNull(first.nextCursor)

        val second = page(api.myRooms(me, cursor = firstCursor, size = 2))
        assertEquals(ids.reversed().subList(2, 4), second.items.map { it.id })
        val secondCursor = assertNotNull(second.nextCursor)

        val last = page(api.myRooms(me, cursor = secondCursor, size = 2))
        assertEquals(ids.reversed().subList(4, 5), last.items.map { it.id })
        assertNull(last.nextCursor)
    }

    @Test
    fun `방 개수가 size와 같으면 다음 페이지는 없다`() {
        val me = api.newUser()
        repeat(2) { createGroup(me, "방 $it") }

        val page = page(api.myRooms(me, size = 2))

        assertEquals(2, page.items.size)
        assertNull(page.nextCursor)
    }

    @Test
    fun `1대1 방의 제목은 상대 닉네임이고, 상대가 나가도 그대로다`() {
        val me = api.newUser("나")
        val partner = api.newUser("상대")
        val roomId = Json.decodeFromString<RoomResponse>(api.createDirect(me, partner.id).body()).id

        val before = page(api.myRooms(me)).items.single()
        assertEquals(RoomSummaryResponse(roomId, RoomType.DIRECT, "상대"), before)

        api.leave(partner, roomId)
        assertEquals("상대", page(api.myRooms(me)).items.single().title)
        assertEquals(emptyList(), page(api.myRooms(partner)).items)
    }

    @Test
    fun `잘못된 커서와 size는 400`() {
        val me = api.newUser()
        api.assertError(api.myRooms(me, cursor = "not-a-cursor"), 400, ErrorCode.INVALID_CURSOR)
        api.assertError(api.myRooms(me, size = 0), 400, ErrorCode.INVALID_REQUEST)
        api.assertError(api.myRooms(me, size = RoomRules.PAGE_SIZE_MAX + 1), 400, ErrorCode.INVALID_REQUEST)
    }

    private fun createGroup(user: TestUser, name: String): Long =
        Json.decodeFromString<RoomResponse>(api.createGroup(user, name).body()).id

    private fun page(response: HttpResponse<String>): CursorPage<RoomSummaryResponse> {
        assertEquals(200, response.statusCode(), response.body())
        return Json.decodeFromString(response.body())
    }
}
