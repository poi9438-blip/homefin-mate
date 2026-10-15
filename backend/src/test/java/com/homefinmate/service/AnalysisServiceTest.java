package com.homefinmate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// AnalysisService 동작 검증 (Python 응답 변환, 한글 URL, fallback)
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

    // TODO: 수정 필요
    // 한글 지역명이 URL 인코딩 없이 나감
    @Test
    @DisplayName("[TC-REGION-001] ⚠️#2 한글 지역명이 URL 인코딩 없이 요청 주소에 들어감")
    void koreanRegion_isNotUrlEncoded() {
        server.expect(request -> assertThat(request.getURI().toString())
                .isEqualTo("http://python.test/analysis/region?region=부산"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        analysisService.getRegionAnalysis("부산", null);

        server.verify();
    }
}
