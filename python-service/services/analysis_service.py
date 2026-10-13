from typing import Optional
from schemas.analysis import RegionAnalysisResponse

# [학습] 지역별 시세 데이터가 코드에 하드코딩된 "예시(mock) 데이터"예요. 실제 시세가 아니에요.
#        ⚠️ 원본 그대로 둔 문제 (새로 발견): 출처가 없고, 단위도 불분명해요.
#           호주 도시는 호주 달러로 보이는데, 한국 도시(서울 980,000)도 같은 단위라 원화로는 말이 안 돼요.
#           화면에 실제 시세처럼 보이면 사용자를 오도할 수 있어요.
#           Phase 13에서 공식 출처(한국부동산원 등) 데이터로 바꾸거나 "예시 데이터"라고 명확히 표시해야 해요.
#        ⚠️ 원본 그대로 둔 문제 (수정 목록 #9): 호주 도시가 섞여 있어요.
# [추천 주석] 지역별 예시 시세 데이터 (실제 시세 아님, 출처 없음)
MOCK_REGION_DATA = {
    "default":   {"average_price": 750000.0,  "growth_rate": 0.045, "risk_score": 3.2},
    # 호주 도시
    "sydney":    {"average_price": 1200000.0, "growth_rate": 0.038, "risk_score": 2.8},
    "melbourne": {"average_price": 950000.0,  "growth_rate": 0.042, "risk_score": 3.0},
    "brisbane":  {"average_price": 750000.0,  "growth_rate": 0.061, "risk_score": 2.5},
    "perth":     {"average_price": 680000.0,  "growth_rate": 0.055, "risk_score": 2.9},
    "adelaide":  {"average_price": 620000.0,  "growth_rate": 0.048, "risk_score": 3.1},
    "darwin":    {"average_price": 520000.0,  "growth_rate": 0.031, "risk_score": 3.5},
    "hobart":    {"average_price": 590000.0,  "growth_rate": 0.035, "risk_score": 3.3},
    # 한국 도시 (고성장 지역)
    "서울":      {"average_price": 980000.0,  "growth_rate": 0.058, "risk_score": 2.6},
    "부산":      {"average_price": 540000.0,  "growth_rate": 0.072, "risk_score": 2.3},
    "대구":      {"average_price": 420000.0,  "growth_rate": 0.065, "risk_score": 2.4},
    "인천":      {"average_price": 490000.0,  "growth_rate": 0.069, "risk_score": 2.5},
    "광주":      {"average_price": 360000.0,  "growth_rate": 0.060, "risk_score": 2.7},
}

# 지역 분석 (대소문자 무시, 없는 지역은 default 값, traceId 그대로 전달)
def get_region_analysis(region: str, trace_id: Optional[str] = None) -> RegionAnalysisResponse:
    """
    지역의 부동산 시장 분석 데이터를 조회합니다.

    지역 이름은 대소문자를 구분하지 않고 찾습니다. 목록에 없는 지역이면
    'default' 지역 데이터를 돌려줍니다.
    trace_id는 서비스 간 추적을 위해 응답에 그대로 전달합니다.
    """
    region_key = region.lower().strip()
    data = MOCK_REGION_DATA.get(region_key, MOCK_REGION_DATA["default"])

    return RegionAnalysisResponse(
        region=region,
        average_price=data["average_price"],
        growth_rate=data["growth_rate"],
        risk_score=data["risk_score"],
        trace_id=trace_id,
    )
