package com.homefinmate.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

// RiskWatch 서킷 브레이커 (5xx·연결 실패·타임아웃만 실패로 집계)
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker riskWatchCircuitBreaker(
            @Value("${app.riskwatch.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${app.riskwatch.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${app.riskwatch.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${app.riskwatch.circuit-breaker.open-wait-ms:10000}") long openWaitMs,
            @Value("${app.riskwatch.circuit-breaker.half-open-calls:2}") int halfOpenCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .waitDurationInOpenState(Duration.ofMillis(openWaitMs))
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .build();
        return CircuitBreaker.of("riskWatch", config);
    }
}
