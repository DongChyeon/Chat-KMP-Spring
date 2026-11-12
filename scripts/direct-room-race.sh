#!/usr/bin/env bash
# 1:1 방 생성(ChatRoomService.createDirect)에서 잠금이 어느 시점에 걸리는지 DB에서 확인한다 (ADR 0022).
#   1. 다른 트랜잭션이 두 사용자 행을 잠그고 있을 때 요청이 어디까지 진행한 뒤 기다리는가
#   2. 같은 두 사람에게 요청 N개를 동시에 보내면 DIRECT 방이 몇 개 생기는가
#   3. 없는 사용자와 방을 만들면 응답 코드는 무엇이고, DB에는 무엇이 남는가
# 사용법: 서버를 띄운 뒤 ./scripts/direct-room-race.sh [동시 요청 수]
set -euo pipefail

N=${1:-10}
BASE=${BASE_URL:-http://localhost:8080}
DB_CONTAINER=${DB_CONTAINER:-chat-kmp-spring-postgres-1}
H='Content-Type: application/json'
RUN=$(date +%s)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

sql() { docker exec "$DB_CONTAINER" psql -U chat -d chat -tA -c "$1"; }
json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

# 가입하고 로그인해서 "id 토큰"을 출력한다
new_user() {
  local email="race-$RUN-$1@test.com" token id
  curl -s -o /dev/null -H "$H" -X POST "$BASE/api/auth/signup" \
    -d "{\"email\":\"$email\",\"password\":\"password123\",\"nickname\":\"$1\"}"
  token=$(curl -s -H "$H" -X POST "$BASE/api/auth/login" \
    -d "{\"email\":\"$email\",\"password\":\"password123\",\"deviceId\":\"race\"}" | json_field accessToken)
  id=$(sql "select id from users where email = '$email'")
  echo "$id $token"
}

# 응답 코드만 출력한다
create_direct() { # 토큰 상대id
  curl -s -o /dev/null -w '%{http_code}\n' -H "$H" -H "Authorization: Bearer $1" \
    -X POST "$BASE/api/rooms/direct" -d "{\"targetUserId\":$2}"
}

seq_value() { sql "select coalesce(pg_sequence_last_value('chat_rooms_id_seq'), 0)"; }
room_count() { sql "select count(*) from chat_rooms"; }
shared_direct_rooms() { # id1 id2
  sql "select count(*) from chat_room_members m1
       join chat_room_members m2 on m2.room_id = m1.room_id
       join chat_rooms r on r.id = m1.room_id
       where m1.user_id = $1 and m2.user_id = $2 and r.type = 'DIRECT'"
}

echo "===== 1. 잠금이 걸리는 시점 ====="
read -r A_ID A_TOKEN <<<"$(new_user a)"
read -r B_ID B_TOKEN <<<"$(new_user b)"
echo "사용자 A=$A_ID, B=$B_ID"

# 'A의 요청'을 흉내 낸다: 다른 트랜잭션이 두 사용자 행을 6초 동안 잠그고 있다
sql "begin; select id from users where id in ($A_ID, $B_ID) order by id for update; select pg_sleep(6); commit;" >/dev/null &
LOCK_PID=$!
sleep 1

SEQ_BEFORE=$(seq_value); COUNT_BEFORE=$(room_count)
echo "B의 요청 전   : chat_rooms 시퀀스=$SEQ_BEFORE, chat_rooms 행 수=$COUNT_BEFORE"

# 'B의 요청'을 보낸다
create_direct "$B_TOKEN" "$A_ID" >"$TMP/b_status" &
REQ_PID=$!
sleep 2

echo "B가 기다리는 중:"
echo "  chat_rooms 시퀀스=$(seq_value) (INSERT를 실행하면 커밋 전에도 올라간다)"
echo "  chat_rooms 행 수=$(room_count) (커밋 전이라 다른 트랜잭션에서는 안 보인다)"
echo "  잠금을 기다리는 쿼리:"
sql "select '    ' || left(regexp_replace(query, '\s+', ' ', 'g'), 160)
     from pg_stat_activity where datname = 'chat' and wait_event_type = 'Lock'"

wait "$LOCK_PID" "$REQ_PID"
echo "잠금이 풀린 뒤 B의 응답: $(command cat "$TMP/b_status")"

echo
echo "===== 2. 동시 요청 $N개 (C→D, D→C 번갈아) ====="
read -r C_ID C_TOKEN <<<"$(new_user c)"
read -r D_ID D_TOKEN <<<"$(new_user d)"
for i in $(seq 1 "$N"); do
  if (( i % 2 )); then create_direct "$C_TOKEN" "$D_ID" >"$TMP/r$i" &
  else create_direct "$D_TOKEN" "$C_ID" >"$TMP/r$i" &
  fi
done
wait
echo "응답 코드: $(command cat "$TMP"/r* | sort | uniq -c | xargs)"
echo "C와 D가 함께 속한 DIRECT 방: $(shared_direct_rooms "$C_ID" "$D_ID")개"

echo
echo "===== 3. 없는 사용자와 방 만들기 ====="
SEQ_BEFORE=$(seq_value); COUNT_BEFORE=$(room_count)
STATUS=$(create_direct "$A_TOKEN" 999999999)
echo "응답 코드: $STATUS"
echo "chat_rooms 시퀀스: $SEQ_BEFORE → $(seq_value)"
echo "chat_rooms 행 수 : $COUNT_BEFORE → $(room_count)"
