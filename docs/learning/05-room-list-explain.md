# 05. 내 채팅방 목록 쿼리는 인덱스를 쓰나 (EXPLAIN)

- 날짜: 2026-11-22
- 로드맵 단계: 2 (Issue #7 작업 목록: `EXPLAIN`으로 인덱스 사용 확인)
- 관련 결정: ADR 0023(방 id 내림차순), ADR 0024(커서), ADR 0025(`(user_id, room_id)` unique + `(room_id)` 인덱스)

## 예측 (오너 작성)

질문: 목록 쿼리를 `EXPLAIN`하면 `chat_room_members`를 `(user_id, room_id)` 인덱스로 읽을까, 테이블 전체를 훑을까(Seq Scan)? 행이 수십 개일 때와 10만 개일 때는?

> 수십 개뿐일 때는 Seq Scan으로 읽고, 10만 개 일 때에는 Index Scan으로 읽어

## 실행

Hibernate가 만든 목록 쿼리(첫 페이지: 커서 `Long.MAX_VALUE`, `size + 1 = 21`)를 로컬 PostgreSQL 18에서 `EXPLAIN`한다. 데이터는 트랜잭션 안에서 넣고 마지막에 `ROLLBACK`하므로 로컬 DB에 남지 않는다.

```bash
docker compose up -d --wait
./gradlew :server:bootRun   # 테이블이 없으면 한 번 띄워서 ddl-auto로 만든다
docker compose exec -T postgres psql -U chat -d chat < scripts/explain-room-list.sql
```

스크립트: `scripts/explain-room-list.sql`

- ① 참여 행 0개, 테이블을 막 만들어 통계가 없는 상태
- ② 참여 행 25개(사용자 5명, 방 10개)를 넣고 `ANALYZE`한 상태. 같은 방식으로 따로 실행했다
- ③ 참여 행 10만 개(사용자 1,000명, 방 1,000개, 사용자마다 100개 방)를 넣고 `ANALYZE`한 상태

## 관찰

**① 참여 행 0개, 통계 없음**
```
Limit
  -> Sort  (Sort Key: r.id DESC)
       -> Nested Loop
            -> Bitmap Heap Scan on chat_room_members m
                 Filter: (left_at IS NULL)
                 -> Bitmap Index Scan on ukeatdjwpl171huu28pvsan941c          -- (user_id, room_id)
                      Index Cond: ((user_id = 0) AND (room_id < ...))
            -> Index Scan using chat_rooms_pkey on chat_rooms r
```

**② 참여 행 25개, ANALYZE 후**
```
Limit
  -> Sort  (Sort Key: r.id DESC)
       -> Nested Loop
            -> Seq Scan on chat_rooms r                                         -- 방 10개 전부
            -> Bitmap Heap Scan on chat_room_members m   (loops=10)
                 Filter: ((left_at IS NULL) AND (user_id = 1094))
                 Rows Removed by Filter: 1
                 -> Bitmap Index Scan on idx_chat_room_members_room_id          -- (room_id)
Execution Time: 0.072 ms
```

**③ 참여 행 10만 개, ANALYZE 후**
```
Limit  (actual rows=21)
  -> Merge Join  (Merge Cond: (m.room_id = r.id))
       -> Index Scan Backward using ukeatdjwpl171huu28pvsan941c on chat_room_members m   (actual rows=21)
            Index Cond: ((user_id = 94) AND (room_id < ...))
            Filter: (left_at IS NULL)
            Buffers: shared hit=7
       -> Index Scan Backward using chat_rooms_pkey on chat_rooms r
Execution Time: 0.060 ms
```

- ③에서 `chat_room_members`는 `(user_id, room_id)` 인덱스를 **거꾸로(Backward)** 읽었고, 10만 행 중 21행만 읽고 멈췄다. `Sort` 노드가 없다.
- ①과 ②에는 `Sort` 노드가 있다.
- ②에서 Seq Scan은 `chat_room_members`가 아니라 `chat_rooms`에서 나왔다. `chat_room_members`는 `(room_id)` 인덱스로 방마다 한 번씩, 10번 찾아 들어갔다.
- 세 경우 모두 `left_at IS NULL`은 `Index Cond`가 아니라 `Filter`에 있었다.

## 왜

10만 개일 때 Index Scan을 쓴다는 예측은 맞았다. 수십 개일 때는 "Seq Scan으로 읽는다"보다 "옵티마이저가 계획을 통째로 바꾼다"에 가까웠다.

**1. 옵티마이저는 통계와 데이터 양으로 계획을 고른다.**
PostgreSQL은 "인덱스로 찾아가는 비용"과 "그냥 다 읽는 비용"을 통계로 추정해서 싼 쪽을 고른다. 행이 25개뿐인 ②에서는 방 10개를 전부 읽고 방마다 참여자를 찾는 쪽이 싸다고 봤다. 그래서 어느 테이블을 먼저 읽을지, 어느 인덱스를 쓸지까지 ③과 달라졌다. ①처럼 `ANALYZE` 전에는 통계가 없어 기본 추정값으로 계획을 세운다. 그래서 작은 데이터로 `EXPLAIN`한 결과는 운영에서의 계획을 말해 주지 않는다. ADR 0025 결과 칸에 "데이터를 충분히 넣고 확인한다"고 적은 이유가 이것이다.

**2. ③에 `Sort`가 없는 이유: 인덱스가 이미 정렬돼 있다.**
B-tree 인덱스 `(user_id, room_id)`는 `user_id`로 먼저 정렬하고, 같은 `user_id` 안에서는 `room_id`로 정렬해 둔다. 그래서 `user_id = 94`인 구간만 놓고 보면 `room_id` 오름차순으로 줄 서 있다. 이 구간을 뒤에서부터 읽으면(Backward) 그대로 `room_id desc` 순서가 되므로, 따로 정렬할 필요가 없다. 게다가 `Limit`이 21행을 채우는 순간 멈출 수 있어서, 참여한 방이 100개든 1만 개든 21행만 읽는다. 컬럼 순서가 `(room_id, user_id)`였다면 한 사용자의 행이 인덱스 곳곳에 흩어져 이렇게 읽을 수 없다(ADR 0025의 B안 단점).

**3. `left_at IS NULL`은 인덱스에 없어서 Filter로 거른다.**
인덱스에 `left_at` 컬럼이 없으므로, 인덱스로 찾은 행을 테이블에서 읽은 뒤 조건을 확인한다. 지금은 나간 방이 드물어서 버리는 행이 거의 없다. 나간 방이 많은 사용자라면 21행을 채우기 위해 더 많이 읽게 된다.

**고칠 것인가**
지금은 고치지 않는다. ③이 0.06ms에 21행만 읽고 끝나므로 ADR 0025 설계가 의도대로 동작한다. 나간 방이 쌓여 `Filter`에서 버리는 행이 많아지면, `left_at IS NULL` 조건을 건 부분 인덱스(partial index)를 결정 게이트에 올린다. 3단계에서 정렬을 최근 메시지순으로 바꾸면(ADR 0023) 인덱스를 다시 설계하고 이 실험을 다시 한다.
