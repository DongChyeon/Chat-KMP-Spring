#!/usr/bin/env bash
# 로그인 실패 응답 시간을 두 경우로 나눠 잰다.
#   A: 가입되지 않은 이메일
#   B: 가입된 이메일 + 틀린 비밀번호
# 두 평균이 크게 다르면, 응답 시간만으로 계정 존재 여부를 알 수 있다는 뜻이다 (타이밍 공격).
# 사용법: ./scripts/login-timing.sh [반복 횟수]
set -euo pipefail

N=${1:-20}
BASE=${BASE_URL:-http://localhost:8080}
EMAIL="timing-$(date +%s)@test.com"
H='Content-Type: application/json'

curl -s -o /dev/null -H "$H" -X POST "$BASE/api/auth/signup" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"nickname\":\"timing\"}"

measure() {
  local email=$1 total=0 t
  for _ in $(seq 1 "$N"); do
    t=$(curl -s -o /dev/null -w '%{time_total}' -H "$H" -X POST "$BASE/api/auth/login" \
      -d "{\"email\":\"$email\",\"password\":\"wrong-password\",\"deviceId\":\"timing-device\"}")
    total=$(echo "$total + $t" | bc -l)
  done
  echo "$total * 1000 / $N" | bc -l | xargs printf '%.1f'
}

# 첫 요청은 JIT 등으로 느릴 수 있어 한 번씩 버린다
measure "nobody-$(date +%s)@test.com" >/dev/null; measure "$EMAIL" >/dev/null

echo "A. 가입되지 않은 이메일        평균 $(measure "nobody-$(date +%s)@test.com") ms"
echo "B. 가입된 이메일 + 틀린 비밀번호 평균 $(measure "$EMAIL") ms"
