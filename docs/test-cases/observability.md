# 관측 · 추적 (TC-OBS)

대상: `JwtAuthFilter`(요청 span 속성), `BusinessMetrics`(업무 지표), `observability/grafana/`(대시보드·알림 규칙), `observability/docker-compose.observability.yml`(OpenTelemetry → `grafana/otel-lgtm`), `observability/docker-compose.observability.full.yml`(FinFlow·RiskWatch도 관측)

**현재 구현**

- **두 번호를 함께 씁니다.**
  - `hfm-` traceId: 업무 번호입니다. 화면·DB·감사 기록·응답에 남고, `X-Trace-Id` 헤더 하나가 기준입니다([api-errors.md](api-errors.md) TC-ERR-013~015).
  - OTel trace id: 관측용입니다. 에이전트가 만들고 서비스 사이에는 `traceparent` 헤더로 이어집니다.
  - 요청 span에 `hfm.trace_id` 속성을 붙이고, 로그에도 MDC의 `traceId`를 함께 보냅니다. 그래서 Grafana에서 hfm 번호로 추적과 로그를 모두 찾을 수 있습니다.
- **세 서비스 함께 관측:** 통합 compose(`full/`)와 함께 띄우면 FinFlow·RiskWatch도 에이전트로 같은 Grafana에 보냅니다. 세 서비스 모두 요청 span에 같은 속성 `hfm.trace_id`를 붙이고, 서로 부를 때 `X-Trace-Id`(업무 번호)와 `traceparent`(OTel)를 함께 보내서 한 추적으로 이어집니다.
- **업무 지표:**
  - `hfm.riskwatch.evaluations`: 통합 분석의 RiskWatch 결과(속성 `status`: `EVALUATED`·`UNAVAILABLE`·`NOT_EVALUATED`).
  - `hfm.loan.applications`: 신청의 FinFlow 결과(속성 `product_code`·`outcome`: `RECEIVED`·`REPLAYED`·`CONFLICT`·`UNAVAILABLE`·`NOT_FOUND`·`REJECTED`). 서버 확인에서 막혀 FinFlow를 부르지 않은 신청은 세지 않습니다.
  - 정해진 속성 조합은 시작할 때 0으로 만들어 둡니다. 처음 생긴 값은 Prometheus `increase()`가 증가로 보지 않아서, 그렇지 않으면 첫 실패 1건을 알림이 놓칩니다.
  - FinFlow·RiskWatch 업무 지표는 각 저장소 문서에 있습니다.
- **대시보드·알림:** 관측 compose가 Grafana에 대시보드 "FinSight 서비스"와 알림 규칙(폴더 FinSight)을 자동으로 등록합니다. 알림은 Grafana 알림 화면에서만 봅니다(밖으로 보내지 않음).
- **관측은 선택입니다.** 기본 compose 이미지에는 에이전트가 없습니다. 관측 compose를 함께 띄울 때만 `otel` 빌드 단계(Java 에이전트 2.33.0, Python `opentelemetry-instrument`)를 씁니다.
- **span에 남기지 않는 것:** DB 쿼리는 값 대신 `?`로 남습니다. 요청·응답 본문과 Authorization 헤더는 기록하지 않습니다(에이전트 기본값).

| ID         | 분류 | 확인 내용                                                                                                | 기대 결과                                                                                                                                                                                                 | 위험 | 자동화                                                                                          | 관련 |
| ---------- | ---- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----------------------------------------------------------------------------------------------- | ---- |
| TC-OBS-001 | 정상 | 요청 span 안에서 필터 실행: `X-Trace-Id` 있음 / 없음                                                     | span 속성 `hfm.trace_id`가 보낸 값 / 서버가 만든 `hfm-` 값, 응답 헤더와 같은 값                                                                                                                           | P2   | JwtAuthFilterSpanTest                                                                           | #90  |
| TC-OBS-201 | 정상 | 통합 분석의 RiskWatch 결과: 판정 / 응답 없음 / 입력 모자람(부르지 않음)                                  | 지표 기록 `EVALUATED` / `UNAVAILABLE` / `NOT_EVALUATED` 1번씩                                                                                                                                             | P2   | AnalyzeServiceTest                                                                              |      |
| TC-OBS-202 | 정상 | 신청의 FinFlow 결과: 접수 / 같은 키 재전송 / 진행 중 신청(409) / 응답 없음, 서버 확인에서 막힌 신청(400) | 지표 기록 `RECEIVED` / `REPLAYED` / `CONFLICT` / `UNAVAILABLE` (상품별), 400은 기록 없음                                                                                                                  | P2   | LoanApplicationServiceTest                                                                      |      |
| TC-OBS-203 | 정상 | 업무 지표가 이름·속성 그대로 나감 (메모리 OTel)                                                          | `hfm.riskwatch.evaluations{status}`, `hfm.loan.applications{product_code, outcome}` 1씩, 기록하지 않은 조합은 시작부터 0(상태 3, 상품 3 × 결과 6)                                                         | P2   | BusinessMetricsTest                                                                             |      |
| TC-OBS-301 | 정상 | 관측 compose로 실서버 통합 분석 1번 (`X-Trace-Id` 지정)                                                  | Tempo에서 hfm 번호로 추적 1건, 백엔드 요청·DB·RiskWatch 재시도·Python 요청 span이 한 추적, Loki에서 hfm 번호로 로그, 지표 수집                                                                            | P2   | 자동화 안 함: Docker 관측 스택이 필요해 PR마다 실서버에서 확인 (README "관측 실행")             | #90  |
| TC-OBS-302 | 정상 | 관측·통합 compose로 실서버 통합 분석 1번, 신청 1번, FinFlow 운영자 심사 시작 1번 (`X-Trace-Id` 지정)     | 통합 분석: HomeFin·RiskWatch·Python span이 한 추적. 신청: HomeFin·FinFlow span이 한 추적. 심사 시작: FinFlow·RiskWatch span이 한 추적. 모두 `hfm.trace_id`로 찾고, Loki에서 같은 traceId로 세 서비스 로그 | P2   | 자동화 안 함: Docker 관측 스택과 세 저장소가 필요해 PR마다 실서버에서 확인 (README "관측 실행") |      |
| TC-OBS-303 | 정상 | 관측·통합 compose: Grafana 자동 등록, 요청 몇 번 뒤 대시보드 패널, RiskWatch를 멈추고 통합 분석          | 대시보드 1개·알림 규칙 등록, 모든 패널 쿼리에 값, "RiskWatch 평가 불가" 알림 Firing → RiskWatch 다시 켜면 Normal                                                                                          | P2   | 자동화 안 함: Docker 관측 스택과 세 저장소가 필요해 PR마다 실서버에서 확인                      |      |
