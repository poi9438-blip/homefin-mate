package com.homefinmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.common.TraceIds;
import com.homefinmate.entity.LoanSimulation;
import com.homefinmate.entity.UserActivityLog;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserActivityLogRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 요청 traceId 규칙 통합 테스트: 헤더 하나가 기준, 응답 헤더·응답 본문·DB가 같은 값 (#90)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceIdApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    LoanSimulationRepository loanSimulationRepository;
    @Autowired
    UserActivityLogRepository userActivityLogRepository;

    private String email;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        email = ApiTestSupport.uniqueEmail("trace");
        token = new ApiTestSupport(mockMvc, objectMapper).signupAndLogin(email);
    }

    // 본문에 bodyTraceId를 넣어 대출 계산 (헤더는 header가 null이 아닐 때만)
    private MockHttpServletResponse calculate(String header, String bodyTraceId) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/loan/calculate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanAmount\":100000000,\"annualInterestRate\":4,\"termMonths\":120,"
                        + "\"monthlyIncome\":4000000,\"traceId\":\"" + bodyTraceId + "\"}");
        if (header != null) {
            req.header(TraceIds.HEADER, header);
        }
        return mockMvc.perform(req).andReturn().getResponse();
    }

    // 응답 헤더·응답 본문·계산 기록·이용 기록의 traceId가 모두 expected
    private void assertSameEverywhere(MockHttpServletResponse res, String expected) throws Exception {
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(TraceIds.HEADER)).isEqualTo(expected);
        assertThat(body.path("traceId").asText()).isEqualTo(expected);
        assertThat(body.path("data").path("traceId").asText()).isEqualTo(expected);
        assertThat(loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .singleElement().extracting(LoanSimulation::getTraceId).isEqualTo(expected);
        assertThat(userActivityLogRepository.findAll()).filteredOn(a -> userId.equals(a.getUserId()))
                .singleElement().extracting(UserActivityLog::getTraceId).isEqualTo(expected);
    }

    @Test
    @DisplayName("[TC-ERR-013] 헤더와 본문 traceId가 다르면 헤더 값이 응답 헤더·응답 본문·계산 기록·이용 기록에 똑같이 남음 (#90)")
    void headerWins_overBodyTraceId() throws Exception {
        assertSameEverywhere(calculate("hfm-header-90", "hfm-body-other"), "hfm-header-90");
    }

    @Test
    @DisplayName("[TC-ERR-014] 헤더 없이 본문에만 traceId를 보내면 서버가 만든 hfm- 값 하나로 응답·DB가 일치하고 본문 값은 쓰지 않음 (#90)")
    void noHeader_serverGeneratedEverywhere() throws Exception {
        MockHttpServletResponse res = calculate(null, "hfm-body-only");
        String generated = res.getHeader(TraceIds.HEADER);

        assertThat(generated).startsWith("hfm-").isNotEqualTo("hfm-body-only");
        assertSameEverywhere(res, generated);
    }

    @Test
    @DisplayName("[TC-ERR-015] 헤더 traceId가 허용 형식(영문·숫자·._- 64자 이하)이 아니면 서버가 새 hfm- 값을 만들어 씀, GET 응답에도 traceId (#90)")
    void invalidHeader_replacedWithGenerated() throws Exception {
        MockHttpServletResponse res = calculate("bad trace;id", "hfm-body");
        String generated = res.getHeader(TraceIds.HEADER);

        assertThat(generated).startsWith("hfm-").doesNotContain(" ");
        assertSameEverywhere(res, generated);

        MockHttpServletResponse tooLong = mockMvc
                .perform(get("/api/loan/history").header("Authorization", bearer(token))
                        .header(TraceIds.HEADER, "a".repeat(65)))
                .andReturn().getResponse();
        assertThat(tooLong.getHeader(TraceIds.HEADER)).startsWith("hfm-");
        assertThat(objectMapper.readTree(tooLong.getContentAsString()).path("traceId").asText())
                .isEqualTo(tooLong.getHeader(TraceIds.HEADER));
    }
}
