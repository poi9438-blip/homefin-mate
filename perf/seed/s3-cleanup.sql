-- S3 측정용 데이터 정리 (s3-seed.sql로 넣은 데이터와 측정 중 seed 계정이 남긴 기록만 지움)
DELETE FROM audit_logs WHERE trace_id LIKE 'perf-seed-%' OR actor_id LIKE '%@seed.test';
DELETE FROM loan_simulations WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@seed.test') x);
DELETE FROM user_activity_logs WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@seed.test') x);
DELETE FROM login_failure_logs WHERE email LIKE '%@seed.test';
DELETE FROM users WHERE email LIKE '%@seed.test';
DROP TABLE IF EXISTS perf_seed_digits;
