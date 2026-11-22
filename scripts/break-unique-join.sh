#!/usr/bin/env bash
# 부숴보기: (user_id, room_id) unique 제약을 지우면 중복 참여를 코드만으로 막을 수 있을까 (로드맵 2단계, ADR 0020·0021)
#
# 실행 (로컬 DB 전용):
#   docker compose up -d --wait
#   ./gradlew :server:bootRun          # 서버를 먼저 띄운다. 띄운 뒤에 제약을 지워야 ddl-auto가 다시 만들지 않는다
#   ./scripts/break-unique-join.sh [동시 요청 수=10]
#
# 끝나면(실패해도) 중복 행을 지우고 제약을 같은 이름으로 다시 만든다.
set -euo pipefail

N=${1:-10}
BASE=${BASE_URL:-http://localhost:8080}
DB_CONTAINER=${DB_CONTAINER:-chat-postgres-1}
H='Content-Type: application/json'
RUN=$(date +%s)
TMP=$(mktemp -d)

sql() { docker exec "$DB_CONTAINER" psql -U chat -d chat -tA -c "$1"; }
json_string() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }
json_number() { sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p"; }

CONSTRAINT=$(sql "select conname from pg_constraint
                  where conrelid = 'chat_room_members'::regclass and contype = 'u'")
if [[ -z "$CONSTRAINT" ]]; then
  echo "chat_room_members에 unique 제약이 없습니다. 이전 실행이 복구되지 않았는지 확인하세요." >&2
  exit 1
fi

restore() {
  echo
  echo "===== 복구 ====="
  local removed
  removed=$(sql "with d as (
                   delete from chat_room_members a using chat_room_members b
                   where a.user_id = b.user_id and a.room_id = b.room_id and a.id > b.id
                   returning a.id)
                 select count(*) from d")
  echo "중복 행 삭제: ${removed}개"
  sql "alter table chat_room_members add constraint $CONSTRAINT unique (user_id, room_id)" >/dev/null
  echo "제약 복구: $CONSTRAINT (user_id, room_id)"
  rm -rf "$TMP"
}
trap restore EXIT

new_user() { # 닉네임 → "id 토큰"
  local email="break-$RUN-$1@test.com" token id
  curl -s -o /dev/null -H "$H" -X POST "$BASE/api/auth/signup" \
    -d "{\"email\":\"$email\",\"password\":\"password123\",\"nickname\":\"$1\"}"
  token=$(curl -s -H "$H" -X POST "$BASE/api/auth/login" \
    -d "{\"email\":\"$email\",\"password\":\"password123\",\"deviceId\":\"break\"}" | json_string accessToken)
  id=$(sql "select id from users where email = '$email'")
  echo "$id $token"
}

call() { # 메서드 경로 토큰 → "상태코드 본문"
  curl -s -w ' %{http_code}' -H "$H" -H "Authorization: Bearer $3" -X "$1" "$BASE$2" \
    | sed -E 's/^(.*) ([0-9]{3})$/\2 \1/'
}

member_rows() { # 사용자id 방id
  sql "select count(*) || '개 (left_at is null: ' || count(*) filter (where left_at is null) || '개)'
       from chat_room_members where user_id = $1 and room_id = $2"
}

echo "===== 0. unique 제약 지우기 ====="
sql "alter table chat_room_members drop constraint $CONSTRAINT" >/dev/null
echo "삭제한 제약: $CONSTRAINT"

read -r OWNER_ID OWNER_TOKEN <<<"$(new_user owner)"
read -r GUEST_ID GUEST_TOKEN <<<"$(new_user guest)"
ROOM_ID=$(curl -s -H "$H" -H "Authorization: Bearer $OWNER_TOKEN" -X POST "$BASE/api/rooms/group" \
  -d '{"name":"부숴보기"}' | json_number id)
echo "owner=$OWNER_ID, guest=$GUEST_ID, 그룹 방=$ROOM_ID"

echo
echo "===== 1. guest가 같은 방에 참여 요청 ${N}개를 동시에 ====="
for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $GUEST_TOKEN" \
    -X POST "$BASE/api/rooms/$ROOM_ID/join" >"$TMP/r$i" &
done
wait
echo "응답 코드: $(command cat "$TMP"/r* | sort | uniq -c | xargs)"
echo "guest의 참여 행: $(member_rows "$GUEST_ID" "$ROOM_ID")"

echo
echo "===== 2. 중복 행이 생긴 뒤 guest가 하는 일 ====="
echo "내 방 목록 : $(call GET /api/rooms "$GUEST_TOKEN")"
echo "나가기     : $(call POST "/api/rooms/$ROOM_ID/leave" "$GUEST_TOKEN")"
echo "  → guest의 참여 행: $(member_rows "$GUEST_ID" "$ROOM_ID")"
echo "다시 참여  : $(call POST "/api/rooms/$ROOM_ID/join" "$GUEST_TOKEN")"
