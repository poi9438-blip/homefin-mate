-- 로그인 시도 제한(#79): 이메일별 최근 실패 수를 빠르게 세기 위한 인덱스
create index idx_login_failure_email_created on login_failure_logs (email, created_at);
