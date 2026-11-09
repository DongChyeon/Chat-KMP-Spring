#!/usr/bin/env bash
# 같은 이메일로 회원가입 요청을 동시에 N개 보내고 응답 코드별 개수를 센다.
# 사용법: ./scripts/race-signup.sh [요청 수] [이메일]
# 서버(localhost:8080)와 docker compose의 DB가 떠 있어야 한다.
set -euo pipefail

N=${1:-20}
EMAIL=${2:-race-$(date +%s)@test.com}
URL=http://localhost:8080/api/auth/signup

echo "같은 이메일($EMAIL)로 동시 요청 ${N}개"
for _ in $(seq 1 "$N"); do
  curl -s -o /dev/null -w '%{http_code}\n' \
    -H 'Content-Type: application/json' -X POST "$URL" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"password123\",\"nickname\":\"race\"}" &
done | sort | uniq -c
wait

echo "DB에 저장된 계정 수: $(docker compose exec -T postgres psql -U chat -d chat -tAc "select count(*) from users where email='$EMAIL'")"
