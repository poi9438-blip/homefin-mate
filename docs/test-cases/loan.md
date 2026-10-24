# 대출 계산 · DTI (TC-LOAN)

대상: `LoanService`, `LoanController` (`POST /api/loan/calculate`, `POST /api/loan/dti`, `GET /api/loan/history`)

**현재 구현 (요구사항과 구분)**

- **월 상환액:** 원리금균등 공식 `M = P·r / (1 − (1+r)^−n)`
  - `r = 연이율 / 1200`, 소수 10자리에서 반올림합니다.
  - 월 상환액은 원 단위 소수 2자리입니다.
- **총상환액·총이자:** 총상환액 = M × n, 총이자 = 총상환액 − P
- **DTI (2026-10 DSR에서 전환):** (월 상환액 + 기존 대출 월 이자) ÷ 월 소득, 소수 4자리입니다.
  - 기존 대출 월 이자는 선택 입력이고, 비우면 0으로 계산합니다.
  - 0.60 이하이면 ACCEPTABLE입니다 (`DtiRule`, 정책 모기지 DTI 60% 기준). 예전 기준 0.43은 없앴습니다(#14).
  - 계산 기준(`dti_basis`)을 같이 저장합니다. 새 기록은 `WITH_EXISTING_INTEREST`, 전환 전 기록(예전 DSR 값을 옮김)은 `NEW_LOAN_ONLY`입니다.
  - 기존 TC-LOAN-302(기존 부채 포함)·303(판정 기준 미정)은 TC-LOAN-010·011과 TC-LOAN-008로 대체했습니다.
- **저장 범위 (V3, 2026-10):** `interest_rate`는 `DECIMAL(6,4)`(99.9999%까지), `dti`는 `DECIMAL(7,4)`(999.9999까지)입니다.
  - 연이율이 99.9999를 넘으면 저장 전에 400으로 막습니다(`@DecimalMax`).
  - DTI가 999.9999를 넘으면 999.9999로 잘라 저장·응답합니다(`DtiRule.MAX`). 판정은 HIGH로 같습니다.
- **DB 변경:** Flyway `V2__dsr_to_dti.sql`이 `dsr` → `dti` + `dti_basis`로 바꿉니다 ([data.md](data.md) TC-DATA-007·008).

| ID          | 분류 | 확인 내용                                                            | 기대 결과                                                                                        | 위험 | 자동화                                                                        | 관련           |
| ----------- | ---- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ---- | ----------------------------------------------------------------------------- | -------------- |
| TC-LOAN-001 | 정상 | 3억, 4.5%, 360개월, 소득 400만                                       | 월 1,520,055.93 / 총이자 247,220,134.80 / DTI 0.3800 ACCEPTABLE, 기준 WITH_EXISTING_INTEREST     | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-002 | 정상 | 같은 대출, 소득 250만                                                | DTI 0.6080 HIGH                                                                                  | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-003 | 정상 | 1.2억, 3%, 120개월                                                   | 월 1,158,728.94 / DTI 0.2317                                                                     | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-004 | 정상 | 계산 저장                                                            | 토큰 사용자 id, 계산값(DTI·계산 기준 포함), traceId로 저장 + 이용 기록                           | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-005 | 정상 | 이력 조회                                                            | 최신순, 총상환액·판정 재계산, 계산 기준 그대로 (전환 전 기록은 NEW_LOAN_ONLY)                    | P2   | LoanServiceTest                                                               |                |
| TC-LOAN-006 | 경계 | 없는 사용자의 이력                                                   | 빈 목록, DB 조회 안 함                                                                           | P2   | LoanServiceTest                                                               |                |
| TC-LOAN-007 | 정상 | DTI 단독 계산 (152만 / 400만)                                        | 0.3800 ACCEPTABLE, 저장 안 함                                                                    | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-008 | 경계 | DTI 0.60 / 0.6001                                                    | ACCEPTABLE / HIGH                                                                                | P1   | LoanServiceTest                                                               | #14            |
| TC-LOAN-009 | 경계 | 소득 0                                                               | ⚠️ 현재 DTI 0 (적정으로 보임). 기대: 판단 불가                                                   | P2   | LoanServiceTest                                                               | #64, 확인 필요 |
| TC-LOAN-010 | 정상 | 3억, 4.5%, 360개월, 소득 400만, 기존 대출 월 이자 50만               | DTI 0.5050 (월 상환액 + 50만 ÷ 400만), 기준 WITH_EXISTING_INTEREST                               | P1   | LoanServiceTest                                                               | #8             |
| TC-LOAN-011 | 정상 | DTI 단독 계산: 월 상환 100만, 소득 200만, 기존 대출 월 이자 0 / 30만 | 0.50 ACCEPTABLE / 0.6500 HIGH (기존 대출 이자로 판정이 바뀜)                                     | P1   | LoanServiceTest                                                               | #8             |
| TC-LOAN-012 | 경계 | DTI 단독 계산: 월 상환 152만, 소득 1원                               | 999.9999로 자름, HIGH                                                                            | P2   | LoanServiceTest                                                               | #74            |
| TC-LOAN-101 | 예외 | 필수값(원금) 없이 계산 요청                                          | 400 + `loanAmount`                                                                               | P2   | LoanApiValidationTest                                                         |                |
| TC-LOAN-102 | 경계 | 기간 601개월, 음수 원금                                              | ⚠️ 현재 200으로 계산·저장 (통합 분석 API는 400). 기대: 400                                       | P1   | LoanApiValidationTest                                                         | #82            |
| TC-LOAN-103 | 경계 | 기간 0개월                                                           | ⚠️ 현재 400 "/ by zero". 기대: 검증 메시지                                                       | P2   | LoanApiValidationTest                                                         | #6             |
| TC-LOAN-104 | 경계 | 연이율 10%                                                           | 200 계산·저장                                                                                    | P1   | LoanApiValidationTest                                                         | #1             |
| TC-LOAN-105 | 경계 | 소득 10만 (DTI 1520%)                                                | 200, DTI 15.2006 HIGH, 저장                                                                      | P1   | LoanApiValidationTest                                                         | #74            |
| TC-LOAN-106 | 예외 | DTI API에 월 상환액 없이 요청                                        | ⚠️ 현재 400 + NPE 메시지(내부 변수 이름). 기대: 400 검증 메시지                                  | P2   | LoanApiValidationTest                                                         | #6, #4         |
| TC-LOAN-107 | 경계 | 연이율 100% (대출 계산·통합 분석)                                    | 400 + `annualInterestRate`, 저장 시도 없음 (SQL 노출 없음)                                       | P1   | LoanApiValidationTest                                                         | #1, #4         |
| TC-LOAN-201 | 경계 | 100만, 12%, 1개월                                                    | 월 1,010,000.00 / 총이자 10,000.00                                                               | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-202 | 정상 | 5개 조건(최대 600개월, 0.1%, 7.9%, 약 10억)                          | 월 상환액이 **테스트에서 따로 계산한** 공식 값과 1원 이내, 총상환액 = M×n, 총이자 = 총상환액 − P | P1   | LoanServiceTest                                                               |                |
| TC-LOAN-203 | 정상 | 계산만 하는 `compute()`                                              | 같은 계산값, 저장·이용 기록 없음, simulationId 없음                                              | P1   | LoanServiceTest                                                               | #5             |
| TC-LOAN-301 | 경계 | 연이율 0%                                                            | 월 상환액 = 원금 ÷ 개월 (현재 0)                                                                 | P1   | 자동화 안 함: 고치는 방법이 명확해서 수정 PR에서 정상 테스트로                | #34            |
| TC-LOAN-304 | 보안 | 아주 큰 기간(예: 1억 개월) 1건                                       | 400으로 즉시 거부. 현재 계산량이 기간에 비례해 커짐                                              | P1   | 자동화 안 함: 테스트가 오래 멈춤, 측정값은 [테스트 보고서](../TEST_REPORT.md) | #82            |
