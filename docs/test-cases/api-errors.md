# 공통 응답 · 오류 형식 (TC-ERR)

대상: `ApiResponse`, `GlobalExceptionHandler`. 모든 API는 `{status, message, data, traceId}` 형식으로 응답합니다.

| ID         | 분류 | 확인 내용                     | 기대 결과                                     | 위험 | 자동화                                      | 관련                       |
| ---------- | ---- | ----------------------------- | --------------------------------------------- | ---- | ------------------------------------------- | -------------------------- |
| TC-ERR-001 | 정상 | 성공 응답 JSON                | status SUCCESS, message OK, data, traceId     | P3   | ApiResponseTest                             |                            |
| TC-ERR-002 | 정상 | 오류 응답 JSON                | null 필드(data, traceId) 제외                 | P3   | ApiResponseTest                             |                            |
| TC-ERR-003 | 예외 | 컨트롤러에서 RuntimeException | 400 + 공통 오류 형식                          | P2   | GlobalExceptionHandlerTest                  |                            |
| TC-ERR-004 | 보안 | SQL문이 담긴 예외 메시지      | ⚠️ 현재 응답에 그대로 노출. 기대: 일반 메시지 | P1   | GlobalExceptionHandlerTest                  | #4                         |
| TC-ERR-005 | 예외 | 없는 주소                     | 404 "Not Found"                               | P3   | GlobalExceptionHandlerTest                  |                            |
| TC-ERR-006 | 예외 | 지원하지 않는 메서드          | 405 "Method Not Allowed"                      | P3   | GlobalExceptionHandlerTest                  |                            |
| TC-ERR-007 | 보안 | 체크 예외(IOException)        | 500 + 내부 메시지 숨김                        | P2   | GlobalExceptionHandlerTest                  |                            |
| TC-ERR-301 | 예외 | 서버 오류 발생 시 서버 로그   | 원인과 traceId가 로그에 남음 (현재 없음)      | P2   | 자동화 안 함: 수정 시 로그 출력 테스트 추가 | #46                        |
| TC-ERR-302 | 예외 | 검증 실패 메시지 언어         | API마다 같은 언어 (현재 영어·한국어 혼재)     | P3   | 자동화 안 함: 정책 결정 전                  | #75, 확인 필요 (응답 언어) |

실제 API에서 SQL·내부 클래스 이름이 노출되는 경우는 [loan.md](loan.md)의 TC-LOAN-104~106에서 HTTP 요청으로 확인합니다.
