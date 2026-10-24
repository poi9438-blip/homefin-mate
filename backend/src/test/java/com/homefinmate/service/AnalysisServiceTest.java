package com.homefinmate.service;

import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// AnalysisService 동작 검증 (Python 응답 변환, 한글 URL, 분석 불가 처리)
class AnalysisServiceTest {

    private MockRestServiceServer server;
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        analysisService = new AnalysisService(restTemplate);
        ReflectionTestUtils.setField(analysisService, "pythonServiceBaseUrl", "http://python.test");
    }

    // 한글 지역명은 UTF-8 퍼센트 인코딩으로 보냄 (부산 → %EB%B6%80%EC%82%B0)
    @Test
    @DisplayName("[TC-REGION-001] 한글 지역명을 URL 인코딩해서 보냄 (#2 해결)")
    void koreanRegion_isUrlEncoded() {
        server.expect(request -> assertThat(request.getURI().toString())
                .isEqualTo("http://python.test/analysis/region?region=%EB%B6%80%EC%82%B0"))
                .andRespond(withSuccess("{\"region\":\"부산\"}", MediaType.APPLICATION_JSON));

        analysisService.getRegionAnalysis("부산", null);

        server.verify();
    }

    // Python 응답(snake_case)의 값이 Java 응답에 채워지고, 예시 데이터 표시와 traceId도 전달
    @Test
    @DisplayName("[TC-REGION-008] Python 응답(snake_case)의 값·예시 여부·traceId가 Java 응답에 채워짐 (#2 해결)")
    void pythonSnakeCaseResponse_isMapped() {
        server.expect(header("X-Trace-Id", "hfm-region-1"))
                .andRespond(withSuccess("""
                        {"region":"부산","average_price":540000.0,"growth_rate":0.072,
                         "risk_score":2.3,"sample":true,"trace_id":"hfm-region-1"}
                        """, MediaType.APPLICATION_JSON));

        RegionAnalysisResponse res = analysisService.getRegionAnalysis("부산", "hfm-region-1");

        assertThat(res.isAvailable()).isTrue();
        assertThat(res.getAveragePrice()).isEqualByComparingTo("540000");
        assertThat(res.getGrowthRate()).isEqualByComparingTo("0.072");
        assertThat(res.getRiskScore()).isEqualByComparingTo("2.3");
        assertThat(res.getSample()).isTrue();
        assertThat(res.getTraceId()).isEqualTo("hfm-region-1");
        server.verify();
    }

    // Python에 없는 지역(404)은 다른 지역 값으로 대체하지 않고 분석 불가 (#40)
    @Test
    @DisplayName("[TC-REGION-009] Python이 404(없는 지역)면 값 없이 available=false, UNSUPPORTED_REGION")
    void unknownRegion_isUnavailable() {
        server.expect(request -> {
        })
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"Unsupported region\"}"));

        RegionAnalysisResponse res = analysisService.getRegionAnalysis("부상", "t-1");

        assertThat(res.isAvailable()).isFalse();
        assertThat(res.getUnavailableReason()).isEqualTo("UNSUPPORTED_REGION");
        assertThat(res.getRegion()).isEqualTo("부상");
        assertThat(res.getAveragePrice()).isNull();
        assertThat(res.getGrowthRate()).isNull();
        assertThat(res.getRiskScore()).isNull();
    }

    // Python 연결 실패는 값 없이 분석 불가 (지어낸 숫자를 보내지 않음)
    @Test
    @DisplayName("[TC-REGION-010] Python 연결 실패면 값 없이 available=false, SERVICE_UNAVAILABLE (임의 숫자 없음)")
    void connectionFailure_isUnavailableWithoutNumbers() {
        server.expect(request -> {
        }).andRespond(withException(new IOException("connection refused")));

        RegionAnalysisResponse res = analysisService.getRegionAnalysis("서울", "t-2");

        assertThat(res.isAvailable()).isFalse();
        assertThat(res.getUnavailableReason()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(res.getAveragePrice()).isNull();
        assertThat(res.getTraceId()).isEqualTo("t-2");
    }
}
