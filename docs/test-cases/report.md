# 리포트 (TC-RPT)

대상: `ReportService` (`GET /api/report/summary`, 로그인 필요). 사용자 간 분리는 [data.md](data.md)의 TC-DATA-101에서 확인합니다.

| ID         | 분류 | 확인 내용                      | 기대 결과                                                         | 위험 | 자동화                                | 관련 |
| ---------- | ---- | ------------------------------ | ----------------------------------------------------------------- | ---- | ------------------------------------- | ---- |
| TC-RPT-001 | 정상 | 계산 7건                       | 총 7건, 최근 계산, DTI 추이 5건, 적정 안내                        | P2   | ReportServiceTest                     |      |
| TC-RPT-002 | 정상 | 최근 DTI 0.60 초과             | HIGH, 경고 안내                                                   | P2   | ReportServiceTest                     |      |
| TC-RPT-003 | 경계 | 계산 0건                       | 0건 + 안내 메시지, 최근 계산·추이 없음                            | P3   | ReportServiceTest                     |      |
| TC-RPT-101 | 정상 | 실제 HTTP: 계산 후 리포트 조회 | 생성 시각·계산 시각에 시간대가 붙어 내려가고 실제 시각과 1분 이내 | P2   | ReportApiTest                         | #91  |
| TC-RPT-102 | 보안 | 리포트 응답 필드               | `reportId`·`userId`(사용자 DB id) 없음, `reportType` SUMMARY      | P2   | ReportApiTest                         | #50  |
| TC-RPT-301 | 보안 | (폐기) 응답의 reportId         | 사용자 DB id가 아닌 값 (현재 사용자 id 노출)                      | P2   | 폐기, 자동화 안 함: TC-RPT-102로 대체 | #50  |
| TC-RPT-302 | 경계 | 계산 10만 건인 사용자의 리포트 | 전체 목록 조회 없이 COUNT와 최근 5건만 사용                       | P2   | ReportServiceTest                     | #51  |
