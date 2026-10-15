package com.homefinmate.service;

import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// PolicyService 점수 규칙 검증 (정책별 점수와 상위 3개 순서)
class PolicyServiceTest {

        private final PolicyService policyService = new PolicyService();

        private PolicyRecommendationRequest request(String income, String existingDebt, String loanAmount,
                        String dsr, String propertyType, String region) {
                PolicyRecommendationRequest r = new PolicyRecommendationRequest();
                r.setIncome(income == null ? null : new BigDecimal(income));
                r.setExistingDebt(existingDebt == null ? null : new BigDecimal(existingDebt));
                r.setLoanAmount(loanAmount == null ? null : new BigDecimal(loanAmount));
                r.setDsr(dsr == null ? null : new BigDecimal(dsr));
                r.setPropertyType(propertyType);
                r.setRegion(region);
                r.setTraceId("trace-policy");
                return r;
        }

        private int scoreOf(PolicyRecommendationResponse res, String name) {
                return res.getRecommendedPolicies().stream()
                                .filter(p -> p.getName().equals(name))
                                .findFirst().map(PolicyProduct::getScore).orElse(-1);
        }

        // 달러 기준 저소득 무주택자: 점수 4, 4, 3 순서, 합계 11
        @Test
        @DisplayName("[TC-POL-001] 달러 기준 저소득 무주택자: 보조금,저소득 지원이 4점으로 상위")
        void recommend_lowIncomeFirstHomeBuyer() {
                PolicyRecommendationResponse res = policyService.recommend(
                                request("2000", "0", "200000", "0.15", "NONE", "부산"));

                assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getName)
                                .containsExactly("First Home Buyer Grant", "Low-Income Housing Assistance",
                                                "Stamp Duty Concession");
                assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getScore)
                                .containsExactly(4, 4, 3);
                assertThat(res.getScore()).isEqualTo(11);
                assertThat(res.getSummary()).isEqualTo(
                                "Based on your profile, you may qualify for: First Home Buyer Grant, "
                                                + "Low-Income Housing Assistance, Stamp Duty Concession. "
                                                + "As a first-home buyer you have additional benefits.");
                assertThat(res.getTraceId()).isEqualTo("trace-policy");
        }

        @Test
        @DisplayName("[TC-POL-002] ⚠️#60 빈 요청도 무주택으로 간주하고 3개를 추천함")
        void recommend_emptyRequest_treatedAsNoHouse() {
                PolicyRecommendationResponse res = policyService.recommend(
                                request(null, null, null, null, null, null));

                assertThat(res.getRecommendedPolicies()).hasSize(3);
                assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getName)
                                .containsExactly("Stamp Duty Concession", "First Home Buyer Grant",
                                                "Regional Growth Incentive");
                assertThat(res.getScore()).isEqualTo(4);
                assertThat(res.getSummary()).endsWith("As a first-home buyer you have additional benefits.");
        }

        // 주택 보유자: 부채 대환 3점이 1위, 지역은 대소문자 무시, DSR 없으면 "높음"으로 판정
        @Test
        @DisplayName("[TC-POL-003] 주택 보유·고부채: 부채 대환 1위, 무주택 점수 없음")
        void recommend_ownerWithDebt() {
                PolicyRecommendationResponse res = policyService.recommend(
                                request("3000", "150000", null, null, "OWN", "Perth"));

                assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getName)
                                .containsExactly("Debt Reduction Refinance Scheme", "Regional Growth Incentive",
                                                "First Home Buyer Grant");
                assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getScore)
                                .containsExactly(3, 2, 1);
                assertThat(res.getSummary()).doesNotContain("first-home buyer");
        }
}
