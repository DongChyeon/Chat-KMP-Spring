# 0011. 에러 응답 형식: 직접 정의한 ErrorResponse

- 상태: 채택
- 날짜: 2026-11-08
- 로드맵 단계: 1
- Issue: #3

## 맥락

회원가입부터 에러 응답이 생긴다. 이 형식은 모든 API에 쓰이고, 앱이 그대로 파싱한다. 앱은 에러 메시지 문구가 아니라 에러 종류(`code`)로 분기한다.

## 선택지

### A. ProblemDetail (RFC 9457)
- 장점: HTTP 에러 응답 국제 표준이다. Spring이 기본 지원해서 Spring 내부 에러(404, 405, 400 등)도 같은 형식으로 나온다. `code` 같은 확장 필드를 추가할 수 있다.
- 단점: 서버는 Spring 클래스(Jackson 직렬화)를 쓰고, 앱은 같은 모양의 DTO를 따로 만들어야 한다. 앱이 쓰지 않는 필드(`type`, `title`, `instance`)가 포함된다.

### B. 직접 정의한 형식
- 장점: `shared`의 `@Serializable` 클래스 하나를 서버와 앱이 함께 쓴다. 응답이 단순하다.
- 단점: Spring이 자동으로 만드는 에러를 모두 이 형식으로 바꿔야 한다.

## 결정

B. `shared`에 `ErrorResponse(code, message)`와 `ErrorCode` enum을 정의해서 사용한다.

## 이유 (오너 작성)

> 한 클래스로 서버와 앱의 통신 규격을 맞추면서 휴먼 에러를 방지시키고, json 데이터를 가볍게 유지하기 위해서 변환하는 수고가 있음에도 사용자 정의 방식을 사용할게

## 결과

- `ErrorCode`를 바꾸면 서버와 앱 양쪽에서 컴파일 에러로 드러난다.
- Spring MVC의 기본 에러(400, 404, 405 등)는 `ResponseEntityExceptionHandler`를 확장해 `ErrorResponse`로 변환한다.
- 인증 실패(401) 응답은 필터에서 직접 작성한다(ADR 0008).

## 나중에 알게 된 것

