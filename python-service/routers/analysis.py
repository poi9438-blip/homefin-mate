from fastapi import APIRouter, Query, Header
from typing import Optional
from services.analysis_service import get_region_analysis
from schemas.analysis import RegionAnalysisResponse

router = APIRouter()

# 지역 분석 조회 (region 필수, traceId 헤더 선택)
@router.get(
    "/region",
    response_model=RegionAnalysisResponse,
    summary="지역 부동산 시장 분석",
    description=(
        "지정한 지역의 부동산 시장 데이터를 돌려줍니다. "
        "호주 도시(Sydney, Melbourne, Brisbane, Perth, Adelaide, Darwin, Hobart)와 "
        "한국 도시(서울, 부산, 대구, 인천, 광주)를 지원합니다. "
        "목록에 없는 지역은 기본 시장 데이터를 돌려줍니다. "
        "X-Trace-Id 헤더를 보내면 같은 값이 응답의 trace_id로 전달됩니다."
    ),
)
async def region_analysis(
    region: str = Query(
        ...,
        description="지역 이름 (예: 'Brisbane', '부산')",
        example="부산",
    ),
    x_trace_id: Optional[str] = Header(None, alias="X-Trace-Id"),
):
    return get_region_analysis(region, trace_id=x_trace_id)
