from pydantic import BaseModel
from typing import Optional


# 지역 분석 응답 (지역, 평균 가격, 성장률, 위험 점수, traceId)
class RegionAnalysisResponse(BaseModel):
    region: str
    # 평균 주택 가격 (단위 불명확, 예시 데이터)
    average_price: float
    # 연 성장률 (%)
    growth_rate: float
    # 시장 위험 점수 (낮을수록 안전, 기준 불명확)
    risk_score: float
    trace_id: Optional[str] = None

    model_config = {"populate_by_name": True}
