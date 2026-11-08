# 0013. 인증 실패 응답: 필터가 EntryPoint를 직접 호출

- 상태: 채택
- 날짜: 2026-11-08
- 로드맵 단계: 1
- Issue: #3

## 맥락

인증 필터는 요청 헤더의 Access Token을 검증한다. 검증에 실패하면 401을 `ErrorResponse`(ADR 0011) 형식으로 돌려줘야 한다.

필터는 DispatcherServlet보다 앞에서 실행된다. 그래서 필터에서 던진 예외는 `@RestControllerAdvice`(`GlobalExceptionHandler`)가 잡지 못하고, 실패를 응답으로 바꾸는 장치가 따로 필요하다.

## 선택지

### A. 필터가 직접 응답
- 장점: 흐름이 한 파일 안에서 끝난다.
- 단점: Spring Security 흐름을 우회한다. 토큰이 없는 요청은 필터가 따로 분기해야 한다. 에러 응답을 쓰는 곳이 하나 더 늘어난다.

### B. EntryPoint에 위임
필터는 인증 정보를 등록하지 않고 다음 필터로 넘긴다. 보호된 경로이면 `AuthorizationFilter`가 거부하고, `ExceptionTranslationFilter`가 `AuthenticationEntryPoint`를 호출한다.
- 장점: 프레임워크가 정한 경로를 그대로 따른다. 401 응답을 쓰는 곳이 EntryPoint 하나다.
- 단점: `permitAll` 경로에서는 잘못된 토큰이 무시되고 요청이 통과한다. 필터의 실패 이유(만료인지 위조인지)를 EntryPoint로 넘기려면 request attribute 같은 우회로가 필요하다.

### C. `HandlerExceptionResolver`로 넘겨 `GlobalExceptionHandler`에서 처리
- 장점: 에러 응답을 만드는 곳이 하나로 모인다.
- 단점: Security 영역 밖의 컴포넌트를 필터 안으로 끌어온다. 토큰이 없는 경우는 필터가 따로 분기해야 한다.

### D. 필터가 EntryPoint를 직접 호출
토큰이 없으면 B와 같이 통과시킨다. 토큰이 있는데 잘못됐으면 필터가 그 자리에서 `entryPoint.commence(...)`를 호출하고, 체인을 더 진행하지 않는다. Spring이 제공하는 `BearerTokenAuthenticationFilter`가 이렇게 동작한다.
- 장점: 401 응답을 쓰는 곳이 EntryPoint 하나다. 잘못된 토큰은 경로와 관계없이 바로 알려준다.
- 단점: `permitAll` 경로에도 잘못된 토큰을 보내면 401을 받는다.

## 결정

D를 선택한다.

## 이유 (오너 작성)

> D의 선택 이유는 앱이 로그인 이후에 잘못된 토큰을 들고 있다면 이후에 다시 발급받아야하기 때문에 permitAll이라도 검사를 하기 위해서야

> (C를 고르지 않은 이유) GlobalExceptionHandler로 넘기더라도 필터에 추가 처리 코드가 필요하고, Security 영역 밖의 컴포넌트를 필터 안으로 끌어와야해.

## 결과

- 앱은 공개 API(`/signup`, `/login`)에 토큰을 붙이지 않아야 한다. 만료된 토큰을 붙인 채 `/login`을 호출하면 401을 받는다.
- 401을 쓰는 곳은 직접 만든 `AuthenticationEntryPoint` 하나다. 이 클래스를 등록하지 않으면 Spring 기본값인 `Http403ForbiddenEntryPoint`가 호출되어 403과 Spring 기본 본문이 나간다. (결정 과정에서 TRACE 로그로 확인)
- 앱이 재발급을 시도할지 다시 로그인할지 판단하려면 만료와 위조를 구분하는 에러 코드가 필요하다. 구분 방식은 필터 구현 단계에서 정한다.

## 나중에 알게 된 것

- (2026-11-08) 처음에는 "B 방식은 인가 필터를 거치지 않는다"고 이해했다. TRACE 로그를 보니 B에서도 요청은 `AuthorizationFilter`를 거친다. `/login`이 통과하는 이유는 인가 필터가 `permitAll` 규칙에 따라 익명 사용자도 허용하기 때문이다. 인증("누구인가")과 인가("들어와도 되는가")는 서로 다른 필터가 맡는다.
