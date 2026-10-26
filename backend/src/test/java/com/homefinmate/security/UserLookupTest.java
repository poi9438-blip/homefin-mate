package com.homefinmate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefinmate.repository.LoanSimulationRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.homefinmate.support.ApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 사용자 조회 횟수 테스트 (#14): 필터가 찾은 사용자를 서비스가 그대로 씀
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLookupTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LoanSimulationRepository loanSimulationRepository;
    // 실제 저장소를 감싸서 호출 횟수만 셈
    @SpyBean
    UserRepository userRepository;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    private String loanBody() throws Exception {
        return api.json(Map.of("loanAmount", 100000000, "annualInterestRate", 4.0, "termMonths", 120,
                "monthlyIncome", 4000000));
    }

    @Test
    @DisplayName("[TC-AUTH-127] 로그인한 요청(계산 저장·이력·리포트·내 정보)마다 사용자 조회(findByEmail)는 필터의 1번뿐 (#14)")
    void authenticatedRequest_looksUpUserOnce() throws Exception {
        String email = ApiTestSupport.uniqueEmail("lookup");
        String token = api.signupAndLogin(email);

        clearInvocations(userRepository);
        mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(loanBody()))
                .andExpect(status().isOk());
        verify(userRepository, times(1)).findByEmail(email);

        for (String path : new String[] { "/api/loan/history", "/api/report/summary", "/api/auth/me" }) {
            clearInvocations(userRepository);
            mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk());
            verify(userRepository, times(1)).findByEmail(email);
        }
    }

    @Test
    @DisplayName("[TC-AUTH-128] 삭제된 사용자의 토큰으로 계산 저장·통합 분석·리포트 → 401, 계산 기록 저장 안 함 (서비스의 사용자 확인 대신, #58·#14)")
    void deletedUserToken_rejectedBeforeServices() throws Exception {
        String email = ApiTestSupport.uniqueEmail("gone");
        String token = api.signupAndLogin(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        userRepository.deleteById(userId);

        mockMvc.perform(post("/api/loan/calculate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(loanBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/analyze").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/report/summary").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        assertThat(loanSimulationRepository.countByUserId(userId)).isZero();
    }
}
