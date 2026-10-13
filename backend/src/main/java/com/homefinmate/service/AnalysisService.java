package com.homefinmate.service;

import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

// Python 지역 분석 호출 (실패 시 Java fallback 데이터)
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final RestTemplate restTemplate;

    @Value("${app.python-service.base-url:http://localhost:8200}")
    private String pythonServiceBaseUrl;

    // 지역 분석 조회 (성공: Python 결과, 실패: fallback)
    public RegionAnalysisResponse getRegionAnalysis(String region, String traceId) {
        try {
            // 요청 주소 (base-url + /analysis/region?region=...)
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(pythonServiceBaseUrl + "/analysis/region")
                    .queryParam("region", region)
                    .build().toUri();

            // traceId 헤더 전달
            HttpHeaders headers = new HttpHeaders();
            if (traceId != null)
                headers.set("X-Trace-Id", traceId);

            // Python 호출 (응답을 DTO로 변환)
            ResponseEntity<RegionAnalysisResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers),
                    RegionAnalysisResponse.class);

            // 응답 본문이 있으면 그대로 반환
            RegionAnalysisResponse body = response.getBody();
            if (body != null) {
                log.info("[{}] Region analysis for '{}': price={} growth={} risk={}",
                        traceId, region, body.getAveragePrice(),
                        body.getGrowthRate(), body.getRiskScore());
                return body;
            }
        } catch (RestClientException e) {
            // 호출 실패: 경고 로그 후 fallback
            log.warn("[{}] Python service unavailable for region '{}' — using fallback: {}",
                    traceId, region, e.getMessage());
        }
        return buildFallback(region, traceId);
    }

    // fallback 데이터 (예시 데이터)
    private RegionAnalysisResponse buildFallback(String region, String traceId) {
        String key = region != null ? region.toLowerCase().trim() : "";
        return switch (key) {
            case "sydney" -> resp(region, 1_200_000, 0.038, 2.8, traceId);
            case "melbourne" -> resp(region, 950_000, 0.042, 3.0, traceId);
            case "brisbane" -> resp(region, 750_000, 0.061, 2.5, traceId);
            case "perth" -> resp(region, 680_000, 0.055, 2.9, traceId);
            case "adelaide" -> resp(region, 620_000, 0.048, 3.1, traceId);
            case "서울" -> resp(region, 1_100_000, 0.025, 3.5, traceId);
            case "부산" -> resp(region, 480_000, 0.058, 2.6, traceId);
            case "대구" -> resp(region, 380_000, 0.052, 2.8, traceId);
            case "인천" -> resp(region, 450_000, 0.047, 3.0, traceId);
            default -> resp(region, 750_000, 0.045, 3.2, traceId);
        };
    }

    // 응답 객체 만들기 (double => BigDecimal)
    private RegionAnalysisResponse resp(String region, double price, double growth, double risk, String traceId) {
        return RegionAnalysisResponse.builder()
                .region(region)
                .averagePrice(BigDecimal.valueOf(price))
                .growthRate(BigDecimal.valueOf(growth))
                .riskScore(BigDecimal.valueOf(risk))
                .traceId(traceId)
                .build();
    }
}
