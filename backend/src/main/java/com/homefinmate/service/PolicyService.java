package com.homefinmate.service;

import com.homefinmate.dto.policy.PolicyCondition;
import com.homefinmate.dto.policy.PolicyCondition.Result;
import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyProduct.Eligibility;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// 한국 주택구입 정책대출(디딤돌대출, 보금자리론) 자격 조건 판정
@Service
@RequiredArgsConstructor
public class PolicyService {

    // ── 공식 출처 (확인일 2026-10-16) ─────────────────────────────────────────
    private static final String CHECKED_DATE = "2026-10-16";
    private static final String DIDIMDOL_URL = "https://nhuf.molit.go.kr/FP/FP05/FP0503/FP05030101.jsp";
    private static final String BOGEUMJARI_URL = "https://www.hf.go.kr/ko/sub01/sub01_01_01.do";

    private static final BigDecimal DTI_LIMIT = new BigDecimal("0.60");
    private static final Set<String> CAPITAL_AREA = Set.of("서울", "인천", "경기");

    public PolicyRecommendationResponse recommend(PolicyRecommendationRequest request) {
        List<PolicyProduct> products = new ArrayList<>(List.of(didimdol(request), bogeumjari(request)));
        // 충족 → 확인 필요 → 미충족 순 (enum 선언 순서)
        products.sort(Comparator.comparing(PolicyProduct::getEligibility));

        int eligible = (int) products.stream().filter(p -> p.getEligibility() == Eligibility.ELIGIBLE).count();
        int needsCheck = (int) products.stream().filter(p -> p.getEligibility() == Eligibility.NEEDS_CHECK).count();

        return PolicyRecommendationResponse.builder()
                .recommendedPolicies(products)
                .summary(String.format("입력한 정보 기준으로 %d개 상품 중 조건 충족 %d개, 확인 필요 %d개입니다. "
                        + "최종 자격과 한도는 각 기관 심사에서 결정됩니다.", products.size(), eligible, needsCheck))
                .eligibleCount(eligible)
                .traceId(request.getTraceId())
                .build();
    }

    // ── 내집마련 디딤돌대출 (주택도시기금) ─────────────────────────────────────
    // 소득: 부부합산 연 6천만 원 (생애최초·2자녀 이상 7천만 원, 신혼 8.5천만 원)
    // 주택: 세대원 전원 무주택, 평가액 5억 원 (신혼·2자녀 이상 6억 원)
    // 한도: 2억 원 (생애최초 2.4억 원, 신혼·2자녀 이상 3.2억 원), 2025-06-27 이후 계약분
    // LTV 70% (생애최초 80%, 수도권·규제지역 70%), DTI 60%
    private PolicyProduct didimdol(PolicyRecommendationRequest r) {
        List<PolicyCondition> conditions = new ArrayList<>();

        conditions.add(incomeCheck(r, didimdolIncomeLimit(r, false), didimdolIncomeLimit(r, true)));
        conditions.add(switch (r.getPropertyType() == null ? "" : r.getPropertyType().toUpperCase()) {
            case "NONE", "RENT" -> new PolicyCondition("무주택", Result.MET, "세대원 전원이 무주택이어야 합니다");
            case "OWN" -> new PolicyCondition("무주택", Result.NOT_MET, "주택 보유 시 이용할 수 없습니다");
            default -> new PolicyCondition("무주택", Result.UNKNOWN, "주택 보유 여부를 입력하지 않았습니다");
        });
        conditions.add(rangeCheck("주택 가격", r.getHousePrice(),
                didimdolPriceLimit(r, false), didimdolPriceLimit(r, true), "주택 가격을 입력하지 않았습니다"));

        BigDecimal low = maxLoan(didimdolLimit(r, false), r.getHousePrice(), didimdolLtv(r, false));
        BigDecimal high = maxLoan(didimdolLimit(r, true), r.getHousePrice(), didimdolLtv(r, true));
        conditions.add(rangeCheck("대출 금액", r.getLoanAmount(), low, high, "대출 금액을 입력하지 않았습니다"));
        conditions.add(dtiCheck(r));

        return product("내집마련 디딤돌대출", "주택도시기금",
                "무주택 서민의 주택 구입 자금을 낮은 금리로 지원하는 정부 대출입니다.",
                conditions,
                List.of("부부합산 연소득으로 다시 확인",
                        "전용면적 85㎡ 이하 (수도권 외 읍·면 100㎡ 이하)",
                        "순자산 5.11억 원 이하 (2026년 기준)",
                        "성년 세대주, 신용 기준, 다른 주택담보대출 미이용"),
                low, "연 2.85~4.15% (소득·기간별, 우대금리 별도)", DIDIMDOL_URL);
    }

