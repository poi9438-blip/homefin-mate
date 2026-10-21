// S1. 외부 시스템(RiskWatch) 장애 시 통합 분석 응답 시간
// 가짜 RiskWatch가 응답하지 않는 상황(지연 60초)에서 POST /api/analyze 의 응답 시간을 잰다.
// 동시 사용자 수는 커넥션 풀(10)보다 작게 두어, 커넥션 부족(S2)의 영향 없이 외부 호출 대기만 본다.
import http from "k6/http";
import { check } from "k6";
import { Trend, Rate } from "k6/metrics";

// 회원가입·로그인(준비 요청)을 빼고 분석 요청만 따로 기록
const analyzeDuration = new Trend("analyze_duration", true);
const analyzeFailed = new Rate("analyze_failed");

const BASE = __ENV.BASE_URL || "http://backend:8100";
const RUN = __ENV.RUN || "1";

export const options = {
  scenarios: {
    analyze: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "3m",
      gracefulStop: "90s",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
};

export function setup() {
  const email = `perf-s1-${Date.now()}@test.com`;
  const headers = { "Content-Type": "application/json" };
  http.post(
    `${BASE}/api/auth/signup`,
    JSON.stringify({ email, password: "password123" }),
    { headers },
  );
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email, password: "password123" }),
    { headers },
  );
  return { token: res.json("data.token") };
}

export default function (data) {
  const res = http.post(
    `${BASE}/api/analyze`,
    JSON.stringify({
      loanAmount: 300000000,
      annualInterestRate: 4.5,
      termMonths: 360,
      monthlyIncome: 4000000,
      propertyType: "NONE",
      // 정책 판정용 필수 입력 (없으면 400)
      housePrice: 400000000,
      householdAnnualIncome: 48000000,
      firstHome: true,
      newlywed: false,
      childCount: 0,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${data.token}`,
      },
      timeout: "120s",
    },
  );
  analyzeDuration.add(res.timings.duration);
  analyzeFailed.add(res.status !== 200);
  check(res, { "status 200": (r) => r.status === 200 });
}

export function handleSummary(data) {
  const d = data.metrics.analyze_duration.values;
  const count =
    data.metrics.analyze_failed.values.passes +
    data.metrics.analyze_failed.values.fails;
  const failed = data.metrics.analyze_failed.values.rate;
  const seconds = data.state.testRunDurationMs / 1000;
  const line =
    `S1 run=${RUN} analyze=${count} rps=${(count / seconds).toFixed(3)} ` +
    `p50=${(d.med / 1000).toFixed(2)}s p95=${(d["p(95)"] / 1000).toFixed(2)}s p99=${(d["p(99)"] / 1000).toFixed(2)}s ` +
    `max=${(d.max / 1000).toFixed(2)}s failed=${(failed * 100).toFixed(1)}%\n`;
  return {
    stdout: line,
    [`/results/s1-run${RUN}.json`]: JSON.stringify(data, null, 2),
  };
}
