from typing import Optional
from fastapi import HTTPException
from schemas.analysis import RegionAnalysisResponse

# 지역별 예시 데이터 (실제 시세 아님, 출처 없음, 가격 단위 모름)
MOCK_REGION_DATA = {
    "서울":      {"average_price": 980000.0,  "growth_rate": 0.058, "risk_score": 2.6},
    "부산":      {"average_price": 540000.0,  "growth_rate": 0.072, "risk_score": 2.3},
    "대구":      {"average_price": 420000.0,  "growth_rate": 0.065, "risk_score": 2.4},
    "인천":      {"average_price": 490000.0,  "growth_rate": 0.069, "risk_score": 2.5},
    "광주":      {"average_price": 360000.0,  "growth_rate": 0.060, "risk_score": 2.7},
}

# 지역 분석 (앞뒤 공백·대소문자 무시, 없는 지역은 404, traceId 그대로 전달)
def get_region_analysis(region: str, trace_id: Optional[str] = None) -> RegionAnalysisResponse:
    """
    지역의 부동산 시장 분석 데이터를 조회합니다.

    지역 이름은 앞뒤 공백과 대소문자를 무시하고 찾습니다. 목록에 없는 지역이면
    404를 돌려줍니다 (다른 지역 값으로 대체하지 않음).
    trace_id는 서비스 간 추적을 위해 응답에 그대로 전달합니다.
    """
    region_key = region.lower().strip()
    data = MOCK_REGION_DATA.get(region_key)
    if data is None:
        raise HTTPException(status_code=404, detail="Unsupported region")

    return RegionAnalysisResponse(
        region=region,
        average_price=data["average_price"],
        growth_rate=data["growth_rate"],
        risk_score=data["risk_score"],
        sample=True,
        trace_id=trace_id,
    )