    private BigDecimal didimdolIncomeLimit(PolicyRecommendationRequest r, boolean optimistic) {
        if (flag(r.getNewlywed(), optimistic))
            return won(85_000_000);
        if (flag(r.getFirstHome(), optimistic) || children(r, optimistic) >= 2)
            return won(70_000_000);
        return won(60_000_000);
    }

    private BigDecimal didimdolPriceLimit(PolicyRecommendationRequest r, boolean optimistic) {
        return flag(r.getNewlywed(), optimistic) || children(r, optimistic) >= 2 ? won(600_000_000) : won(500_000_000);
    }

    private BigDecimal didimdolLimit(PolicyRecommendationRequest r, boolean optimistic) {
        if (flag(r.getNewlywed(), optimistic) || children(r, optimistic) >= 2)
            return won(320_000_000);
        if (flag(r.getFirstHome(), optimistic))
            return won(240_000_000);
        return won(200_000_000);
    }

    // 생애최초는 80%, 단 수도권·규제지역은 70% (규제지역은 입력이 없어 수도권만 반영)
    private BigDecimal didimdolLtv(PolicyRecommendationRequest r, boolean optimistic) {
        boolean capital = r.getRegion() == null ? !optimistic : CAPITAL_AREA.contains(r.getRegion().trim());
        return flag(r.getFirstHome(), optimistic) && !capital ? new BigDecimal("0.80") : new BigDecimal("0.70");
    }

    // ── 보금자리론 (한국주택금융공사) ──────────────────────────────────────────
    // 소득: 부부합산 연 7천만 원 (신혼 8.5천만 원, 자녀 1명 9천만 원, 다자녀 1억 원)
    // 주택: 무주택 또는 1주택(기존 주택 3년 내 처분 조건), 주택 가격 6억 원
    // 한도: 3.6억 원 (다자녀 4억 원, 생애최초 4.2억 원), LTV 아파트 70% (기타 65%), DTI 60%
    private PolicyProduct bogeumjari(PolicyRecommendationRequest r) {
        List<PolicyCondition> conditions = new ArrayList<>();

        conditions.add(incomeCheck(r, bogeumjariIncomeLimit(r, false), bogeumjariIncomeLimit(r, true)));
        conditions.add(switch (r.getPropertyType() == null ? "" : r.getPropertyType().toUpperCase()) {
            case "NONE", "RENT" -> new PolicyCondition("주택 보유", Result.MET, "무주택");
            case "OWN" -> new PolicyCondition("주택 보유", Result.UNKNOWN,
                    "1주택이면 기존 주택을 3년 안에 처분하는 조건으로 가능, 2주택 이상은 불가");
            default -> new PolicyCondition("주택 보유", Result.UNKNOWN, "주택 보유 여부를 입력하지 않았습니다");
        });
        conditions.add(rangeCheck("주택 가격", r.getHousePrice(), won(600_000_000), won(600_000_000),
                "주택 가격을 입력하지 않았습니다"));

        BigDecimal ltv = new BigDecimal("0.70");
        BigDecimal low = maxLoan(bogeumjariLimit(r, false), r.getHousePrice(), ltv);
        BigDecimal high = maxLoan(bogeumjariLimit(r, true), r.getHousePrice(), ltv);
        conditions.add(rangeCheck("대출 금액", r.getLoanAmount(), low, high, "대출 금액을 입력하지 않았습니다"));
        conditions.add(dtiCheck(r));

        return product("보금자리론", "한국주택금융공사",
                "만기까지 금리가 고정되는 장기 주택담보대출입니다.",
                conditions,
                List.of("부부합산 연소득으로 다시 확인",
                        "아파트가 아니면 LTV 65%",
                        "규제지역이면 LTV·DTI 10%p 차감 (생애최초·실수요자 예외)",
                        "신용점수 271점 이상"),
                low, "연 4.90~5.30% 고정 (2026년 10월 공시, 우대금리 최대 1.0%p)", BOGEUMJARI_URL);
    }

    private BigDecimal bogeumjariIncomeLimit(PolicyRecommendationRequest r, boolean optimistic) {
        int kids = children(r, optimistic);
        if (kids >= 2)
            return won(100_000_000);
        if (kids == 1)
            return won(90_000_000);
        if (flag(r.getNewlywed(), optimistic))
            return won(85_000_000);
        return won(70_000_000);
    }

