from pydantic import BaseModel
from typing import Optional


# 지역 분석 응답 (지역, 평균 가격, 성장률, 위험 점수, traceId)
class RegionAnalysisResponse(BaseModel):
    region: str
    # 평균 주택 가격 (예시 데이터, 출처가 없어 단위 모름)
    average_price: float
    # 연 성장률 (%)
    growth_rate: float
    # 시장 위험 점수 (낮을수록 안전, 기준 불명확)
    risk_score: float
    # 예시 데이터 여부 (True: 실제 시세 아님, 출처 없음)
    sample: bool = True
    trace_id: Optional[str] = None
