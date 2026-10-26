// S3. 데이터가 쌓였을 때 조회 속도 (perf/seed/s3-seed.sh로 데이터를 넣은 뒤 실행)
// ENDPOINT: report | history | audit | integration
//   report      GET /api/report/summary        (측정용 사용자, 계산 100건 / 전체 10만 건)
//   history     GET /api/loan/history          (측정용 사용자)
//   audit       GET /api/admin/audit-logs      (관리자, 감사 기록 100만 건)
//   integration GET /api/admin/integration-failures (관리자)
// 동시 요청 없이 1명이 차례로 보내 순수한 처리 시간을 잰다.
import http from "k6/http";
import { Trend, Rate } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://backend:8100";
const RUN = __ENV.RUN || "1";
const ENDPOINT = __ENV.ENDPOINT || "report";
const ITER = Number(__ENV.ITER || 20);

const PATHS = {
  report: "/api/report/summary",
  history: "/api/loan/history",
  audit: "/api/admin/audit-logs",
  integration: "/api/admin/integration-failures",
};
const ADMIN = ENDPOINT === "audit" || ENDPOINT === "integration";

const duration = new Trend("req_duration", true);
const failed = new Rate("req_failed");

export const options = {
  scenarios: {
    main: {
      executor: "per-vu-iterations",
      vus: 1,
      iterations: ITER,
      maxDuration: "30m",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "p(95)", "max"],
};

export function setup() {
  const email = ADMIN ? "perf-s3-admin@seed.test" : "perf-s3-user@seed.test";
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email, password: "password123" }),
    { headers: { "Content-Type": "application/json" } },
  );
  return {
    token:
      res.cookies.hfm_session[0]
        .value /* 토큰은 HttpOnly 쿠키로만 옴(#78), Bearer로 보냄 */,
  };
}

export default function (data) {
  const res = http.get(`${BASE}${PATHS[ENDPOINT]}`, {
    headers: { Authorization: `Bearer ${data.token}` },
    timeout: "300s",
  });
  duration.add(res.timings.duration);
  failed.add(res.status !== 200);
}

export function handleSummary(data) {
  const d = data.metrics.req_duration.values;
  const f = data.metrics.req_failed.values;
  const line =
    `S3 ${ENDPOINT} run=${RUN} n=${f.passes + f.fails} p50=${d.med.toFixed(0)}ms p95=${d["p(95)"].toFixed(0)}ms ` +
    `max=${d.max.toFixed(0)}ms failed=${(f.rate * 100).toFixed(1)}%\n`;
  return {
    stdout: line,
    [`/results/s3-${ENDPOINT}-run${RUN}.json`]: JSON.stringify(data, null, 2),
  };
}
