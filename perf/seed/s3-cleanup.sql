-- S3 측정용 데이터 정리 (s3-seed.sql로 넣은 데이터와 측정 중 seed 계정이 남긴 기록만 지움)
DELETE FROM audit_logs WHERE trace_id LIKE 'perf-seed-%' OR actor_id LIKE '%@seed.test' OR actor_id LIKE 'user:perf-%';
DELETE FROM loan_simulations WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@seed.test') x);
DELETE FROM user_activity_logs WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@seed.test') x);
-- 로그인 실패 기록은 V5부터 이메일 대신 HMAC이라 이메일로 찾을 수 없음 (seed는 로그인 실패 기록을 만들지 않음)
DELETE FROM users WHERE email LIKE '%@seed.test';
DROP TABLE IF EXISTS perf_seed_digits;
