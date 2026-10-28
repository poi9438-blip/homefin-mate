package com.homefinmate.security;

import com.homefinmate.common.TraceIds;
import com.homefinmate.repository.UserRepository;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 요청 span에 hfm traceId 연결 (OTel trace id와 hfm 번호를 함께 찾기 위함)
class JwtAuthFilterSpanTest {

    @RegisterExtension
    static final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    private final JwtAuthFilter filter = new JwtAuthFilter(mock(JwtUtil.class), mock(UserRepository.class));

    // 에이전트가 만든 요청 span 역할을 하는 span 안에서 필터 실행 후, 그 span의 속성 반환
    private String hfmTraceIdOnSpan(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        Span span = otel.getOpenTelemetry().getTracer("test").spanBuilder("GET /api/loan/history").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            filter.doFilter(request, response, new MockFilterChain());
        } finally {
            span.end();
        }
        // 속성 이름은 상수가 아닌 글자로 확인 (FinFlow·RiskWatch와 같은 이름이어야 Tempo에서 함께 찾음)
        return otel.getSpans().get(otel.getSpans().size() - 1).getAttributes()
                .get(AttributeKey.stringKey("hfm.trace_id"));
    }

    @Test
    @DisplayName("[TC-OBS-001] 요청 span에 hfm.trace_id 속성: 보낸 X-Trace-Id 또는 서버가 만든 값, 응답 헤더와 같은 값")
    void requestSpan_hasHfmTraceId() throws Exception {
        MockHttpServletRequest withHeader = new MockHttpServletRequest("GET", "/api/loan/history");
        withHeader.addHeader(TraceIds.HEADER, "hfm-span-test");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        assertThat(hfmTraceIdOnSpan(withHeader, res1)).isEqualTo("hfm-span-test");

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        String generated = hfmTraceIdOnSpan(new MockHttpServletRequest("GET", "/api/loan/history"), res2);
        assertThat(generated).startsWith("hfm-").isEqualTo(res2.getHeader(TraceIds.HEADER));
    }
}
