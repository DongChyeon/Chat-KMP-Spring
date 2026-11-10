#!/usr/bin/env bash
# 로그아웃한 기기의 토큰이 로그아웃 뒤에 어떻게 동작하는지 시간 순서대로 기록한다 (ADR 0016).
#   1. 가입 → 로그인 → 로그아웃
#   2. 로그아웃 직후 그 기기의 Refresh Token으로 재발급을 시도한다
#   3. 그 기기의 Access Token으로 /me를 INTERVAL초마다 호출하며 응답을 기록한다
# 사용법: ./scripts/logout-window.sh [관찰 시간(초)] [호출 간격(초)]
# 15분을 기다리지 않으려면 서버를 짧은 TTL로 띄운다:
#   ./gradlew :server:bootRun --args='--jwt.access-token-ttl=1m'
set -euo pipefail

DURATION=${1:-90}
INTERVAL=${2:-10}
BASE=${BASE_URL:-http://localhost:8080}
EMAIL="logout-$(date +%s)@test.com"
DEVICE=phone
H='Content-Type: application/json'

json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

# 응답 코드와 에러 코드(code 필드)를 한 줄로 출력한다
call() {
  local out status body
  out=$(curl -s -w '\n%{http_code}' "$@")
  status=$(tail -n1 <<<"$out")
  body=$(sed '$d' <<<"$out")
  echo "$status $(json_field code <<<"$body")"
}

curl -s -o /dev/null -H "$H" -X POST "$BASE/api/auth/signup" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"nickname\":\"logout\"}"
TOKENS=$(curl -s -H "$H" -X POST "$BASE/api/auth/login" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"deviceId\":\"$DEVICE\"}")
ACCESS=$(json_field accessToken <<<"$TOKENS")
REFRESH=$(json_field refreshToken <<<"$TOKENS")

echo "로그인 직후 /me                : $(call -H "Authorization: Bearer $ACCESS" "$BASE/api/users/me")"
echo "로그아웃                       : $(call -H "$H" -H "Authorization: Bearer $ACCESS" -X POST "$BASE/api/auth/logout" -d "{\"deviceId\":\"$DEVICE\"}")"
START=$(date +%s)
echo "로그아웃 직후 재발급           : $(call -H "$H" -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$REFRESH\"}")"

echo
echo "로그아웃 후 경과(초)  /me 응답"
while :; do
  ELAPSED=$(( $(date +%s) - START ))
  printf '%10d  %s\n' "$ELAPSED" "$(call -H "Authorization: Bearer $ACCESS" "$BASE/api/users/me")"
  (( ELAPSED + INTERVAL > DURATION )) && break
  sleep "$INTERVAL"
done
