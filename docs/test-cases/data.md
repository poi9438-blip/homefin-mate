# 저장소 · 사용자 간 데이터 분리 (TC-DATA)

대상: `UserRepository`, `LoanSimulationRepository`, 계산 이력·리포트 API

개인 데이터 조회는 모두 **토큰의 이메일 → 사용자 id** 기준입니다. 요청 본문이나 주소로 다른 사용자 id를 받는 API는 없습니다.

| ID          | 분류 | 확인 내용                                    | 기대 결과                           | 위험 | 자동화                               | 관련 |
| ----------- | ---- | -------------------------------------------- | ----------------------------------- | ---- | ------------------------------------ | ---- |
| TC-DATA-001 | 정상 | 사용자 저장 후 이메일로 조회                 | id·가입 시각이 붙고 조회됨          | P2   | UserRepositoryTest                   |      |
| TC-DATA-002 | 경계 | 없는 이메일 조회                             | 빈 Optional, existsByEmail false    | P3   | UserRepositoryTest                   |      |
| TC-DATA-003 | 정상 | 가입된 이메일 existsByEmail                  | true                                | P3   | UserRepositoryTest                   |      |
| TC-DATA-004 | 예외 | 같은 이메일 두 번 저장                       | DB unique 제약 위반                 | P2   | UserRepositoryTest                   |      |
| TC-DATA-005 | 정상 | 사용자별 계산 이력                           | 최신순, 다른 사용자 id는 빈 결과    | P2   | LoanSimulationRepositoryTest         |      |
| TC-DATA-101 | 보안 | A가 계산 저장 후 A·B가 각각 이력·리포트 조회 | A는 1건, B는 0건                    | P1   | UserDataIsolationTest                |      |
| TC-DATA-102 | 보안 | B가 본문에 A의 userId를 넣어 계산            | B 이름으로 저장, A 이력은 비어 있음 | P1   | UserDataIsolationTest                |      |
| TC-DATA-301 | 예외 | 없는 사용자 id로 계산 저장                   | 거부 (현재 외래키 없음)             | P3   | 자동화 안 함: 스키마 변경 시 함께    | #32  |
| TC-DATA-302 | 정상 | 관리자 조회 페이징 (100만 건)                | DB에서 100건만 조회                 | P2   | 작성 예정 (성능 13-2 S3 개선과 함께) | #45  |
