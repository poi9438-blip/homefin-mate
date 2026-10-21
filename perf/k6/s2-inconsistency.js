// S2b. 통합 분석 실패 시 저장 불일치 건수
// 가짜 RiskWatch가 502 + 긴 HTML 오류 페이지를 돌려주면(MODE=html502) 감사 기록 저장이 실패해 분석이 예외로 끝난다(#37).
// 이때 "사용자는 실패 응답을 받았는데 계산 결과는 저장된" 건수를 센다.
import http from "k6/http";
import { Counter } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://backend:8100";
const RUN = __ENV.RUN || "1";
const N = Number(__ENV.N || 20);

const analyzeOk = new Counter("analyze_ok");
const analyzeFail = new Counter("analyze_fail");
const savedRows = new Counter("saved_rows");

export const options = {
  scenarios: {
    analyze: {
      executor: "shared-iterations",
      vus: 1,
      iterations: N,
      maxDuration: "10m",
    },
  },
};

export function setup() {
  const email = `perf-s2b-${Date.now()}@test.com`;
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

function auth(data) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${data.token}`,
  };
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
    { headers: auth(data), timeout: "120s" },
  );
  if (res.status === 200) analyzeOk.add(1);
  else analyzeFail.add(1);
}

export function teardown(data) {
  // 이 사용자로 서버에 저장된 계산 기록 수
  const res = http.get(`${BASE}/api/loan/history`, { headers: auth(data) });
  savedRows.add(res.json("data").length);
}

export function handleSummary(data) {
  const v = (name) =>
    data.metrics[name] ? data.metrics[name].values.count : 0;
  const ok = v("analyze_ok");
  const fail = v("analyze_fail");
  const saved = v("saved_rows");
  // 저장은 됐는데 성공 응답을 받지 못한 건수
  const mismatch = Math.max(saved - ok, 0);
  const line = `S2b run=${RUN} 요청=${ok + fail} 성공응답=${ok} 실패응답=${fail} 저장된계산=${saved} 불일치=${mismatch}\n`;
  return {
    stdout: line,
    [`/results/s2b-run${RUN}.json`]: JSON.stringify(data, null, 2),
  };
}
