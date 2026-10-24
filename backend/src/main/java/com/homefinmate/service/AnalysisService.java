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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// Python 지역 분석 호출 (실패 시 숫자 없이 '분석 불가'로 응답)
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final RestTemplate restTemplate;

    @Value("${app.python-service.base-url:http://localhost:8200}")
    private String pythonServiceBaseUrl;

    // 지역 분석 조회 (성공: Python 결과, 없는 지역·연결 실패: 분석 불가)
    public RegionAnalysisResponse getRegionAnalysis(String region, String traceId) {
        try {
            // 요청 주소 (base-url + /analysis/region?region=...)
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(pythonServiceBaseUrl + "/analysis/region")
                    .queryParam("region", region)
                    .encode()
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
        } catch (HttpClientErrorException.NotFound e) {
            // Python에 데이터가 없는 지역 (오타 포함)
            log.info("[{}] Region analysis not available for region '{}'", traceId, region);
            return unavailable(region, "UNSUPPORTED_REGION", traceId);
        } catch (RestClientException e) {
            // 호출 실패: 경고 로그 후 분석 불가
            log.warn("[{}] Python service unavailable for region '{}': {}", traceId, region, e.getMessage());
        }
        return unavailable(region, "SERVICE_UNAVAILABLE", traceId);
    }

    // 분석 불가 응답 (값은 비워 둠, 지어낸 숫자를 보내지 않음)
    private RegionAnalysisResponse unavailable(String region, String reason, String traceId) {
        return RegionAnalysisResponse.builder()
                .region(region)
                .available(false)
                .unavailableReason(reason)
                .traceId(traceId)
                .build();
    }
}
