#!/usr/bin/env bash
# S3 측정용 데이터 생성 / 정리 (저장소 루트에서 실행, 백엔드·MySQL이 떠 있어야 함)
#   bash perf/seed/s3-seed.sh          # 생성
#   bash perf/seed/s3-seed.sh clean    # 정리
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8100}"
set -a; . ./.env; set +a
SQL="docker exec -i -e MYSQL_PWD=$MYSQL_PASSWORD homefinmate-mysql mysql -u$MYSQL_USER $MYSQL_DATABASE"

if [ "${1:-}" = "clean" ]; then
  $SQL < perf/seed/s3-cleanup.sql
  echo "정리 완료"
  $SQL -e "SELECT COUNT(*) AS seed_users FROM users WHERE email LIKE '%@seed.test'"
  exit 0
fi

# 로그인이 필요한 계정 2개는 API로 만들어 실제 BCrypt 비밀번호를 갖게 함 (이미 있으면 400, 무시)
for email in perf-s3-user@seed.test perf-s3-admin@seed.test; do
  curl -s -o /dev/null -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"password123\"}" "$BASE/api/auth/signup" || true
done
$SQL -e "UPDATE users SET role='ADMIN' WHERE email='perf-s3-admin@seed.test'"

start=$(date +%s)
$SQL < perf/seed/s3-seed.sql
echo "생성 완료 ($(( $(date +%s) - start ))초)"

$SQL -e "
SELECT
  (SELECT COUNT(*) FROM users WHERE email LIKE '%@seed.test') AS seed_users,
  (SELECT COUNT(*) FROM loan_simulations) AS loan_simulations,
  (SELECT COUNT(*) FROM loan_simulations WHERE user_id = (SELECT id FROM users WHERE email='perf-s3-user@seed.test')) AS measure_user_loans,
  (SELECT COUNT(*) FROM audit_logs) AS audit_logs,
  (SELECT COUNT(*) FROM audit_logs WHERE action LIKE 'RISKWATCH_INTEGRATION_FAILURE%') AS integration_failures"
