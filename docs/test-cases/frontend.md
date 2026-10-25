# 화면 공통 (TC-WEB)

대상: `frontend/src/utils/auth.js`(토큰 저장·만료 확인), `frontend/src/api/axiosInstance.js`(인증 헤더, traceId, 401 처리), `frontend/src/hooks`(테마·큰 글씨), 대시보드·리포트·관리자 화면. 실행은 Vitest + jsdom입니다.

화면 테스트는 API를 가짜로 바꾸고 차트는 빈 컴포넌트로 대체해, 화면에 보이는 글자와 API 호출을 확인합니다. 기존 TC-WEB-301(만료 토큰)·302(traceId)는 TC-WEB-216·217로 대체했습니다.

- **시각 표시 (#91):** 서버가 시각에 시간대를 붙여 보내므로(`2026-10-25T12:13:04Z`) 화면은 `new Date()`로 읽어 브라우저 시간대로 표시합니다.
- **만료 토큰 (#77):** 화면은 JWT의 `exp`만 읽어 이미 지났으면 토큰을 지우고 로그인 화면으로 보냅니다. 서명 확인과 최종 판단은 서버가 합니다(읽을 수 없는 토큰은 서버 응답에 맡김).

| ID         | 분류 | 확인 내용                                                         | 기대 결과                                                                                                                   | 위험 | 자동화                                 | 관련           |
| ---------- | ---- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---- | -------------------------------------- | -------------- |
| TC-WEB-201 | 정상 | 로그인 정보 저장 후 로그아웃                                      | 토큰·사용자 정보 삭제, 비로그인 상태                                                                                        | P2   | src/utils/auth.test.js                 |                |
| TC-WEB-202 | 예외 | 저장된 사용자 정보가 깨진 JSON                                    | 오류 없이 null                                                                                                              | P3   | src/utils/auth.test.js                 |                |
| TC-WEB-203 | 보안 | 로그아웃 후 입력 임시 저장                                        | `hfm_draft_*` 모두 삭제 (월 소득·기존 대출 월 이자), 다른 저장 값은 유지                                                    | P2   | src/utils/auth.test.js                 | #43            |
| TC-WEB-204 | 정상 | 토큰이 있을 때 API 요청                                           | `Authorization: Bearer <토큰>`, `X-Trace-Id` 헤더                                                                           | P1   | src/api/axiosInstance.test.js          | #3             |
| TC-WEB-205 | 보안 | 401 응답                                                          | 토큰 삭제 + /login 이동                                                                                                     | P1   | src/api/axiosInstance.test.js          |                |
| TC-WEB-206 | 예외 | 500 응답                                                          | 토큰 유지, 이동 안 함                                                                                                       | P2   | src/api/axiosInstance.test.js          |                |
| TC-WEB-210 | 정상 | 로그인 요청(`/auth/login`)의 401 응답                             | 토큰 유지, 화면 이동 없음 (로그인 화면이 오류 문구 표시)                                                                    | P2   | src/api/axiosInstance.test.js          | #88            |
| TC-WEB-207 | 정상 | 리포트 DTI 추이 표에 전환 전 기록(NEW_LOAN_ONLY)과 새 기록이 섞임 | 전환 전 기록에만 "기존 대출 이자 미포함" 표시, 기준 문구 DTI 60%                                                            | P2   | src/pages/ReportPage.test.jsx          |                |
| TC-WEB-208 | 정상 | 지역 분석 표시: 예시 데이터 / 없는 지역 / 연결 실패               | 예시 데이터는 ₩ 없이 "예시 데이터 · 실제 시세 아님", 분석 불가는 숫자 없이 이유만                                           | P2   | src/components/RegionSummary.test.jsx  | #66            |
| TC-WEB-209 | 예외 | 화면 오류 문구: 500 / 연결 실패 / 400                             | 500·연결 실패는 화면의 한국어 안내, 400은 서버 검증 메시지                                                                  | P2   | src/utils/errorMessage.test.js         | #4             |
| TC-WEB-211 | 정상 | 리포트 DTI 추이 (서버는 최신순 3건, DTI 없는 기록 포함)           | 표·차트가 과거 → 최근 순, DTI 없음은 "판단 불가" 배지(초과 아님), 생성 시각은 서버 값                                       | P2   | src/pages/ReportPage.test.jsx          | #52, #53       |
| TC-WEB-212 | 정상 | 기록이 없을 때 "대출 시뮬레이터로 이동"                           | 페이지 새로 불러오기 없이 화면만 이동 (Link)                                                                                | P3   | src/pages/ReportPage.test.jsx          | #53            |
| TC-WEB-213 | 정상 | 대시보드 요약 카드                                                | 리포트 `reportData`의 총 계산 수·최근 DTI·최근 월 상환금, 이력의 DTI 적정 비율(판단 불가 제외), 정책 수·위험 등급 카드 없음 | P2   | src/pages/DashboardPage.test.jsx       | #49            |
| TC-WEB-214 | 정상 | 관리자 화면 새로고침                                              | 현재 탭 기록을 다시 불러옴                                                                                                  | P3   | src/pages/AdminPage.test.jsx           | #47            |
| TC-WEB-215 | 정상 | 관리자 로그인 실패 탭                                             | IP 열 없음, 이메일 검색은 검색 API 결과(앞뒤 공백 제거), 전체 보기는 최근 기록                                              | P2   | src/pages/AdminPage.test.jsx           | #48, #89       |
| TC-WEB-216 | 보안 | 만료 시각이 지난 토큰 / 만료 전 / 읽을 수 없는 토큰               | 만료 토큰은 로그인 상태 아님 + 삭제, 나머지는 로그인 상태(서버가 판단)                                                      | P1   | src/utils/auth.test.js                 | #77            |
| TC-WEB-217 | 정상 | 본문에 traceId가 있는 요청 / 없는 요청                            | 헤더 `X-Trace-Id`가 본문 값과 같음 / 새 `hfm-` 값                                                                           | P2   | src/api/axiosInstance.test.js          | #3             |
| TC-WEB-218 | 정상 | 다크로 저장된 상태에서 라이트로 바꾸기 (큰 글씨도)                | 다른 컴포넌트·html 클래스·저장 값이 바로 같이 바뀜                                                                          | P3   | src/hooks/preferences.test.jsx         | #67            |
| TC-WEB-303 | 보안 | 토큰 저장 위치                                                    | XSS로 읽을 수 없는 곳 (현재 localStorage)                                                                                   | P2   | 자동화 안 함: 인증 방식 설계 변경 필요 | #78, 확인 필요 |
