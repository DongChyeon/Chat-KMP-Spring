# 04. 내 채팅방 목록 요청 하나에 SQL은 몇 번 나가나

- 날짜: 2026-11-22
- 로드맵 단계: 2 (Issue #7 작업 목록: SQL 로그로 N+1 확인)
- 관련 결정: ADR 0028(참여자 fetch join), ADR 0029(목록은 요약, 1:1 상대 닉네임은 `IN` 한 번)

## 예측 (오너 작성)

질문: 1:1 방 3개와 그룹 방 2개에 참여한 사용자가 `GET /api/rooms` 첫 페이지를 요청하면 SQL이 모두 몇 번 나갈까?

> JWT 인증 필터를 통한 DB 조회 1번
> select m.room은 내부적으로 2번
> 각 룸마다 상대의 닉네임을 가져올 때 3번
>
> 6번 호출

## 실행

Hibernate 통계(`generate_statistics=true`)를 켜고, 목록 요청 직전에 통계를 비운 뒤 요청 하나 동안 실행된 SQL 수를 센다. 요청 앞뒤에 표시를 찍어 그 사이의 SQL 로그도 뽑는다.

```bash
./gradlew :server:test --tests '*RoomListQueryCountTest*'
```

테스트: `server/src/test/kotlin/com/dongchyeon/chat/server/room/RoomListQueryCountTest.kt`

## 관찰

`prepareStatementCount = 2`. 요청 하나 동안 나간 SQL은 다음 두 개뿐이었다.

```sql
-- 1. 내 방 한 페이지 (size + 1 = 21개까지)
select r1_0.id, r1_0.created_at, r1_0.name, r1_0.type
from chat_room_members crm1_0 join chat_rooms r1_0 on r1_0.id=crm1_0.room_id
where crm1_0.user_id=? and crm1_0.room_id<? and crm1_0.left_at is null
order by crm1_0.room_id desc fetch first ? rows only

-- 2. 1:1 방 3개의 상대 닉네임
select crm1_0.room_id, u1_0.nickname
from chat_room_members crm1_0 join users u1_0 on u1_0.id=crm1_0.user_id
where crm1_0.room_id in (?, ?, ?) and u1_0.id<>?
```

- 인증 필터 단계에는 SQL이 없었다.
- `chat_room_members`와 `chat_rooms`는 쿼리 하나 안에서 JOIN으로 함께 읽었다.
- 상대 닉네임은 방마다가 아니라 `in (?, ?, ?)` 쿼리 한 번으로 가져왔다.

## 왜

예측은 6번(필터 1 + 조인 2 + 방마다 3), 실제는 2번(필터 0 + 조인 1 + `IN` 1)이었다.

**1. 인증 필터는 DB를 보지 않는다.**
Access Token 검증은 `JwtProvider`로 서명과 만료 시간만 확인하고, 사용자 id는 토큰 안에 들어 있다(ADR 0005, 무상태). 그래서 요청마다 사용자를 조회하지 않는다.

**2. `select m.room`은 조인 한 번이다.**
`@ManyToOne` 경로(`m.room`)를 select하면 Hibernate가 `join chat_rooms`를 만들어 방 컬럼까지 한 쿼리에서 가져온다. 참여 행과 방을 따로 읽지 않는다.

**3. `IN`은 범위가 아니라 값 목록이다.**
`in (?, ?, ?)`의 물음표에는 이번 페이지에 있는 1:1 방 id가 하나씩 들어간다(예: `in (12, 15, 19)`). 범위는 `BETWEEN`이나 `>=`, `<=`로 고른다. 방이 몇 개든 물음표만 늘고 쿼리는 한 번이다. "방마다 1번씩"은 ADR 0029의 A안(방마다 조회)이 했을 동작이고, C안을 고르면서 `findDirectPartners`에 1:1 방 id를 모아 한 번에 넘기도록 했다.

**고칠 것인가**
고칠 것은 없다. 대신 `RoomListQueryCountTest`가 "목록 요청의 SQL은 2번"을 검사하므로, 나중에 목록에 방마다 조회하는 코드가 들어와 N+1이 다시 생기면 테스트가 깨진다.
