from fastapi.testclient import TestClient
from main import app

# 지역 분석 API 테스트
client = TestClient(app)


# [TC-REGION-002] 상태 확인
def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "python-analysis-service"}


# [TC-REGION-003] 부산: 등록된 값 그대로, snake_case 필드
def test_busan_returns_registered_values():
    response = client.get("/analysis/region", params={"region": "부산"})
    assert response.status_code == 200
    assert response.json() == {
        "region": "부산",
        "average_price": 540000.0,
        "growth_rate": 0.072,
        "risk_score": 2.3,
        "trace_id": None,
    }


# [TC-REGION-004] 대소문자, 앞뒤 공백 무시 (region은 입력값 그대로 돌려줌)
def test_case_and_whitespace_insensitive():
    data = client.get("/analysis/region", params={"region": " Sydney "}).json()
    assert data["average_price"] == 1200000.0
    assert data["region"] == " Sydney "


# [TC-REGION-005] X-Trace-Id 헤더 값을 응답 trace_id로 돌려줌
def test_trace_id_header_is_propagated():
    data = client.get("/analysis/region", params={"region": "서울"},
                      headers={"X-Trace-Id": "hfm-region-1"}).json()
    assert data["trace_id"] == "hfm-region-1"


# [TC-REGION-006] region 누락 => 422
def test_missing_region_returns_422():
    response = client.get("/analysis/region")
    assert response.status_code == 422

# TODO: 수정 필요
# [TC-REGION-007] 없는 지역(오타)도 default 값으로 200, 대체 여부 표시 없음
def test_unknown_region_silently_uses_default():
    response = client.get("/analysis/region", params={"region": "부상"})
    assert response.status_code == 200
    data = response.json()
    assert data["region"] == "부상"
    assert data["average_price"] == 750000.0
    assert set(data.keys()) == {"region", "average_price", "growth_rate", "risk_score", "trace_id"}
