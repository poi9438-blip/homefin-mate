// S2a. 외부 호출을 기다리는 동안의 DB 커넥션 점유
// 느린 통합 분석(RiskWatch 무응답)이 커넥션 풀(10개)보다 많이 도는 동안,
// DB를 쓰는 다른 요청(리포트, 로그인)의 응답 시간·오류율과 Hikari 대기 수를 잰다.
import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://backend:8100';
const RUN = __ENV.RUN || '1';
const DURATION = __ENV.DURATION || '2m';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const analyzeDuration = new Trend('analyze_duration', true);
const analyzeFailed = new Rate('analyze_failed');
const reportDuration = new Trend('report_duration', true);
const reportFailed = new Rate('report_failed');
const loginDuration = new Trend('login_duration', true);
const loginFailed = new Rate('login_failed');
const hikariPending = new Trend('hikari_pending');
const hikariActive = new Trend('hikari_active');

export const options = {
  scenarios: {
    slow_analyze: {
      executor: 'constant-vus', exec: 'slowAnalyze',
      vus: Number(__ENV.ANALYZE_VUS || 20), duration: DURATION, gracefulStop: '90s',
    },
    report: {
      executor: 'constant-arrival-rate', exec: 'report', startTime: '5s',
      rate: 2, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 40, maxVUs: 80,
    },
    login: {
      executor: 'constant-arrival-rate', exec: 'login', startTime: '5s',
      rate: 1, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 20, maxVUs: 40,
    },
    hikari: {
      executor: 'constant-arrival-rate', exec: 'hikari', startTime: '5s',
      rate: 1, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 2, maxVUs: 5,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const email = `perf-s2-${Date.now()}@test.com`;
  http.post(`${BASE}/api/auth/signup`, JSON.stringify({ email, password: 'password123' }), { headers: JSON_HEADERS });
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: 'password123' }), { headers: JSON_HEADERS });
  return { email, token: res.json('data.token') };
}

function auth(data) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` };
}

export function slowAnalyze(data) {
  const res = http.post(`${BASE}/api/analyze`, JSON.stringify({
    loanAmount: 300000000, annualInterestRate: 4.5, termMonths: 360, monthlyIncome: 4000000, propertyType: 'NONE',
  }), { headers: auth(data), timeout: '120s' });
  analyzeDuration.add(res.timings.duration);
  analyzeFailed.add(res.status !== 200);
}

export function report(data) {
  const res = http.get(`${BASE}/api/report/summary`, { headers: auth(data), timeout: '60s' });
  reportDuration.add(res.timings.duration);
  reportFailed.add(res.status !== 200);
}

export function login(data) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: data.email, password: 'password123' }),
    { headers: JSON_HEADERS, timeout: '60s' });
  loginDuration.add(res.timings.duration);
  loginFailed.add(res.status !== 200);
}

export function hikari(data) {
  for (const [name, trend] of [['pending', hikariPending], ['active', hikariActive]]) {
    const res = http.get(`${BASE}/actuator/metrics/hikaricp.connections.${name}`, { headers: auth(data), timeout: '10s' });
    if (res.status === 200) trend.add(res.json('measurements.0.value'));
  }
}

function fmt(name, data) {
  const d = data.metrics[`${name}_duration`].values;
  const f = data.metrics[`${name}_failed`].values;
  return `${name}: n=${f.passes + f.fails} p50=${(d.med / 1000).toFixed(2)}s p95=${(d['p(95)'] / 1000).toFixed(2)}s ` +
    `max=${(d.max / 1000).toFixed(2)}s failed=${(f.rate * 100).toFixed(1)}%`;
}

export function handleSummary(data) {
  const p = data.metrics.hikari_pending ? data.metrics.hikari_pending.values : { max: 'n/a', avg: 0 };
  const a = data.metrics.hikari_active ? data.metrics.hikari_active.values : { max: 'n/a' };
  const line = `S2a run=${RUN}\n  ${fmt('report', data)}\n  ${fmt('login', data)}\n  ${fmt('analyze', data)}\n` +
    `  hikari: active max=${a.max} / pending max=${p.max} avg=${typeof p.avg === 'number' ? p.avg.toFixed(1) : p.avg}\n`;
  return { stdout: line, [`/results/s2a-run${RUN}.json`]: JSON.stringify(data, null, 2) };
}
