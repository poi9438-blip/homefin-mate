# 관측 · 추적 (TC-OBS)

대상: `JwtAuthFilter`(요청 span 속성), `observability/docker-compose.observability.yml`(OpenTelemetry → `grafana/otel-lgtm`), `observability/docker-compose.observability.full.yml`(FinFlow·RiskWatch도 관측)

**현재 구현**

- **두 번호를 함께 씁니다.**
  - `hfm-` traceId: 업무 번호입니다. 화면·DB·감사 기록·응답에 남고, `X-Trace-Id` 헤더 하나가 기준입니다([api-errors.md](api-errors.md) TC-ERR-013~015).
  - OTel trace id: 관측용입니다. 에이전트가 만들고 서비스 사이에는 `traceparent` 헤더로 이어집니다.
  - 요청 span에 `hfm.trace_id` 속성을 붙이고, 로그에도 MDC의 `traceId`를 함께 보냅니다. 그래서 Grafana에서 hfm 번호로 추적과 로그를 모두 찾을 수 있습니다.
- **세 서비스 함께 관측:** 통합 compose(`full/`)와 함께 띄우면 FinFlow·RiskWatch도 에이전트로 같은 Grafana에 보냅니다. 세 서비스 모두 요청 span에 같은 속성 `hfm.trace_id`를 붙이고, 서로 부를 때 `X-Trace-Id`(업무 번호)와 `traceparent`(OTel)를 함께 보내서 한 추적으로 이어집니다.
- **관측은 선택입니다.** 기본 compose 이미지에는 에이전트가 없습니다. 관측 compose를 함께 띄울 때만 `otel` 빌드 단계(Java 에이전트 2.33.0, Python `opentelemetry-instrument`)를 씁니다.
- **span에 남기지 않는 것:** DB 쿼리는 값 대신 `?`로 남습니다. 요청·응답 본문과 Authorization 헤더는 기록하지 않습니다(에이전트 기본값).

| ID         | 분류 | 확인 내용                                                                                            | 기대 결과                                                                                                                                                                                                 | 위험 | 자동화                                                                                          | 관련 |
| ---------- | ---- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----------------------------------------------------------------------------------------------- | ---- |
| TC-OBS-001 | 정상 | 요청 span 안에서 필터 실행: `X-Trace-Id` 있음 / 없음                                                 | span 속성 `hfm.trace_id`가 보낸 값 / 서버가 만든 `hfm-` 값, 응답 헤더와 같은 값                                                                                                                           | P2   | JwtAuthFilterSpanTest                                                                           | #90  |
| TC-OBS-301 | 정상 | 관측 compose로 실서버 통합 분석 1번 (`X-Trace-Id` 지정)                                              | Tempo에서 hfm 번호로 추적 1건, 백엔드 요청·DB·RiskWatch 재시도·Python 요청 span이 한 추적, Loki에서 hfm 번호로 로그, 지표 수집                                                                            | P2   | 자동화 안 함: Docker 관측 스택이 필요해 PR마다 실서버에서 확인 (README "관측 실행")             | #90  |
| TC-OBS-302 | 정상 | 관측·통합 compose로 실서버 통합 분석 1번, 신청 1번, FinFlow 운영자 심사 시작 1번 (`X-Trace-Id` 지정) | 통합 분석: HomeFin·RiskWatch·Python span이 한 추적. 신청: HomeFin·FinFlow span이 한 추적. 심사 시작: FinFlow·RiskWatch span이 한 추적. 모두 `hfm.trace_id`로 찾고, Loki에서 같은 traceId로 세 서비스 로그 | P2   | 자동화 안 함: Docker 관측 스택과 세 저장소가 필요해 PR마다 실서버에서 확인 (README "관측 실행") |      |
