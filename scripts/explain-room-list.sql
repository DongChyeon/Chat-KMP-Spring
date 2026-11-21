-- 내 채팅방 목록 쿼리의 실행 계획 확인 (Issue #7, ADR 0025)
--
-- 실행:
--   docker compose up -d --wait
--   ./gradlew :server:bootRun   # 테이블이 없으면 한 번 띄워서 ddl-auto로 만든다(만든 뒤 종료해도 된다)
--   docker compose exec -T postgres psql -U chat -d chat < scripts/explain-room-list.sql
--
-- 전체가 하나의 트랜잭션이고 마지막에 ROLLBACK 하므로, 넣은 데이터는 로컬 DB에 남지 않는다.
-- 쿼리는 Hibernate가 만든 SQL과 같은 모양이다(첫 페이지: 커서 = Long.MAX_VALUE, size 20 + 1).

\pset pager off
BEGIN;

\echo
\echo '========== 1. 지금 데이터 그대로 =========='
SELECT count(*) AS member_rows FROM chat_room_members;
SELECT coalesce(min(user_id), 0) AS uid FROM chat_room_members \gset

EXPLAIN
SELECT r.id, r.created_at, r.name, r.type
FROM chat_room_members m JOIN chat_rooms r ON r.id = m.room_id
WHERE m.user_id = :uid AND m.room_id < 9223372036854775807 AND m.left_at IS NULL
ORDER BY m.room_id DESC
FETCH FIRST 21 ROWS ONLY;

\echo
\echo '========== 2. 사용자 1,000명 x 방 1,000개, 참여 행 10만 개를 넣은 뒤 =========='
INSERT INTO users (created_at, email, nickname, password)
SELECT now(), 'explain-' || g || '@test.com', 'explain-' || g, 'x'
FROM generate_series(1, 1000) g;

INSERT INTO chat_rooms (created_at, name, type)
SELECT now(), 'explain-room-' || g, 'GROUP'
FROM generate_series(1, 1000) g;

-- 사용자마다 방 100개에 참여한다 (1,000 x 1,000 / 10 = 100,000행)
WITH u AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM users WHERE email LIKE 'explain-%'),
     r AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM chat_rooms WHERE name LIKE 'explain-room-%')
INSERT INTO chat_room_members (joined_at, room_id, user_id)
SELECT now(), r.id, u.id FROM u JOIN r ON (u.rn + r.rn) % 10 = 0;

-- 옵티마이저가 쓰는 통계를 새로 모은다. 안 하면 "행이 적은 테이블"로 알고 계획을 세운다
ANALYZE users;
ANALYZE chat_rooms;
ANALYZE chat_room_members;

SELECT count(*) AS member_rows FROM chat_room_members;
SELECT min(id) AS uid FROM users WHERE email LIKE 'explain-%' \gset

EXPLAIN ANALYZE
SELECT r.id, r.created_at, r.name, r.type
FROM chat_room_members m JOIN chat_rooms r ON r.id = m.room_id
WHERE m.user_id = :uid AND m.room_id < 9223372036854775807 AND m.left_at IS NULL
ORDER BY m.room_id DESC
FETCH FIRST 21 ROWS ONLY;

\echo
\echo '참고: chat_room_members의 인덱스'
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'chat_room_members';

ROLLBACK;
