-- S3 측정용 데이터 생성 (s3-seed.sh가 실행)
-- 실행 전 API로 만든 계정: perf-s3-user@seed.test(측정용 사용자), perf-s3-admin@seed.test(관리자)
-- 생성: 사용자 999명(로그인 불가 더미) + 측정용 사용자 = 1,000명, 계산 기록 1,000명 × 100건 = 10만 건, 감사 기록 100만 건
-- 넣은 데이터는 이메일 '@seed.test', traceId 'perf-seed-'로 구분해서 s3-cleanup.sql로 지울 수 있음

-- 0~9 숫자 표 (곱해서 큰 수열을 만듦). MySQL은 임시 테이블을 한 쿼리에서 여러 번 참조할 수 없어 일반 테이블로 만들고 끝에 지움
DROP TABLE IF EXISTS perf_seed_digits;
CREATE TABLE perf_seed_digits (d INT PRIMARY KEY);
INSERT INTO perf_seed_digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

-- 더미 사용자 999명 (비밀번호는 로그인할 수 없는 고정 문자열)
INSERT INTO users (email, password, role, created_at)
SELECT CONCAT('perf-s3-', n, '@seed.test'), 'perf-seed-not-a-real-hash', 'USER', NOW(6)
FROM (SELECT a.d + b.d * 10 + c.d * 100 + 1 AS n FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c) t
WHERE n <= 999;

-- 계산 기록: 관리자를 뺀 seed 사용자 1,000명 × 100건, 최근 1년에 흩어지게
-- dsr 컬럼은 V2(2026-10)에서 dti + dti_basis로, dti는 V5에서 암호문 컬럼(dti_encrypted)으로 바뀜
-- 암호문은 SQL로 만들 수 없어서 seed 기록의 DTI는 비워 둠 (조회 성능 측정에는 영향 없음)
INSERT INTO loan_simulations (user_id, loan_amount, interest_rate, monthly_payment, total_interest, dti_encrypted, dti_basis, term_months, region, trace_id, created_at)
SELECT u.id, 300000000, 4.5000, 1520055.93, 247220134.80, NULL, 'WITH_EXISTING_INTEREST', 360, NULL,
       CONCAT('perf-seed-loan-', u.id, '-', s.n),
       NOW(6) - INTERVAL (s.n * 3 + u.id % 3) DAY
FROM users u
JOIN (SELECT a.d + b.d * 10 AS n FROM perf_seed_digits a, perf_seed_digits b) s
WHERE u.email LIKE 'perf-s3-%@seed.test' AND u.email <> 'perf-s3-admin@seed.test';

-- 감사 기록 100만 건 (10만 건씩 10번), 1%는 RiskWatch 연동 실패, 최근 1년에 흩어지게
INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 0 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 1 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 2 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 3 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 4 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 5 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 6 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 7 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 8 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

INSERT INTO audit_logs (actor_type, actor_id, action, trace_id, created_at)
SELECT IF(n % 100 = 0, 'SYSTEM', 'USER'),
       IF(n % 100 = 0, 'HOMEFIT_MATE', CONCAT('user:perf-', n % 999 + 1)),
       IF(n % 100 = 0, 'RISKWATCH_INTEGRATION_FAILURE: I/O error on POST request: Connection refused', 'LOGIN_SUCCESS'),
       CONCAT('perf-seed-audit-', n),
       NOW(6) - INTERVAL (n % 525600) MINUTE
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + 9 * 100000 AS n
      FROM perf_seed_digits a, perf_seed_digits b, perf_seed_digits c, perf_seed_digits d, perf_seed_digits e) t;

DROP TABLE perf_seed_digits;
