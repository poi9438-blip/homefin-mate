# 인증 · 인가 (TC-AUTH)

대상: `AuthService`, `AuthController`, `JwtUtil`, `JwtAuthFilter`, `SecurityConfig`

- **공개 주소:** `/api/auth/**`, `/api/policy/**`, `/api/analysis/**`, `/api/loan/dti`, `/actuator/health`, Swagger(`SWAGGER_ENABLED=true`일 때만 켜짐)
- **역할:** 토큰은 이메일만 증명하고, 역할은 요청마다 DB(users)에서 읽습니다 (#44).
- **관리자 전용:** `/api/admin/**`
  - URL 규칙과 `AdminController`의 `@PreAuthorize`가 이중으로 막습니다.
- **나머지 주소:** 로그인이 필요합니다.

| ID          | 분류 | 확인 내용                                                            | 기대 결과                                                                               | 위험 | 자동화                                                  | 관련 |
| ----------- | ---- | -------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | ---- | ------------------------------------------------------- | ---- |
| TC-AUTH-001 | 정상 | 새 이메일로 가입 (소득 포함)                                         | 이메일·USER 반환, 소득 암호화 호출                                                      | P1   | AuthServiceTest                                         |      |
| TC-AUTH-002 | 예외 | 이미 가입된 이메일로 가입                                            | 예외 "Email already registered"                                                         | P2   | AuthServiceTest                                         |      |
| TC-AUTH-003 | 보안 | 가입 시 저장되는 사용자                                              | 비밀번호는 해시, 역할 USER, 소득 없으면 암호화 안 함                                    | P1   | AuthServiceTest                                         |      |
| TC-AUTH-004 | 정상 | 올바른 이메일·비밀번호로 로그인                                      | 토큰 발급, LOGIN_SUCCESS 감사 기록, 실패 기록 없음                                      | P1   | AuthServiceTest                                         |      |
| TC-AUTH-005 | 예외 | 비밀번호 틀림                                                        | 실패 기록 + 업무 오류 401 "Invalid credentials", 토큰 발급 안 함                        | P1   | AuthServiceTest                                         |      |
| TC-AUTH-006 | 보안 | 없는 이메일로 로그인                                                 | 비밀번호 틀림과 같은 401 + 메시지 (계정 존재 여부 숨김)                                 | P1   | AuthServiceTest                                         |      |
| TC-AUTH-007 | 보안 | 같은 비밀번호를 두 번 해시                                           | 결과가 다름 (salt), BCrypt 형식                                                         | P2   | PasswordEncoderTest                                     |      |
| TC-AUTH-008 | 정상 | `matches()` 비교                                                     | 맞으면 true, 틀리면 false                                                               | P2   | PasswordEncoderTest                                     |      |
| TC-AUTH-009 | 정상 | 토큰 발급 후 이메일·역할 추출                                        | 발급할 때 넣은 값                                                                       | P1   | JwtUtilTest                                             |      |
| TC-AUTH-010 | 보안 | 페이로드를 키 없이 디코딩                                            | 읽힘 (민감정보를 넣으면 안 되는 이유)                                                   | P2   | JwtUtilTest                                             |      |
| TC-AUTH-011 | 보안 | 페이로드 역할을 ADMIN으로 변조                                       | 무효                                                                                    | P1   | JwtUtilTest                                             |      |
| TC-AUTH-012 | 보안 | 다른 비밀키로 서명한 토큰                                            | 무효                                                                                    | P1   | JwtUtilTest                                             |      |
| TC-AUTH-013 | 경계 | 만료된 토큰                                                          | 무효                                                                                    | P1   | JwtUtilTest                                             |      |
| TC-AUTH-014 | 보안 | 32바이트 미만 비밀키                                                 | 토큰 생성 거부                                                                          | P2   | JwtUtilTest                                             |      |
| TC-AUTH-015 | 보안 | 같은 이메일의 최근 15분 실패가 5번                                   | 429, 비밀번호 확인 전에 거절, 거절도 "Too many attempts"로 기록                         | P1   | AuthServiceTest                                         | #79  |
| TC-AUTH-016 | 경계 | 최근 실패가 4번                                                      | 평소처럼 로그인 확인 (15분 기준, 센 이유는 없는 이메일·비밀번호 틀림)                   | P2   | AuthServiceTest                                         | #79  |
| TC-AUTH-017 | 예외 | 동시 가입으로 저장 중 unique 위반                                    | 400 "Email already registered" (500 아님)                                               | P2   | AuthServiceTest                                         | #72  |
| TC-AUTH-101 | 보안 | 토큰 없이 보호된 API 6개 (이력, 계산, 분석, 리포트, 관리자, metrics) | 401 + `{"status":"ERROR","message":"Unauthorized"}`                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-102 | 정상 | 토큰 없이 공개 API (DTI, 정책 추천, health)                          | 200                                                                                     | P2   | SecurityAccessTest                                      |      |
| TC-AUTH-103 | 보안 | USER 토큰으로 관리자 API 4개                                         | 403 "Access denied"                                                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-104 | 정상 | ADMIN 토큰으로 관리자 API                                            | 200                                                                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-105 | 보안 | 역할을 ADMIN으로 바꾼 변조 토큰으로 관리자 API                       | 401 (잘못된 토큰은 비로그인 취급)                                                       | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-106 | 경계 | 만료 토큰으로 보호된 API                                             | 401                                                                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-107 | 보안 | 다른 키로 서명한 ADMIN 토큰                                          | 401                                                                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-108 | 예외 | `Bearer ` 접두사 없는 Authorization 헤더                             | 401                                                                                     | P2   | SecurityAccessTest                                      |      |
| TC-AUTH-109 | 정상 | 정상 USER 토큰으로 보호된 API                                        | 200                                                                                     | P1   | SecurityAccessTest                                      |      |
| TC-AUTH-110 | 정상 | `X-Trace-Id` 헤더 있음 / 없음                                        | 받은 값 그대로 / `hfm-`로 시작하는 새 값                                                | P3   | SecurityAccessTest                                      |      |
| TC-AUTH-111 | 보안 | 실제 HTTP: 비밀번호 틀림 / 없는 이메일로 로그인                      | 둘 다 401 + "Invalid credentials" (500 아님, 계정 존재 여부 구분 불가)                  | P1   | SecurityAccessTest                                      | #88  |
| TC-AUTH-112 | 보안 | 실제 HTTP: 같은 이메일로 5번 틀린 뒤 맞는 비밀번호                   | 429, 다른 이메일은 정상 로그인                                                          | P1   | SecurityAccessTest                                      | #79  |
| TC-AUTH-113 | 보안 | 서명은 맞지만 역할(roles)이 없는 토큰                                | 권한은 DB 기준: 사용자 API 200, 관리자 API 403 (빈 `ROLE_` 권한 없음)                   | P3   | SecurityAccessTest                                      | #81  |
| TC-AUTH-114 | 보안 | DB에서 ADMIN → USER로 바꾼 뒤 같은 토큰                              | 다음 요청부터 관리자 API 403 (역할은 요청마다 DB에서 읽음)                              | P1   | SecurityAccessTest                                      | #44  |
| TC-AUTH-115 | 보안 | 삭제된 사용자의 유효한 토큰                                          | 보호된 API 401                                                                          | P2   | SecurityAccessTest                                      | #44  |
| TC-AUTH-116 | 보안 | CORS 사전 요청: 화면 주소 / 다른 출처                                | 화면 주소 허용, 다른 출처 403, credentials 허용 안 함                                   | P2   | SecurityAccessTest                                      | #28  |
| TC-AUTH-117 | 보안 | 기본 설정에서 Swagger UI·API 문서                                    | 404 (`SWAGGER_ENABLED=true`일 때만 공개)                                                | P2   | SecurityAccessTest                                      | #80  |
| TC-AUTH-301 | 보안 | URL 규칙이 빠졌을 때 `@PreAuthorize`가 막는 경우의 응답              | 403 (처리기는 TC-ERR-009에서 403 확인, HTTP 경로는 URL 규칙이 먼저 막아 재현 방법 필요) | P2   | 작성 예정 (#84 수정과 함께, 변이 검사로 현재 동작 확인) | #84  |
| TC-AUTH-302 | 예외 | 회원가입 HTTP 입력 검증 (이메일 형식, 비밀번호 8자 미만)             | 400 + 필드 이름                                                                         | P2   | 작성 예정                                               |      |
| TC-AUTH-307 | 예외 | 빈 소득 `""`으로 가입                                                | 소득 없음으로 저장 (현재 빈 문자열을 암호화)                                            | P3   | 자동화 안 함: 사소, 수정 방법 명확                      | #30  |

TC-AUTH-303(역할 없는 토큰)·305(연속 실패 제한)·306(동시 가입)은 TC-AUTH-113·112·015~017로, 304(권한 회수)는 TC-AUTH-114로 대체했습니다.
