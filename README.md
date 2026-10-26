# HomeFin Mate

주택담보대출을 계획할 수 있도록 돕고 맞춤 정책을 추천해주는 서비스

사용자가 대출 조건을 입력하면 월 상환액과 DTI(총부채상환비율)를 계산하고
받을 수 있는 정책을 추천하며 계산 이력을 저장해 대시보드와 리포트로 보여줍니다.

> 이 저장소는 직접 만든 HomeFin Mate를 **처음부터 다시 구현하며 문제를 찾아 개선한 기록**입니다.
> 기존 동작을 단계별로 다시 구현하면서 테스트와 실제 요청으로 문제를 확인했고, 찾은 문제는 [알려진 문제 목록](docs/known-issues.md)에 정리해 순서대로 고치고 있습니다.
> 단계마다 PR을 나누어 merge 했고 PR 본문에 구현 내용, 확인 결과, 발견한 문제를 남겼습니다.

---

## 주요 기능

- [x] 회원가입 / 로그인 (JWT 인증, 소득 AES-256-GCM 암호화 저장)
- [x] 대출 계산 (원리금 균등상환, DTI = (월 상환액 + 기존 대출 월 이자) ÷ 월 소득)
- [x] 정책대출 판정·추천 (디딤돌·신생아 특례 디딤돌·보금자리론 조건별 판정, 공식 기본금리로 비교)
- [x] 외부 시스템 연동 (RiskWatch 위험 평가, 타임아웃·재시도 + 서킷 브레이커 + fallback)
- [x] 지역 부동산 분석 (Python 서비스)
- [x] 통합 분석 (대출 + 정책 + 리스크 + 지역을 한 번에)
- [x] 대시보드 / 리포트
- [x] 관리자 화면 (감사 기록, 로그인 실패, 이용 기록, 연동 실패)
- [x] 다크 모드 / 큰 글씨 모드
- [x] Docker Compose 실행

---

## 기술 스택

| 구분     | 기술                                                                                             |
| -------- | ------------------------------------------------------------------------------------------------ |
| Backend  | Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, JWT(jjwt 0.12), Resilience4j         |
| Frontend | React 18, Vite 8, Tailwind CSS 4, React Router 7, axios, Chart.js (Node 22.12 이상)              |
| Python   | FastAPI, Pydantic, Uvicorn                                                                       |
| Database | MySQL 8 (테스트는 H2 메모리 DB + Testcontainers MySQL 8.0)                                       |
| Test     | JUnit 5, Mockito, AssertJ, MockRestServiceServer, JaCoCo, pytest, Vitest                         |
| CI/CD    | GitHub Actions: PR·main마다 테스트, main CI 성공 시 Docker 이미지 → GHCR (서버 배포는 아직 없음) |
| Infra    | Docker, Docker Compose, nginx                                                                    |

---

## 구조

```
homefin-mate/
├── backend/            Spring Boot API             :8100
│   └── src/main/java/com/homefinmate/
│       ├── controller/   API (auth, loan, policy, analyze, analysis, report, admin)
│       ├── service/      비즈니스 로직
│       ├── integration/  외부 시스템 호출 (RiskWatchClient)
│       ├── security/     JWT 발급·검증, 인증 필터
│       ├── entity/ repository/ dto/ config/ util/
├── frontend/           React + Vite (Docker에서는 nginx) :3100
├── python-service/     FastAPI 지역 분석 서비스   :8200
├── docker-compose.yml  mysql + backend + python-service + frontend
└── .env.example        환경변수 예시 (.env로 복사해서 사용)
```

### 서비스 간 흐름

```
브라우저 ──▶ frontend(:3100) ──/api──▶ backend(:8100) ──▶ MySQL(:3310)
                                          │
                                          ├──▶ python-service(:8200)   지역 분석 (예시 데이터, 실패 시 분석 불가)
                                          └──▶ RiskWatch(:8120)        위험 평가 (재시도 후 fallback)
```

