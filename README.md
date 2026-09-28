# HomeFin Mate

주택담보대출을 계획할 수 있도록 돕고 맞춤 정책을 추천해주는 서비스

사용자가 대출 조건을 입력하면 월 상환액과 DSR(총부채원리금상환비율)을 계산하고
받을 수 있는 정책을 추천하며 계산 이력을 저장해 대시보드와 리포트로 보여줍니다.

## 주요 기능

- [ ] 회원가입 / 로그인 (JWT 인증)
- [ ] 대출 계산 (원리금 균등상환, DSR)
- [ ] 정책 추천
- [ ] 지역 부동산 분석 (Python 서비스)
- [ ] 통합 분석 (대출 + 정책 + 리스크 + 지역)
- [ ] 대시보드 / 리포트
- [ ] 관리자 화면

---

## 기술 스택

| 구분     | 기술                                                            |
| -------- | --------------------------------------------------------------- |
| Backend  | Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, JWT |
| Frontend | React, Vite, Tailwind CSS, axios                                |
| Python   | FastAPI                                                         |
| Database | MySQL 8                                                         |
| Infra    | Docker, docker-compose                                          |

---

## 프로젝트 구조

```
homefin-mate/
├── backend/          Spring Boot  :8100
├── frontend/         React + Vite :3100
├── python-service/   FastAPI      :8200
└── docker-compose.yml
```

---

## 실행 방법

(작성 예정)
