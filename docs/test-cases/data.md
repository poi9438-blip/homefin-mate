# 저장소 · 사용자 간 데이터 분리 (TC-DATA)

대상: `UserRepository`, `LoanSimulationRepository`, 계산 이력·리포트 API, DB 마이그레이션(Flyway `db/migration`)

개인 데이터 조회는 모두 **토큰의 이메일 → 사용자 id** 기준입니다. 요청 본문이나 주소로 다른 사용자 id를 받는 API는 없습니다.

| ID          | 분류 | 확인 내용                                                                | 기대 결과                                                                                 | 위험 | 자동화                            | 관련    |
| ----------- | ---- | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- | ---- | --------------------------------- | ------- |
| TC-DATA-001 | 정상 | 사용자 저장 후 이메일로 조회                                             | id·가입 시각이 붙고 조회됨                                                                | P2   | UserRepositoryTest                |         |
| TC-DATA-002 | 경계 | 없는 이메일 조회                                                         | 빈 Optional, existsByEmail false                                                          | P3   | UserRepositoryTest                |         |
| TC-DATA-003 | 정상 | 가입된 이메일 existsByEmail                                              | true                                                                                      | P3   | UserRepositoryTest                |         |
| TC-DATA-004 | 예외 | 같은 이메일 두 번 저장                                                   | DB unique 제약 위반                                                                       | P2   | UserRepositoryTest                |         |
| TC-DATA-005 | 정상 | 사용자별 계산 이력                                                       | 최신순, 다른 사용자 id는 빈 결과                                                          | P2   | LoanSimulationRepositoryTest      |         |
| TC-DATA-006 | 정상 | 사용자별 계산 수와 최근 5건                                              | 내 기록만 COUNT, 최신순 5건                                                               | P2   | LoanSimulationRepositoryTest      | #51     |
| TC-DATA-007 | 정상 | Flyway 이력이 없는 기존 DB(ddl-auto로 만든 DB)에 dsr 기록이 있을 때 시작 | V1은 건너뛰고 V2 실행: dsr 값을 dti로 옮기고 기준 NEW_LOAN_ONLY, dsr 컬럼 삭제            | P1   | FlywayMigrationTest               |         |
| TC-DATA-008 | 정상 | 빈 DB로 시작                                                             | V1~V4 차례로 실행, dti·dti_basis 컬럼 있음, dsr 컬럼 없음                                 | P2   | FlywayMigrationTest               |         |
| TC-DATA-009 | 경계 | V2까지 적용된 DB(기록 있음)에 V3 실행                                    | 기존 값 그대로, V3 전에는 실패하던 연이율 10.5%·DTI 15.2006 저장 가능                     | P1   | FlywayMigrationTest               | #1, #74 |
| TC-DATA-010 | 정상 | 로그인 실패 수 조회 (시도 제한용)                                        | 같은 이메일·센 이유(없는 이메일·비밀번호 틀림)·15분 안의 기록만 셈                        | P2   | LoginFailureLogRepositoryTest     | #79     |
| TC-DATA-101 | 보안 | A가 계산 저장 후 A·B가 각각 이력·리포트 조회                             | A는 1건, B는 0건                                                                          | P1   | UserDataIsolationTest             |         |
| TC-DATA-102 | 보안 | B가 본문에 A의 userId를 넣어 계산                                        | B 이름으로 저장, A 이력은 비어 있음                                                       | P1   | UserDataIsolationTest             |         |
| TC-DATA-103 | 정상 | ADMIN이 연동 실패 조회 API 호출                                          | 연동 실패 기록만, 100건 이하                                                              | P2   | AdminApiTest                      | #45     |
| TC-DATA-301 | 예외 | 없는 사용자 id로 계산 저장                                               | 거부 (현재 외래키 없음)                                                                   | P3   | 자동화 안 함: 스키마 변경 시 함께 | #32     |
| TC-DATA-302 | 경계 | 감사 기록 120건에서 관리자용 최신 100건 조회 / 연동 실패만 조회          | DB에서 최신순 100건만, 연동 실패 조회는 연동 실패 기록만 (100만 건 효과는 성능 S3로 확인) | P2   | AuditLogRepositoryTest            | #45     |
