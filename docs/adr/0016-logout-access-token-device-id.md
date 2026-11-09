# 0016. 로그아웃: Access Token + deviceId로 행을 식별한다

- 상태: 채택
- 날짜: 2026-11-09
- 로드맵 단계: 1
- Issue: #3

## 맥락

로그아웃은 "이 기기만"을 기본으로 한다(ADR 0010). `refresh_tokens`에서 지울 행을 무엇으로 식별할지 정해야 한다. 행은 `(user_id, device_id)`마다 하나이고, `token_hash`에 unique 인덱스가 있다.

## 선택지

### A. Refresh Token만 (본문)
- 장점: `permitAll`이라 Access Token이 만료돼도 바로 로그아웃된다. 재발급 API와 방식이 같다.
- 단점: Refresh Token을 가진 사람은 누구나 그 기기를 로그아웃시킬 수 있다.

### B. Access Token + deviceId
- 장점: 인증된 본인 기기만 지울 수 있다. "다른 기기 로그아웃"으로 확장하기 쉽다.
- 단점: Access Token이 만료됐으면 앱이 재발급부터 하고 로그아웃해야 한다.

### C. Access Token + Refresh Token
- 장점: 두 토큰을 모두 가져야 하므로 가장 엄격하다.
- 단점: 검증 단계와 요청이 가장 복잡하다. Access Token이 만료됐으면 재발급이 먼저 필요하다.

## 결정

B를 선택한다.

## 이유 (오너 작성)

> 나중에 다른 기기 로그아웃 및 FCM 푸시 알림 확장을 용이하게 하기 위해 Access Token + deviceId 조합으로 수행할게.

## 결과

- 로그아웃 API는 인증이 필요하다. userId는 Access Token에서, deviceId는 요청 본문에서 받는다.
- Access Token이 만료되면 필터가 `TOKEN_EXPIRED`를 돌려주므로, 앱은 재발급 후 다시 로그아웃한다. 재발급까지 실패하면 로컬 토큰만 지운다.
- 앱은 서버 응답과 관계없이 로컬 토큰을 지운다.
- 로그아웃해도 이미 발급된 Access Token은 만료(최대 15분)까지 유효하다(ADR 0009).

## 나중에 알게 된 것
