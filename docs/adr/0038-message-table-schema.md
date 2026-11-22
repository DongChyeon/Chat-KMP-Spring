# 0038. 메시지 테이블 스키마와 인덱스

- 상태: 채택
- 날짜: 2026-11-22
- 로드맵 단계: 3
- Issue: #9

## 맥락

메시지를 저장할 테이블이 필요하다. 채팅방 화면은 "이 방의 최신 N개"를 커서로 이어서 조회한다(`where room_id = ? and id < :cursor order by id desc`).

정렬과 커서를 복합 인덱스 하나로 받치는 개념은 2단계(ADR 0025, 학습 기록 05)에서 다뤘다. 같은 개념이 두 번째로 나오므로 🟢로 내려, 선택지 대신 제안하고 오너가 확인했다.

## 결정

```sql
messages
  id          bigint generated always as identity  primary key   -- ADR 0037
  room_id     bigint        not null  references chat_rooms(id)
  sender_id   bigint        not null  references users(id)
  content     varchar(1000) not null
  created_at  timestamptz   not null

index idx_messages_room_id_id (room_id, id)
```

- `(room_id, id)`: 방의 최신 N개를 정렬 없이 역방향 인덱스 스캔으로 읽는다. 방 목록의 `(user_id, room_id)`와 같은 모양이다. PK만 있으면 다른 방 메시지까지 훑고, `room_id` 단일 인덱스만 있으면 방 안의 메시지를 모두 읽은 뒤 정렬한다.
- `sender_id`는 참여 행이 아니라 `users`를 참조한다. 나간 사람이 보낸 메시지도 남아야 하기 때문이다.
- 내용 길이 상한(1000자)은 `shared`의 상수로 두어, 서버 검증(에러 프레임, ADR 0034)과 앱 입력 제한이 같은 값을 쓴다.

## 이유 (오너 작성)

> 이대로 진행할게

## 나중에 알게 된 것
