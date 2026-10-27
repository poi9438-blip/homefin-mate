package com.homefinmate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// 외부 서버 호출용 RestTemplate
@Configuration
public class RestTemplateConfig {

    // 기본 외부 호출용 (연결 5초, 응답 10초)
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    // RiskWatch 호출용 (타임아웃은 설정값, 기본 연결 1초·응답 2초)
    @Bean
    public RestTemplate riskWatchRestTemplate(
            @Value("${app.riskwatch.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${app.riskwatch.timeout-ms:2000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    // FinFlow 호출용 (기본 연결 1초·응답 3초, 신청 접수는 같은 키로 재시도하므로 시간 초과 뒤에도 안전)
    @Bean
    public RestTemplate finFlowRestTemplate(
            @Value("${app.finflow.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${app.finflow.timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
