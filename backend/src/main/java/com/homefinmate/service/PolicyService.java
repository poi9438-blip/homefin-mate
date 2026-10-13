package com.homefinmate.service;

import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 점수 기반 정책 추천 (정책 5개 채점 => 상위 3개)
@Service
@RequiredArgsConstructor
public class PolicyService {

    // 수 기준 (월 소득 3,000/5,000, DSR 0.30, 대출 300,000 — 달러 기준)
    private static final BigDecimal LOW_INCOME = BigDecimal.valueOf(3000);
    private static final BigDecimal MID_INCOME = BigDecimal.valueOf(5000);
    private static final BigDecimal DSR_HEALTHY = BigDecimal.valueOf(0.30);
    private static final BigDecimal SMALL_LOAN = BigDecimal.valueOf(300_000);

    // 지역 목록 (지역 인센티브 점수)
    private static final List<String> HIGH_GROWTH_REGIONS = List.of("brisbane", "perth", "부산", "대구", "광주", "인천");

    // 전체 채점 => 점수 내림차순 정렬 => 상위 3개 + 요약 + 합계 점수
    public PolicyRecommendationResponse recommend(PolicyRecommendationRequest request) {
        List<PolicyProduct> scoredProducts = scorePolicies(request);
        scoredProducts.sort(Comparator.comparingInt(PolicyProduct::getScore).reversed());

        List<PolicyProduct> top3 = scoredProducts.stream().limit(3).toList();
        int aggregateScore = top3.stream().mapToInt(PolicyProduct::getScore).sum();

        return PolicyRecommendationResponse.builder()
                .recommendedPolicies(top3)
                .summary(buildSummary(top3, request))
                .score(aggregateScore)
                .traceId(request.getTraceId())
                .build();
    }

    // 정책 5개 채점
    private List<PolicyProduct> scorePolicies(PolicyRecommendationRequest request) {
        List<PolicyProduct> products = new ArrayList<>();

        // 생애 최초 주택 구입 보조금: 무주택 +1, 저소득 +2 / 중간소득 +1, 소액 대출 +1
        {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (isNoHouse(request)) {
                score += 1;
                reasons.add("No existing property ownership");
            }
            if (isLowIncome(request)) {
                score += 2;
                reasons.add("Income below $3,000/mo — priority band");
            } else if (isMidIncome(request)) {
                score += 1;
                reasons.add("Income in $3,000–$5,000 moderate band");
            }
            if (isSmallLoan(request)) {
                score += 1;
                reasons.add("Loan amount under $300,000");
            }
            products.add(PolicyProduct.builder()
                    .name("First Home Buyer Grant")
                    .score(score)
                    .reasons(reasons)
                    .description("Government grant up to $30,000 for first-home purchasers.")
                    .build());
        }

        // 인지세(취득세) 감면: 무주택 +2, 소액 대출 +1
        {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (isNoHouse(request)) {
                score += 2;
                reasons.add("First-home buyer: full stamp duty waiver may apply");
            }
            if (isSmallLoan(request)) {
                score += 1;
                reasons.add("Property value likely in concession threshold");
            }
            products.add(PolicyProduct.builder()
                    .name("Stamp Duty Concession")
                    .score(score)
                    .reasons(reasons)
                    .description("Reduced or waived stamp duty for eligible first-home buyers.")
                    .build());
        }

        // 저소득 주거 지원: 저소득 +3 / 중간소득 +1, DSR 0.30 이하 +1
        {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (isLowIncome(request)) {
                score += 3;
                reasons.add("Income qualifies for low-income housing scheme");
            } else if (isMidIncome(request)) {
                score += 1;
                reasons.add("Borderline income eligibility");
            }
            if (isHealthyDsr(request)) {
                score += 1;
                reasons.add("Debt ratio within healthy range (≤ 30%)");
            }
            products.add(PolicyProduct.builder()
                    .name("Low-Income Housing Assistance")
                    .score(score)
                    .reasons(reasons)
                    .description("Subsidised lending rates for households earning under $3,000/month.")
                    .build());
        }

        // 지역 성장 인센티브: 성장 지역 +2, 무주택 +1
        {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (isHighGrowthRegion(request)) {
                score += 2;
                reasons.add("Region is in a designated high-growth zone");
            }
            if (isNoHouse(request)) {
                score += 1;
                reasons.add("First-home buyer in target region");
            }
            products.add(PolicyProduct.builder()
                    .name("Regional Growth Incentive")
                    .score(score)
                    .reasons(reasons)
                    .description("Cash-back and rate discount for buyers in high-growth regions.")
                    .build());
        }

        // 부채 감축 대환 제도: 기존 부채 100,000 초과 +2, DSR 0.30 초과(또는 없음)이고 소득 있음 +1
        {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (request.getExistingDebt() != null &&
                    request.getExistingDebt().compareTo(BigDecimal.valueOf(100_000)) > 0) {
                score += 2;
                reasons.add("Existing debt exceeds $100,000 — refinance may reduce interest");
            }
            if (!isHealthyDsr(request) && request.getIncome() != null) {
                score += 1;
                reasons.add("Elevated DSR ratio — debt consolidation recommended");
            }
            products.add(PolicyProduct.builder()
                    .name("Debt Reduction Refinance Scheme")
                    .score(score)
                    .reasons(reasons)
                    .description("Government-backed refinance scheme to lower monthly debt burden.")
                    .build());
        }

        return products;
    }

    // 요약 문장 만들기 (상위 정책 이름 나열, 무주택이면 안내 문장 추가)
    private String buildSummary(List<PolicyProduct> top3, PolicyRecommendationRequest request) {
        if (top3.isEmpty())
            return "No matching policies found for your profile.";
        StringBuilder sb = new StringBuilder("Based on your profile, you may qualify for: ");
        top3.forEach(p -> sb.append(p.getName()).append(", "));
        if (sb.charAt(sb.length() - 2) == ',')
            sb.setLength(sb.length() - 2);
        if (isNoHouse(request))
            sb.append(". As a first-home buyer you have additional benefits.");
        return sb.toString();
    }

    // 저소득: 소득 < 3,000
    private boolean isLowIncome(PolicyRecommendationRequest r) {
        return r.getIncome() != null && r.getIncome().compareTo(LOW_INCOME) < 0;
    }

    // 중간소득: 3,000 ≤ 소득 ≤ 5,000
    private boolean isMidIncome(PolicyRecommendationRequest r) {
        return r.getIncome() != null
                && r.getIncome().compareTo(LOW_INCOME) >= 0
                && r.getIncome().compareTo(MID_INCOME) <= 0;
    }

    // 무주택: NONE 또는 값 없음
    private boolean isNoHouse(PolicyRecommendationRequest r) {
        return "NONE".equalsIgnoreCase(r.getPropertyType())
                || r.getPropertyType() == null;
    }

    // 성장 지역 여부 (소문자 비교)
    private boolean isHighGrowthRegion(PolicyRecommendationRequest r) {
        if (r.getRegion() == null)
            return false;
        return HIGH_GROWTH_REGIONS.contains(r.getRegion().toLowerCase());
    }

    // 건강한 DSR: 0.30 이하 (값 없으면 false)
    private boolean isHealthyDsr(PolicyRecommendationRequest r) {
        return r.getDsr() != null && r.getDsr().compareTo(DSR_HEALTHY) <= 0;
    }

    // 소액 대출: 대출 금액 < 300,000
    private boolean isSmallLoan(PolicyRecommendationRequest r) {
        return r.getLoanAmount() != null && r.getLoanAmount().compareTo(SMALL_LOAN) < 0;
    }
}