    private BigDecimal bogeumjariLimit(PolicyRecommendationRequest r, boolean optimistic) {
        if (flag(r.getFirstHome(), optimistic))
            return won(420_000_000);
        if (children(r, optimistic) >= 2)
            return won(400_000_000);
        return won(360_000_000);
    }

    // ── 공통 판정 ────────────────────────────────────────────────────────────

    private PolicyCondition incomeCheck(PolicyRecommendationRequest r, BigDecimal low, BigDecimal high) {
        BigDecimal annual = r.getIncome() == null ? null : r.getIncome().multiply(BigDecimal.valueOf(12));
        PolicyCondition c = rangeCheck("연소득", annual, low, high, "소득을 입력하지 않았습니다");
        if (annual != null) {
            c.setDetail("월 소득 × 12 = " + format(annual) + " (본인 기준). " + c.getDetail());
        }
        return c;
    }

    private PolicyCondition dtiCheck(PolicyRecommendationRequest r) {
        if (r.getDsr() == null) {
            return new PolicyCondition("DTI 60% 이내", Result.UNKNOWN, "DSR을 입력하지 않았습니다");
        }
        String pct = r.getDsr().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
        boolean ok = r.getDsr().compareTo(DTI_LIMIT) <= 0;
        return new PolicyCondition("DTI 60% 이내", ok ? Result.MET : Result.NOT_MET,
                "입력한 DSR " + pct + "로 대신 확인 (실제 DTI는 기존 대출 이자도 포함)");
    }

    // 값이 low 이하면 충족, high 초과면 미충족, 그 사이는 가구 조건에 따라 달라서 확인 필요
    private PolicyCondition rangeCheck(String name, BigDecimal value, BigDecimal low, BigDecimal high, String missing) {
        if (value == null) {
            return new PolicyCondition(name, Result.UNKNOWN, missing);
        }
        String range = low.compareTo(high) == 0 ? format(low) : format(low) + "~" + format(high);
        if (value.compareTo(low) <= 0) {
            return new PolicyCondition(name, Result.MET, "기준 " + range + " 이하");
        }
        if (value.compareTo(high) > 0) {
            return new PolicyCondition(name, Result.NOT_MET, "기준 " + range + " 초과");
        }
        return new PolicyCondition(name, Result.UNKNOWN,
                "기준이 가구 조건(생애최초·신혼·자녀 수)에 따라 " + range + "라 확인 필요");
    }

    // 상품 한도와 주택 가격 × LTV 중 작은 값 (주택 가격을 모르면 상품 한도)
    private BigDecimal maxLoan(BigDecimal productLimit, BigDecimal housePrice, BigDecimal ltv) {
        if (housePrice == null)
            return productLimit;
        return productLimit.min(housePrice.multiply(ltv).setScale(0, RoundingMode.DOWN));
    }

    private PolicyProduct product(String name, String provider, String description, List<PolicyCondition> conditions,
            List<String> additionalChecks, BigDecimal maxLoan, String rate, String url) {
        Eligibility eligibility = conditions.stream().anyMatch(c -> c.getResult() == Result.NOT_MET)
                ? Eligibility.NOT_ELIGIBLE
                : conditions.stream().anyMatch(c -> c.getResult() == Result.UNKNOWN)
                        ? Eligibility.NEEDS_CHECK
                        : Eligibility.ELIGIBLE;
        return PolicyProduct.builder()
                .name(name).provider(provider).description(description)
                .eligibility(eligibility).conditions(conditions).additionalChecks(additionalChecks)
                .maxLoanAmount(maxLoan).interestRate(rate)
                .sourceUrl(url).checkedDate(CHECKED_DATE)
                .build();
    }

    private boolean flag(Boolean value, boolean optimistic) {
        return value != null ? value : optimistic;
    }

    private int children(PolicyRecommendationRequest r, boolean optimistic) {
        return r.getChildCount() != null ? r.getChildCount() : (optimistic ? 2 : 0);
    }

    private static BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }

    // 금액 표시: 1억 이상은 "2.4억 원", 미만은 "6,000만 원"
    private String format(BigDecimal won) {
        if (won.compareTo(BigDecimal.valueOf(100_000_000)) >= 0) {
            return won.divide(BigDecimal.valueOf(100_000_000), 2, RoundingMode.DOWN).stripTrailingZeros()
                    .toPlainString() + "억 원";
        }
        return String.format("%,d만 원", won.divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN).longValue());
    }
}