- 아직 **RiskWatch 서버가 없습니다.** 위험 평가는 항상 연결 실패 → fallback(`available=false`) 경로로 동작합니다.
- 요청마다 `traceId`(`X-Trace-Id` 헤더 하나가 기준)를 백엔드 로그(MDC), 응답, DB 기록, RiskWatch·Python 호출 헤더에 함께 넘겨서 한 번의 분석을 끝까지 추적할 수 있습니다.
- 관측 compose를 함께 띄우면 OpenTelemetry로 추적·로그·지표를 Grafana에서 봅니다. 요청 span에 `hfm.trace_id`가 붙어 화면에 보인 번호로 추적을 찾을 수 있습니다 ([관측 실행](#4-관측-실행-선택)).

---

## 실행 방법

### 0. 환경변수 준비

```bash
cp .env.example .env
```

`.env`의 비밀값을 직접 정한 값으로 채웁니다. **비밀값에는 기본값이 없어서, 비어 있으면 서버와 compose가 시작되지 않습니다.**

| 변수                                    | 설명                                                                                                  |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | MySQL 비밀번호                                                                                        |
| `ENCRYPTION_KEY`                        | 암호화 키 (UTF-8 정확히 32바이트, 아니면 서버가 시작되지 않음. 바꾸면 기존 암호문을 복호화할 수 없음) |
| `JWT_SECRET`                            | 토큰 서명 키 (32바이트 이상)                                                                          |
| `JWT_EXPIRATION_MS`                     | 토큰·로그인 쿠키 유효 기간 (선택, 기본 24시간)                                                        |
| `RISKWATCH_BASE_URL`                    | RiskWatch 주소 (선택, 없으면 fallback)                                                                |
| `ADMIN_EMAILS`                          | 관리자 이메일 (선택, 쉼표로 여러 개). 가입하면 ADMIN, 이미 가입했으면 서버 시작 시 ADMIN              |
| `COOKIE_SECURE`                         | 로그인 쿠키 Secure 속성 (선택. 로컬 http compose는 기본 false, HTTPS로 운영하면 true)                 |

### 1. Docker Compose로 전체 실행

```bash
docker compose up -d --build
```

| 주소                                  | 내용                                                     |
| ------------------------------------- | -------------------------------------------------------- |
| http://localhost:3100                 | 화면 (nginx)                                             |
| http://localhost:8100/swagger-ui.html | 백엔드 API 문서 (`.env`에 `SWAGGER_ENABLED=true`일 때만) |
| http://localhost:8200/docs            | Python 서비스 API 문서                                   |

화면(3100)만 다른 PC에서 접속할 수 있고, 백엔드(8100)·Python(8200)·MySQL(3310)은 이 PC(`127.0.0.1`)에서만 열립니다. 컨테이너는 모두 root가 아닌 사용자로 실행되고, 화면은 백엔드·Python이 준비(healthy)된 뒤 시작합니다.

DB 테이블은 백엔드가 시작할 때 Flyway(`backend/src/main/resources/db/migration`)가 만들고 바꿉니다. 테이블을 바꿀 때는 기존 파일을 고치지 말고 다음 버전 파일(예: `V7__설명.sql`)을 추가합니다. Flyway 도입 전에 만든 DB는 V1을 건너뛰고 V2부터 실행합니다.

### 2. 로컬 개발 실행

코드를 고치면서 확인할 때 씁니다. MySQL만 Docker로 띄우고, 나머지 세 개는 **각각 다른 터미널에서 저장소 루트 기준으로** 실행합니다.
1번으로 띄운 컨테이너가 켜져 있으면 포트(8100, 8200, 3100)가 겹치므로 먼저 끕니다.

```bash
# 1번 컨테이너가 켜져 있다면 (MySQL은 그대로 둠)
docker compose stop backend python-service frontend

# MySQL (호스트 3310)
docker compose up -d mysql
```

**백엔드** (8100) — 저장소 루트의 `.env`를 자동으로 읽습니다. Maven Wrapper(`mvnw`)가 Maven 3.9.9를 받아 쓰므로 Maven 설치는 필요 없습니다 (Java 17은 필요).

```bash
cd backend
./mvnw spring-boot:run      # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

**Python 서비스** (8200) — 처음 한 번만 가상환경을 만들고 설치합니다.

```powershell
cd python-service
python -m venv .venv
.venv\Scripts\Activate.ps1          # cmd: .venv\Scripts\activate.bat / Git Bash: source .venv/Scripts/activate / macOS·Linux: source .venv/bin/activate
pip install -r requirements-dev.txt   # 실행 + 테스트 도구 (Docker 이미지는 requirements.txt만)
uvicorn main:app --port 8200
```

> PowerShell에서 `Activate.ps1`이 "스크립트를 실행할 수 없습니다"로 막히면, 현재 창에서만 허용합니다: `Set-ExecutionPolicy -Scope Process RemoteSigned`

**프론트** (3100) — 포트와 `/api` 프록시(→ localhost:8100)는 `vite.config.js`에 설정되어 있습니다.

```bash
cd frontend
npm install
npm run dev
```

> `python-service/requirements.txt`에는 한글을 넣지 않습니다. 한국어 Windows의 pip(Python 3.10)가 이 파일을 cp949로 읽어서 한글이 있으면 설치에 실패합니다.

### 3. 테스트

외부 서버 없이 실행됩니다. 각각 저장소 루트 기준입니다. 기준과 절차는 [테스트 기준](docs/TESTING.md)에 있습니다.

```bash
# 백엔드 (대부분 H2 메모리 DB, MySqlSchemaTest만 Docker로 MySQL 컨테이너 사용 — Docker가 꺼져 있으면 건너뜀). 커버리지: backend/target/site/jacoco/index.html
cd backend
./mvnw test
```

```bash
# Python (가상환경을 활성화한 상태에서)
cd python-service
python -m pytest -q --junitxml=test-results/pytest.xml
```

```bash
# 프론트 (Vitest + jsdom)
cd frontend
npm test
```

```bash
# 세 모듈을 실행한 뒤, 테스트 명세(docs/test-cases)와 실행 결과 대조
python scripts/tc_status.py
```

PR과 main push마다 GitHub Actions([`.github/workflows/ci.yml`](.github/workflows/ci.yml))가 위 네 단계를 실행합니다.
main에서 CI가 성공하면 [`.github/workflows/release.yml`](.github/workflows/release.yml)이 backend·frontend·python-service 이미지를 만들어
`ghcr.io/poi9438-blip/homefin-<서비스>:<커밋 번호>`와 `:latest`로 올립니다. 서버 배포는 아직 하지 않습니다.

### 4. 관측 실행 (선택)

기본 실행에는 관측 도구가 없습니다. 필요할 때만 관측 compose를 겹쳐 띄웁니다. 백엔드는 OTel Java 에이전트, Python은 `opentelemetry-instrument`가 든 이미지(`otel` 빌드 단계)로 바뀝니다.

```bash
docker compose -f docker-compose.yml -f observability/docker-compose.observability.yml up -d --build
```

| 주소                  | 내용                                                                  |
| --------------------- | --------------------------------------------------------------------- |
| http://localhost:3000 | Grafana (`grafana/otel-lgtm`: Tempo 추적, Loki 로그, Prometheus 지표) |

- **hfm 번호로 추적 찾기:** Explore → Tempo → TraceQL `{span.hfm.trace_id="hfm-…"}`. 백엔드 요청, DB 쿼리, RiskWatch 재시도, Python 요청이 한 추적에 보입니다.
- **hfm 번호로 로그 찾기:** Explore → Loki → `{service_name="homefin-backend"} | traceId="hfm-…"`. 로그 줄에서 OTel 추적으로 바로 이동할 수 있습니다.
- 관측 없이 다시 실행할 때는 `docker compose up -d --build`로 기본 이미지로 돌아옵니다(관측 이미지는 `…-otel` 이름이라 섞이지 않음).

---

## API

| 메서드 | 주소                                                                           | 인증   | 설명                                                                              |
| ------ | ------------------------------------------------------------------------------ | ------ | --------------------------------------------------------------------------------- |
| POST   | `/api/auth/signup`                                                             | -      | 회원가입                                                                          |
| POST   | `/api/auth/login`                                                              | -      | 로그인 (토큰은 HttpOnly 쿠키로만, 실패 기록·성공 감사 기록, 15분 5번 실패 시 429) |
| GET    | `/api/auth/me`                                                                 | 로그인 | 현재 사용자 이메일·역할 (DB 기준)                                                 |
| POST   | `/api/auth/logout`                                                             | -      | 로그아웃 (로그인 쿠키 삭제)                                                       |
| POST   | `/api/loan/calculate`                                                          | 로그인 | 대출 계산 + 저장                                                                  |
| GET    | `/api/loan/history`                                                            | 로그인 | 내 계산 이력                                                                      |
| POST   | `/api/loan/dti`                                                                | -      | DTI 단독 계산 (저장 안 함)                                                        |
| POST   | `/api/policy/recommend`                                                        | 로그인 | 정책대출 판정·추천 (조건별 판정, 기본금리 비교)                                   |
| GET    | `/api/analysis/region`                                                         | -      | 지역 분석 (Python 서비스 호출)                                                    |
| POST   | `/api/analyze`                                                                 | 로그인 | 통합 분석 (대출 → 정책 → 위험 → 지역)                                             |
| GET    | `/api/report/summary`                                                          | 로그인 | 내 계산 요약 리포트                                                               |
| GET    | `/api/admin/{audit-logs, login-failures, user-activity, integration-failures}` | ADMIN  | 관리자 기록 조회 (각 최근 100건)                                                  |

응답은 모두 `{ "status": "SUCCESS" | "ERROR", "message", "data", "traceId" }` 형식입니다.

---

## 보안 관련 구현

- 비밀번호 BCrypt 해시, 계산 기록의 DTI는 AES-256-GCM 암호문, 로그인 실패 이메일은 HMAC
- JWT(HS256) 인증, 세션 미사용(STATELESS), 401/403 JSON 응답
- 토큰은 HttpOnly·SameSite=Strict 쿠키로만 전달(화면 자바스크립트가 읽을 수 없음), 쿠키 인증 요청은 XSRF 토큰(이중 제출)으로 CSRF 방어
- 관리자 API는 주소 규칙(`hasRole("ADMIN")`)과 메서드 권한(`@PreAuthorize`) 두 겹으로 검사
- 비밀값(DB 비밀번호, 암호화 키, JWT 키)은 기본값 없이 `.env`에서만 주입
- 로그인 실패 시 이메일·비밀번호 중 무엇이 틀렸는지 알려주지 않음 (계정 존재 여부 노출 방지)

---

## 성능 개선 (측정 기반)

로컬 Docker에서 외부 시스템 장애와 데이터 누적을 재현하고 k6로 측정한 뒤, 같은 조건으로 다시 측정했습니다 (3회 중앙값). 자세한 조건은 [docs/performance.md](docs/performance.md)에 있습니다.

| 상황                                                    | 개선 전                       | 개선 후                                                       | 한 일                                     |
| ------------------------------------------------------- | ----------------------------- | ------------------------------------------------------------- | ----------------------------------------- |
| 외부 시스템(RiskWatch)이 멈춘 동안 무관한 로그인·리포트 | 64% 실패 (DB 커넥션 고갈)     | 실패 0%, p95 약 0.1초                                         | 타임아웃, OSIV 끄기, 저장만 짧은 트랜잭션 |
| 감사 기록 100만 건에서 관리자 조회                      | 메모리 부족으로 3번 모두 실패 | 3번 모두 0.2초 안팎 성공                                      | DB에서 100건만 조회 + 인덱스              |
| RiskWatch 장애가 이어질 때                              | 분석마다 타임아웃까지 대기    | 서킷 브레이커로 바로 대체 응답, RiskWatch 호출 445 → 30건/3분 | Resilience4j                              |

---

## 문제

다시 구현하면서 테스트와 실제 요청으로 확인한 문제를 번호로 정리했습니다. 수정할 때마다 해당 PR을 기록합니다.

- [docs/known-issues.md](docs/known-issues.md)
- 성능 측정 계획과 결과: [docs/performance.md](docs/performance.md)
- 테스트 기준·명세·결과: [docs/TESTING.md](docs/TESTING.md), [docs/test-cases/](docs/test-cases/README.md), [docs/TEST_REPORT.md](docs/TEST_REPORT.md)
