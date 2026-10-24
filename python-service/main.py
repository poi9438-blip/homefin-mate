from fastapi import FastAPI
from routers import analysis

# Python 지역 분석 서비스 (포트 8200, 문서 /docs)
app = FastAPI(
    title="HomeFin Mate — Python 지역 분석 서비스",
    description=(
        "HomeFin Mate의 지역 부동산 시장 데이터 마이크로서비스입니다. "
        "한국 지역의 예시 평균 가격, 성장률, 시장 위험 점수를 제공합니다 (실제 시세 아님). "
        "포트 8200에서 실행됩니다."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS 설정 없음 (브라우저에서 직접 호출하지 않는 내부 서비스)

# 지역 분석 라우터 등록 (/analysis)
app.include_router(analysis.router, prefix="/analysis", tags=["지역 분석"])

# 상태 확인
@app.get("/health", tags=["상태 확인"])
def health_check():
    """서비스 동작 확인. 서비스가 실행 중이면 status ok를 돌려줍니다."""
    return {"status": "ok", "service": "python-analysis-service"}
