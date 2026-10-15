# 화면 공통 (TC-WEB)

대상: `frontend/src/utils/auth.js`(토큰 저장), `frontend/src/api/axiosInstance.js`(인증 헤더, 401 처리). 실행은 Vitest + jsdom입니다.

화면 컴포넌트 렌더링 테스트는 아직 없습니다. 지금은 인증·개인정보와 관련된 공통 동작만 확인합니다.

| ID         | 분류 | 확인 내용                      | 기대 결과                                                   | 위험 | 자동화                                     | 관련           |
| ---------- | ---- | ------------------------------ | ----------------------------------------------------------- | ---- | ------------------------------------------ | -------------- |
| TC-WEB-201 | 정상 | 로그인 정보 저장 후 로그아웃   | 토큰·사용자 정보 삭제, 비로그인 상태                        | P2   | src/utils/auth.test.js                     |                |
| TC-WEB-202 | 예외 | 저장된 사용자 정보가 깨진 JSON | 오류 없이 null                                              | P3   | src/utils/auth.test.js                     |                |
| TC-WEB-203 | 보안 | 로그아웃 후 입력 임시 저장     | ⚠️ 현재 월 소득·기존 부채가 남음. 기대: 함께 삭제           | P2   | src/utils/auth.test.js                     | #43            |
| TC-WEB-204 | 정상 | 토큰이 있을 때 API 요청        | `Authorization: Bearer <토큰>`, `X-Trace-Id` 헤더           | P1   | src/api/axiosInstance.test.js              | #3             |
| TC-WEB-205 | 보안 | 401 응답                       | 토큰 삭제 + /login 이동                                     | P1   | src/api/axiosInstance.test.js              |                |
| TC-WEB-206 | 예외 | 500 응답                       | 토큰 유지, 이동 안 함                                       | P2   | src/api/axiosInstance.test.js              |                |
| TC-WEB-301 | 보안 | 만료 토큰으로 보호된 화면 진입 | 바로 로그인 화면 (현재 화면이 열렸다가 첫 API 401에서 이동) | P3   | 자동화 안 함: 라우트 렌더링 테스트 도입 시 | #77            |
| TC-WEB-302 | 정상 | 한 요청의 traceId              | 헤더와 본문이 같은 값 (현재 따로 생성)                      | P2   | 자동화 안 함: 고치는 방법이 명확           | #3             |
| TC-WEB-303 | 보안 | 토큰 저장 위치                 | XSS로 읽을 수 없는 곳 (현재 localStorage)                   | P2   | 자동화 안 함: 인증 방식 설계 변경 필요     | #78, 확인 필요 |
