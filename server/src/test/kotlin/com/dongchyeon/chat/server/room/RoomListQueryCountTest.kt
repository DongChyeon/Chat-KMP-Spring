package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.TestcontainersConfiguration
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals

// ADR 0029: 목록 요청 한 번에 나가는 SQL 수를 센다. N+1이 다시 생기면 이 테스트가 깨진다
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"],
)
@Import(TestcontainersConfiguration::class)
class RoomListQueryCountTest(
    @LocalServerPort port: Int,
    @Autowired entityManagerFactory: EntityManagerFactory,
) {
    private val api = RoomTestClient(port)
    private val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    @Test
    fun `1대1 방 3개와 그룹 방 2개가 있어도 목록 요청의 SQL은 2번`() {
        val me = api.newUser()
        repeat(3) { api.createDirect(me, api.newUser().id) }
        repeat(2) { api.createGroup(me, "그룹 $it") }

        statistics.clear()
        println("===== 목록 요청 시작 =====")
        val response = api.myRooms(me)
        println("===== 목록 요청 끝 =====")

        assertEquals(200, response.statusCode(), response.body())
        assertEquals(2, statistics.prepareStatementCount)
    }
}
