# 성능 측정 도구

[성능 측정 계획과 결과](../docs/performance.md)의 수치를 재현하는 방법입니다.
애플리케이션 코드는 바꾸지 않고, compose 덮어쓰기 파일로 측정 조건만 바꿉니다.

## 구성

| 파일                      | 역할                                                                                                      |
| ------------------------- | --------------------------------------------------------------------------------------------------------- |
| `docker-compose.perf.yml` | 가짜 RiskWatch 추가, 백엔드가 가짜 RiskWatch를 부르게 함, 컨테이너 CPU·메모리 제한, actuator metrics 공개 |
| `fake-riskwatch/app.py`   | 응답 지연·실패를 조절할 수 있는 가짜 RiskWatch (`DELAY_SECONDS`, `MODE`: ok / error / html502)                                |
| `k6/s1-riskwatch-down.js` | S1: RiskWatch가 응답하지 않을 때 통합 분석 응답 시간                                                      |
| `k6/s2-connection-hold.js` | S2a: 느린 분석 20건이 도는 동안 리포트·로그인 응답 시간·오류율, Hikari 사용·대기 수 |
| `k6/s2-inconsistency.js` | S2b: 분석이 실패했는데 계산이 저장된 건수 (가짜 RiskWatch `MODE=html502`) |
| `results/`                | k6 원본 결과(JSON). 커밋하지 않음                                                                         |

k6는 PC에 설치하지 않고 Docker 이미지(`grafana/k6:0.54.0`)로 실행합니다.

## 1. 측정 환경 띄우기

저장소 루트에서 실행합니다. `FAKE_RISKWATCH_DELAY`는 가짜 RiskWatch의 응답 지연(초)입니다.

```bash
FAKE_RISKWATCH_DELAY=60 docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml up -d --build
```

| 컨테이너       | 제한                |
| -------------- | ------------------- |
| backend        | CPU 2, 메모리 1GB   |
| mysql          | CPU 2, 메모리 1GB   |
| python-service | CPU 1, 메모리 512MB |

## 2. 시나리오 실행 (Git Bash 기준)

```bash
export MSYS_NO_PATHCONV=1   # Git Bash가 /scripts 같은 경로를 Windows 경로로 바꾸지 않게 함
K="docker run --rm --network homefin-local_default -v $(pwd -W)/perf/k6:/scripts:ro -v $(pwd -W)/perf/results:/results grafana/k6:0.54.0 run --quiet"

# 워밍업 (결과는 쓰지 않음)
$K -e VUS=5 -e DURATION=30s -e RUN=warmup /scripts/s1-riskwatch-down.js

# 본 측정 3번
for r in 1 2 3; do $K -e VUS=5 -e DURATION=3m -e RUN=base$r /scripts/s1-riskwatch-down.js; done
```

- 네트워크 이름 `homefin-local_default`는 `.env`의 `COMPOSE_PROJECT_NAME` + `_default`입니다. 다르면 `docker network ls`로 확인합니다.
- k6는 같은 Docker 네트워크 안에서 `http://backend:8100`을 직접 부릅니다.
- 결과 한 줄 요약이 출력되고, 원본은 `perf/results/s1-<RUN>.json`에 저장됩니다.

### S2 실행

```bash
# S2a: 가짜 RiskWatch 지연 60초 상태에서
$K -e DURATION=2m -e RUN=base1 /scripts/s2-connection-hold.js

# S2b: 가짜 RiskWatch를 "지연 없이 502 + 긴 HTML" 모드로 바꾼 뒤
FAKE_RISKWATCH_MODE=html502 FAKE_RISKWATCH_DELAY=0 docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml up -d fake-riskwatch
$K -e N=20 -e RUN=base1 /scripts/s2-inconsistency.js

# 끝나면 가짜 RiskWatch를 기본값(지연 60초, ok)으로 되돌림
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml up -d fake-riskwatch
```

## 3. Hikari 커넥션 지표 보기

actuator `metrics`는 로그인이 필요합니다. 아무 계정으로 로그인한 토큰을 붙입니다.

```bash
curl -H "Authorization: Bearer <토큰>" http://localhost:8100/actuator/metrics/hikaricp.connections.active
```

## 4. 측정 끝난 뒤 원래 설정으로

```bash
docker compose up -d --build   # 덮어쓰기 파일 없이 다시 띄우면 원래 설정
docker compose stop fake-riskwatch
```
