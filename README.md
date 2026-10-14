# HomeFin Mate

주택담보대출을 계획할 수 있도록 돕고 맞춤 정책을 추천해주는 서비스

사용자가 대출 조건을 입력하면 월 상환액과 DSR(총부채원리금상환비율)을 계산하고
받을 수 있는 정책을 추천하며 계산 이력을 저장해 대시보드와 리포트로 보여줍니다.

> 이 저장소는 기존 프로젝트(finsight-system의 HomeFin Mate)를 **작은 단계로 다시 만들며 학습한 기록**입니다.
> 단계마다 PR을 나누어 merge 했고 PR 본문에 구현 내용, 확인 결과, 발견한 문제를 남겼습니다.

---

## 주요 기능

- [x] 회원가입 / 로그인 (JWT 인증, 소득 AES-256-GCM 암호화 저장)
- [x] 대출 계산 (원리금 균등상환, DSR)
- [x] 정책 추천 (점수 기반 상위 3개)
- [x] 외부 시스템 연동 (RiskWatch 위험 평가, 재시도 + fallback)
- [x] 지역 부동산 분석 (Python 서비스)
- [x] 통합 분석 (대출 + 정책 + 리스크 + 지역을 한 번에)
- [x] 대시보드 / 리포트
- [x] 관리자 화면 (감사 기록, 로그인 실패, 이용 기록, 연동 실패)
- [x] 다크 모드 / 큰 글씨 모드
- [x] Docker Compose 실행

---

## 기술 스택

| 구분     | 기술                                                                       |
| -------- | -------------------------------------------------------------------------- |
| Backend  | Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, JWT(jjwt 0.12) |
| Frontend | React 18, Vite 5, Tailwind CSS 3, React Router 6, axios, Chart.js          |
| Python   | FastAPI, Pydantic, Uvicorn                                                 |
| Database | MySQL 8 (테스트는 H2 메모리 DB)                                            |
| Test     | JUnit 5, Mockito, AssertJ, MockRestServiceServer, pytest                   |
| Infra    | Docker, Docker Compose, nginx                                              |

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
                                          ├──▶ python-service(:8200)   지역 분석 (실패 시 fallback)
                                          └──▶ RiskWatch(:8120)        위험 평가 (재시도 후 fallback)
```

- 아직 **RiskWatch 서버가 없습니다.** 위험 평가는 항상 연결 실패 → fallback(`available=false`) 경로로 동작합니다.
- 요청마다 `traceId`를 백엔드 로그(MDC), DB 기록, RiskWatch·Python 호출 헤더에 함께 넘겨서 한 번의 분석을 끝까지 추적할 수 있습니다.

---

## 실행 방법

### 0. 환경변수 준비

```bash
cp .env.example .env
```

`.env`의 비밀값을 직접 정한 값으로 채웁니다. **비밀값에는 기본값이 없어서, 비어 있으면 서버와 compose가 시작되지 않습니다.**

| 변수                                    | 설명                                                         |
| --------------------------------------- | ------------------------------------------------------------ |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | MySQL 비밀번호                                               |
| `ENCRYPTION_KEY`                        | 소득 암호화 키 (32자, 바꾸면 기존 암호문을 복호화할 수 없음) |
| `JWT_SECRET`                            | 토큰 서명 키 (32바이트 이상)                                 |
| `JWT_EXPIRATION_MS`                     | 토큰 유효 기간 (선택, 기본 24시간)                           |
| `RISKWATCH_BASE_URL`                    | RiskWatch 주소 (선택, 없으면 fallback)                       |

### 1. Docker Compose로 전체 실행

```bash
docker compose up -d --build
```

| 주소                                  | 내용                   |
| ------------------------------------- | ---------------------- |
| http://localhost:3100                 | 화면 (nginx)           |
| http://localhost:8100/swagger-ui.html | 백엔드 API 문서        |
| http://localhost:8200/docs            | Python 서비스 API 문서 |

### 2. 로컬 개발 실행

MySQL만 Docker로 띄우고 나머지는 각각 실행합니다.

```bash
# MySQL (호스트 3310)
docker compose up -d mysql

# 백엔드 (저장소 루트의 .env를 자동으로 읽음, mvnw가 없어 Maven 설치 필요)
cd backend
mvn spring-boot:run

# Python 서비스
cd python-service
python -m venv .venv
.venv\Scripts\activate            # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8200

# 프론트 (Vite가 /api를 localhost:8100으로 프록시)
cd frontend
npm install
npm run dev -- --port 3100
```

> `python-service/requirements.txt`에는 한글을 넣지 않습니다. 한국어 Windows의 pip(Python 3.10)가 이 파일을 cp949로 읽어서 한글이 있으면 설치에 실패합니다.

### 3. 테스트

```bash
cd backend && mvn test                         # 백엔드 (H2 메모리 DB, 외부 서버 불필요)
cd python-service && python -m pytest -q       # Python
```

---

## API

| 메서드 | 주소                                                                           | 인증   | 설명                                              |
| ------ | ------------------------------------------------------------------------------ | ------ | ------------------------------------------------- |
| POST   | `/api/auth/signup`                                                             | -      | 회원가입 (소득 암호화 저장)                       |
| POST   | `/api/auth/login`                                                              | -      | 로그인, JWT 발급 (실패 기록·성공 감사 기록)       |
| POST   | `/api/auth/logout`                                                             | -      | 로그아웃 (서버 동작 없음, 토큰은 프론트에서 삭제) |
| POST   | `/api/loan/calculate`                                                          | 로그인 | 대출 계산 + 저장                                  |
| GET    | `/api/loan/history`                                                            | 로그인 | 내 계산 이력                                      |
| POST   | `/api/loan/dsr`                                                                | -      | DSR 단독 계산 (저장 안 함)                        |
| POST   | `/api/policy/recommend`                                                        | -      | 정책 추천 (상위 3개)                              |
| GET    | `/api/analysis/region`                                                         | -      | 지역 분석 (Python 서비스 호출)                    |
| POST   | `/api/analyze`                                                                 | 로그인 | 통합 분석 (대출 → 정책 → 위험 → 지역)             |
| GET    | `/api/report/summary`                                                          | 로그인 | 내 계산 요약 리포트                               |
| GET    | `/api/admin/{audit-logs, login-failures, user-activity, integration-failures}` | ADMIN  | 관리자 기록 조회 (각 최근 100건)                  |

응답은 모두 `{ "status": "SUCCESS" | "ERROR", "message", "data", "traceId" }` 형식입니다.

---

## 보안 관련 구현

- 비밀번호 BCrypt 해시, 월 소득 AES-256-GCM 암호화 저장
- JWT(HS256) 인증, 세션 미사용(STATELESS), 401/403 JSON 응답
- 관리자 API는 주소 규칙(`hasRole("ADMIN")`)과 메서드 권한(`@PreAuthorize`) 두 겹으로 검사
- 비밀값(DB 비밀번호, 암호화 키, JWT 키)은 기본값 없이 `.env`에서만 주입
- 로그인 실패 시 이메일·비밀번호 중 무엇이 틀렸는지 알려주지 않음 (계정 존재 여부 노출 방지)
