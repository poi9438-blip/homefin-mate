package com.homefinmate.integration;

import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.repository.AuditLogRepository;
import com.homefinmate.service.AuditService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

// RiskWatch 실패 사유가 감사 기록에 실제로 저장되는지
@DataJpaTest
@ActiveProfiles("test")
@Import(AuditService.class)
class RiskWatchClientAuditTest {

    private static final String URL = "http://riskwatch.test/api/v1/risk/evaluate";

    @Autowired
    AuditService auditService;
    @Autowired
    AuditLogRepository auditLogRepository;

    private RiskWatchClient clientWith(MockRestServiceServer[] holder) {
        RestTemplate restTemplate = new RestTemplate();
        holder[0] = MockRestServiceServer.bindTo(restTemplate).build();
        RiskWatchClient client = new RiskWatchClient(restTemplate, auditService, CircuitBreaker.ofDefaults("test"));
        ReflectionTestUtils.setField(client, "riskWatchBaseUrl", "http://riskwatch.test");
        ReflectionTestUtils.setField(client, "riskWatchRetryCount", 2);
        ReflectionTestUtils.setField(client, "backoffMs", 0L);
        return client;
    }

    private RiskEvaluationRequest request() {
        return RiskEvaluationRequest.builder().userId("7").traceId("hfm-audit-risk").build();
    }

    // 짧은 실패 사유(연결 거부): 감사 기록 저장, fallback 정상
    @Test
    @DisplayName("[TC-RISK-008] 짧은 실패 사유는 감사 기록에 저장되고 fallback 반환")
    void shortReason_savedAndFallback() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        RiskWatchClient client = clientWith(server);
        server[0].expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThat(client.evaluateRisk(request()).isAvailable()).isFalse();

        assertThat(auditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getActorType()).isEqualTo("SYSTEM");
                    assertThat(log.getAction()).startsWith("RISKWATCH_INTEGRATION_FAILURE: ");
                    System.out.println("short action = " + log.getAction());
                });
    }

    // 긴 실패 사유도 255자로 잘라 저장하고 fallback 반환
    @Test
    @DisplayName("[TC-RISK-009] 긴 실패 사유도 255자로 잘라 저장하고 fallback 반환 (#37)")
    void longReason_truncatedAndFallback() {
        String html = "<html><body><h1>502 Bad Gateway</h1><p>" + "upstream connect() failed. ".repeat(20)
                + "</p></body></html>";
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        RiskWatchClient client = clientWith(server);
        server[0].expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body(html));

        assertThat(client.evaluateRisk(request()).isAvailable()).isFalse();

        assertThat(auditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> assertThat(log.getAction())
                        .startsWith("RISKWATCH_INTEGRATION_FAILURE: ")
                        .hasSize(255));
    }
}
