package com.dongchyeon.chat.server.message

import com.dongchyeon.chat.server.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

// ADR 0038, 0040, 0041: ddl-auto가 만든 테이블이 결정한 스키마와 같은지 확인한다
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MessageSchemaTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `messages 인덱스는 room_id, id 순서의 복합 인덱스`() {
        val definition = jdbc.queryForObject(
            "select indexdef from pg_indexes where tablename = 'messages' and indexname = 'idx_messages_room_id_id'",
            String::class.java,
        )
        assertEquals("CREATE INDEX idx_messages_room_id_id ON public.messages USING btree (room_id, id)", definition)
    }

    @Test
    fun `컬럼의 null 허용 여부`() {
        assertEquals(
            mapOf(
                "id" to "NO",
                "room_id" to "NO",
                "sender_id" to "NO",
                "type" to "NO",
                "content" to "YES", // JOIN/LEAVE는 문구가 없다
                "created_at" to "NO",
            ),
            nullability("messages"),
        )
        assertEquals("YES", nullability("chat_rooms")["last_message_id"])
    }

    private fun nullability(table: String): Map<String, String> =
        jdbc.queryForList(
            "select column_name, is_nullable from information_schema.columns where table_name = ?",
            table,
        ).associate { it["column_name"] as String to it["is_nullable"] as String }
}
