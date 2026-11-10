# 03. 로그아웃한 기기의 Access Token은 언제까지 통하나

- 날짜: 2026-11-10
- 로드맵 단계: 1 (부숴보기)
- 관련 결정: ADR 0009(Access 15분), ADR 0016(로그아웃 후 Access Token 감수)

## 예측 (오너 작성)

질문: 로그아웃 직후, 로그아웃한 기기의 Access Token으로 `/me`를 호출하면 어떤 응답이 올까? 그 응답은 언제, 어떤 응답으로 바뀔까?

> 401 응답이 오고
> 클라이언트가 리프레쉬 토큰을 이용해서 Access Token을 갱신해서 호출하면 200 응답으로 바뀌어

## 실행

15분을 기다리지 않도록 Access Token TTL을 1분으로 줄여 서버를 띄운다. 코드와 설정 파일은 바꾸지 않는다.

```bash
docker compose up -d --wait
./gradlew :server:bootRun --args='--jwt.access-token-ttl=1m'
# 다른 터미널에서
./scripts/logout-window.sh 90 10
```

## 관찰

<!-- 스크립트 출력을 붙인 뒤 AI가 초안을 쓴다 -->

## 왜

<!-- AI가 초안을 쓰고 오너가 검토한다 -->
