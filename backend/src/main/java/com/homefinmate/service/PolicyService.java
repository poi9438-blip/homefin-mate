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

// 한국 주택구입 정책대출(디딤돌대출, 신생아 특례 디딤돌대출, 보금자리론) 자격 조건 판정과 추천
@Service
@RequiredArgsConstructor
public class PolicyService {

    // ── 공식 출처 (확인일 2026-10-26) ─────────────────────────────────────────
    private static final String CHECKED_DATE = "2026-10-26";
    private static final String DIDIMDOL_URL = "https://nhuf.molit.go.kr/FP/FP05/FP0503/FP05030101.jsp";
    private static final String NEWBORN_URL = "https://nhuf.molit.go.kr/FP/FP05/FP0503/FP05030801.jsp";
    private static final String BOGEUMJARI_URL = "https://www.hf.go.kr/ko/sub01/sub01_01_01.do";

    private static final String DIDIMDOL = "내집마련 디딤돌대출";
    private static final String NEWBORN = "신생아 특례 디딤돌대출";
    private static final String BOGEUMJARI = "보금자리론";
    private static final BigDecimal DTI_LIMIT = new BigDecimal("0.60");
    private static final Set<String> CAPITAL_AREA = Set.of("서울", "인천", "경기");

    public PolicyRecommendationResponse recommend(PolicyRecommendationRequest request) {
        List<PolicyProduct> products = new ArrayList<>(
                List.of(newborn(request), didimdol(request), bogeumjari(request)));
        // 충족 → 확인 필요 → 미충족 순 (enum 선언 순서)
        products.sort(Comparator.comparing(PolicyProduct::getEligibility));

        int eligible = (int) products.stream().filter(p -> p.getEligibility() == Eligibility.ELIGIBLE).count();
        int needsCheck = (int) products.stream().filter(p -> p.getEligibility() == Eligibility.NEEDS_CHECK).count();

        String[] recommendation = recommendation(products);

        return PolicyRecommendationResponse.builder()
                .recommendedPolicies(products)
                .summary(String.format("입력한 정보 기준으로 %d개 상품 중 조건 충족 %d개, 확인 필요 %d개입니다. "
                        + "최종 자격과 한도는 각 기관 심사에서 결정됩니다.", products.size(), eligible, needsCheck))
                .eligibleCount(eligible)
                .recommendedProduct(recommendation[0])
                .recommendationReason(recommendation[1])
                .traceId(request.getTraceId())
                .build();
    }

    // 추천: 조건을 모두 충족한 상품 중 공식 기본금리가 가장 낮은 상품. 반환값 {상품 이름 또는 null, 이유}
    private String[] recommendation(List<PolicyProduct> products) {
        List<PolicyProduct> eligible = products.stream().filter(p -> p.getEligibility() == Eligibility.ELIGIBLE)
                .toList();
        if (eligible.isEmpty()) {
            if (products.stream().anyMatch(p -> p.getEligibility() == Eligibility.NEEDS_CHECK)) {
                return new String[] { null, "확인이 필요한 조건이 있어 추천하지 않았습니다. 입력을 보완하면 다시 판정합니다." };
            }
            return new String[] { null, "입력한 조건으로는 모든 상품을 이용하기 어렵습니다." };
        }

        PolicyProduct best = eligible.stream()
                .min(Comparator.comparing(PolicyProduct::getBaseRate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
        StringBuilder reason = new StringBuilder();
        if (eligible.size() == 1) {
            reason.append(best.getName()).append("의 조건을 모두 충족합니다.");
        } else {
            reason.append(String.format("조건을 충족한 %d개 상품 중 %s 기준 공식 기본금리가 가장 낮은 %s(%s)%s 추천합니다 (",
                    eligible.size(), best.getBaseRateBasis(), best.getName(), rate(best),
                    particle(best.getName(), "을", "를")));
            reason.append(String.join(", ", eligible.stream().filter(p -> p != best)
                    .map(p -> p.getName() + " " + rate(p)).toList())).append(").");
        }
        for (PolicyProduct p : products) {
            if (p.getEligibility() == Eligibility.NOT_ELIGIBLE) {
                reason.append(" ").append(p.getName()).append(particle(p.getName(), "은", "는")).append(" ")
                        .append(conditionNames(p, Result.NOT_MET)).append(" 조건이 맞지 않습니다.");
            } else if (p.getEligibility() == Eligibility.NEEDS_CHECK && (p.getBaseRate() == null
                    || best.getBaseRate() == null || p.getBaseRate().compareTo(best.getBaseRate()) < 0)) {
                reason.append(" ").append(p.getName()).append(particle(p.getName(), "은", "는")).append(" ")
                        .append(conditionNames(p, Result.UNKNOWN))
                        .append(" 확인이 필요해 추천하지 않았습니다. 확인되면 더 낮은 금리로 이용할 수도 있습니다.");
            }
        }
        return new String[] { best.getName(), reason.toString() };
    }

    // 이름 끝 글자 받침에 맞는 조사 (받침 있으면 withFinal, 없으면 withoutFinal)
    private String particle(String name, String withFinal, String withoutFinal) {
        char last = name.charAt(name.length() - 1);
        boolean hangul = last >= 0xAC00 && last <= 0xD7A3;
        return hangul && (last - 0xAC00) % 28 == 0 ? withoutFinal : withFinal;
    }

    // "연 3.80%" (기본금리를 모르면 "기본금리 모름")
    private String rate(PolicyProduct p) {
        return p.getBaseRate() == null ? "기본금리 모름" : "연 " + p.getBaseRate().setScale(2, RoundingMode.UNNECESSARY) + "%";
    }

    // 특정 결과인 조건 이름을 "주택 가격, 대출 금액" 형태로
    private String conditionNames(PolicyProduct product, Result result) {
        return String.join("·", product.getConditions().stream()
                .filter(c -> c.getResult() == result).map(PolicyCondition::getName).toList());
    }

    // ── 신생아 특례 디딤돌대출 (주택도시기금) ──────────────────────────────────
    // 대상: 대출접수일 기준 2년 내 출산(입양, 2023-01-01 이후 출생), 세대원 전원 무주택
    // 소득: 부부합산 연 1.3억 원 (맞벌이 2억 원, 부부 각자 1.3억 원 이하), 순자산 5.11억 원
    // 주택: 평가액 9억 원, 전용 85㎡ / 한도 4억 원 (2025-06-27 이후 계약분), LTV 70%(생애최초 80%, 수도권
    // 70%), DTI 60%
    private PolicyProduct newborn(PolicyRecommendationRequest r) {
        List<PolicyCondition> conditions = new ArrayList<>();

        Boolean born = r.getNewbornWithin2Years();
        conditions.add(born == null
                ? new PolicyCondition("2년 내 출산·입양", Result.UNKNOWN, "출산·입양 여부를 입력하지 않았습니다",
                        "대출 신청일 기준 2년 안에 출산·입양했는지 선택하면 판정할 수 있습니다")
                : born ? new PolicyCondition("2년 내 출산·입양", Result.MET, "대출 신청일 기준 2년 안에 출산·입양한 가구")
                        : new PolicyCondition("2년 내 출산·입양", Result.NOT_MET, "2년 안에 출산·입양한 가구만 이용할 수 있습니다"));
        BigDecimal incomeLow = Boolean.TRUE.equals(r.getDualIncome()) ? won(200_000_000) : won(130_000_000);
        BigDecimal incomeHigh = Boolean.FALSE.equals(r.getDualIncome()) ? won(130_000_000) : won(200_000_000);
        PolicyCondition income = withHint(incomeCheck(r, incomeLow, incomeHigh), "부부합산 연소득", incomeHigh,
                "이하가 기준입니다 (맞벌이면 2억 원)");
        if (income.getResult() == Result.UNKNOWN && income.getDetail() != null
                && income.getDetail().contains("기준이 가구 조건")) {
            income.setDetail(income.getDetail().replace("기준이 가구 조건(생애최초·신혼·자녀 수)에 따라", "맞벌이 여부에 따라 기준이"));
            income.setHint("맞벌이(부부 각자 1.3억 원 이하)면 부부합산 2억 원까지 충족합니다");
        }
        conditions.add(income);
        conditions.add(switch (r.getPropertyType() == null ? "" : r.getPropertyType().toUpperCase()) {
            case "NONE", "RENT" -> new PolicyCondition("무주택", Result.MET, "세대원 전원이 무주택이어야 합니다");
            case "OWN" -> new PolicyCondition("무주택", Result.NOT_MET, "주택 보유 시 구입자금은 이용할 수 없습니다",
                    "1주택 세대주는 기존 대출을 갈아타는 대환만 가능합니다");
            default -> new PolicyCondition("무주택", Result.UNKNOWN, "주택 보유 여부를 입력하지 않았습니다",
                    "주택 보유 여부를 선택하면 판정할 수 있습니다");
        });
        conditions.add(withHint(rangeCheck("주택 가격", r.getHousePrice(), won(900_000_000), won(900_000_000),
                "주택 가격을 입력하지 않았습니다"), "주택 가격", won(900_000_000), "이하 주택이면 충족"));

        BigDecimal low = maxLoan(won(400_000_000), r.getHousePrice(), didimdolLtv(r, false));
        BigDecimal high = maxLoan(won(400_000_000), r.getHousePrice(), didimdolLtv(r, true));
        conditions.add(withHint(rangeCheck("대출 금액", r.getLoanAmount(), low, high, "대출 금액을 입력하지 않았습니다"),
                "대출 금액", high, "이하로 줄이면 충족 (상품 한도 4억 원과 주택 가격 × LTV 중 작은 값)"));
        conditions.add(dtiCheck(r));

        return product(NEWBORN, "주택도시기금",
                "2년 안에 출산·입양한 가구에 낮은 특례금리로 주택 구입 자금을 지원하는 정부 대출입니다.",
                conditions,
                checks(r, "2023년 1월 1일 이후 출생아 (임신 중 태아는 제외)",
                        "맞벌이면 부부 각자 연소득 1.3억 원 이하",
                        "전용면적 85㎡ 이하 (수도권 외 읍·면 100㎡ 이하)",
                        "순자산 5.11억 원 이하 (2026년 기준)",
                        "특례금리는 기본 5년 (2년 안 추가 출산 시 1명당 5년 연장, 최장 15년)"),
                low, "연 1.80~4.50% (소득·기간별 특례금리, 지방 주택 0.2%p 인하, 우대 최대 0.5%p)", NEWBORN_URL,
                PolicyRates.newborn(annualIncome(r), r.getTermMonths()), fundBasis(r));
    }

    // ── 내집마련 디딤돌대출 (주택도시기금) ─────────────────────────────────────
    // 소득: 부부합산 연 6천만 원 (생애최초·2자녀 이상 7천만 원, 신혼 8.5천만 원)
    // 주택: 세대원 전원 무주택, 평가액 5억 원 (신혼·2자녀 이상 6억 원)
    // 한도: 2억 원 (생애최초 2.4억 원, 신혼·2자녀 이상 3.2억 원), 2025-06-27 이후 계약분
    // LTV 70% (생애최초 80%, 수도권·규제지역 70%), DTI 60%
    private PolicyProduct didimdol(PolicyRecommendationRequest r) {
        List<PolicyCondition> conditions = new ArrayList<>();

        conditions.add(withHint(incomeCheck(r, didimdolIncomeLimit(r, false), didimdolIncomeLimit(r, true)),
                "부부합산 연소득", didimdolIncomeLimit(r, true), "이하가 기준입니다. 보금자리론은 최대 1억 원(다자녀)까지 가능합니다"));
        conditions.add(switch (r.getPropertyType() == null ? "" : r.getPropertyType().toUpperCase()) {
            case "NONE", "RENT" -> new PolicyCondition("무주택", Result.MET, "세대원 전원이 무주택이어야 합니다");
            case "OWN" -> new PolicyCondition("무주택", Result.NOT_MET, "주택 보유 시 이용할 수 없습니다",
                    "세대원 전원이 무주택이어야 합니다. 보금자리론은 1주택이어도 기존 주택 처분 조건으로 가능합니다");
            default -> new PolicyCondition("무주택", Result.UNKNOWN, "주택 보유 여부를 입력하지 않았습니다",
                    "주택 보유 여부를 선택하면 판정할 수 있습니다");
        });
        conditions.add(withHint(rangeCheck("주택 가격", r.getHousePrice(),
                didimdolPriceLimit(r, false), didimdolPriceLimit(r, true), "주택 가격을 입력하지 않았습니다"),
                "주택 가격", didimdolPriceLimit(r, true), "이하 주택이면 충족 (신혼·자녀 2명 이상이면 6억 원)"));

        BigDecimal low = maxLoan(didimdolLimit(r, false), r.getHousePrice(), didimdolLtv(r, false));
        BigDecimal high = maxLoan(didimdolLimit(r, true), r.getHousePrice(), didimdolLtv(r, true));
        conditions.add(withHint(rangeCheck("대출 금액", r.getLoanAmount(), low, high, "대출 금액을 입력하지 않았습니다"),
                "대출 금액", high, "이하로 줄이면 충족 (상품 한도와 주택 가격 × LTV 중 작은 값)"));
        conditions.add(dtiCheck(r));

        return product(DIDIMDOL, "주택도시기금",
                "무주택 서민의 주택 구입 자금을 낮은 금리로 지원하는 정부 대출입니다.",
                conditions,
                checks(r, "전용면적 85㎡ 이하 (수도권 외 읍·면 100㎡ 이하)",
                        "순자산 5.11억 원 이하 (2026년 기준)",
                        "성년 세대주, 신용 기준, 다른 주택담보대출 미이용"),
                low, "연 2.85~4.15% (소득·기간별, 우대금리 별도)", DIDIMDOL_URL,
                PolicyRates.didimdol(annualIncome(r), r.getTermMonths()), fundBasis(r));
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

        conditions.add(withHint(incomeCheck(r, bogeumjariIncomeLimit(r, false), bogeumjariIncomeLimit(r, true)),
                "부부합산 연소득", bogeumjariIncomeLimit(r, true), "이하가 기준입니다 (신혼 8,500만 원, 자녀 1명 9,000만 원, 다자녀 1억 원)"));
        conditions.add(switch (r.getPropertyType() == null ? "" : r.getPropertyType().toUpperCase()) {
            case "NONE", "RENT" -> new PolicyCondition("주택 보유", Result.MET, "무주택");
            case "OWN" -> new PolicyCondition("주택 보유", Result.UNKNOWN,
                    "1주택이면 기존 주택을 3년 안에 처분하는 조건으로 가능, 2주택 이상은 불가",
                    "1주택이고 기존 주택을 대출 후 3년 안에 처분하면 이용할 수 있습니다");
            default -> new PolicyCondition("주택 보유", Result.UNKNOWN, "주택 보유 여부를 입력하지 않았습니다",
                    "주택 보유 여부를 선택하면 판정할 수 있습니다");
        });
        conditions.add(withHint(rangeCheck("주택 가격", r.getHousePrice(), won(600_000_000), won(600_000_000),
                "주택 가격을 입력하지 않았습니다"), "주택 가격", won(600_000_000), "이하 주택이면 충족"));

        BigDecimal ltv = new BigDecimal("0.70");
        BigDecimal low = maxLoan(bogeumjariLimit(r, false), r.getHousePrice(), ltv);
        BigDecimal high = maxLoan(bogeumjariLimit(r, true), r.getHousePrice(), ltv);
        conditions.add(withHint(rangeCheck("대출 금액", r.getLoanAmount(), low, high, "대출 금액을 입력하지 않았습니다"),
                "대출 금액", high, "이하로 줄이면 충족 (상품 한도와 주택 가격 × LTV 중 작은 값)"));
        conditions.add(dtiCheck(r));

        return product(BOGEUMJARI, "한국주택금융공사",
                "만기까지 금리가 고정되는 장기 주택담보대출입니다.",
                conditions,
                checks(r, "아파트가 아니면 LTV 65%",
                        "규제지역이면 LTV·DTI 10%p 차감 (생애최초·실수요자 예외)",
                        "신용점수 271점 이상"),
                low, "연 4.90~5.30% 고정 (2026년 10월 공시, 우대금리 최대 1.0%p)", BOGEUMJARI_URL,
                PolicyRates.bogeumjari(r.getTermMonths()),
                termText(r.getTermMonths(), PolicyRates.bogeumjariTerm(r.getTermMonths())) + " · t-보금자리론 · 우대 전");
    }

    // 다자녀 = 자녀 2명 이상으로 처리
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

    // 소득 판정: 부부합산 연소득이 있으면 그 값, 없으면 월 소득 × 12(본인 기준, 부부합산 확인 안내)
    private PolicyCondition incomeCheck(PolicyRecommendationRequest r, BigDecimal low, BigDecimal high) {
        if (r.getHouseholdAnnualIncome() != null) {
            PolicyCondition c = rangeCheck("연소득", r.getHouseholdAnnualIncome(), low, high, "");
            c.setDetail("부부합산 연소득 " + format(r.getHouseholdAnnualIncome()) + ". " + c.getDetail());
            return c;
        }
        BigDecimal annual = r.getIncome() == null ? null : r.getIncome().multiply(BigDecimal.valueOf(12));
        PolicyCondition c = rangeCheck("연소득", annual, low, high, "소득을 입력하지 않았습니다");
        if (annual != null) {
            c.setDetail("월 소득 × 12 = " + format(annual) + " (본인 기준, 부부합산으로 다시 확인). " + c.getDetail());
        }
        return c;
    }

    // 신청 전 추가 확인 조건 (부부합산 연소득을 입력하지 않았으면 맨 앞에 "부부합산 연소득으로 다시 확인")
    private List<String> checks(PolicyRecommendationRequest r, String... items) {
        List<String> list = new ArrayList<>();
        if (r.getHouseholdAnnualIncome() == null) {
            list.add("부부합산 연소득으로 다시 확인");
        }
        list.addAll(List.of(items));
        return list;
    }

    private PolicyCondition dtiCheck(PolicyRecommendationRequest r) {
        if (r.getDti() == null) {
            return new PolicyCondition("DTI 60% 이내", Result.UNKNOWN, "DTI를 입력하지 않았습니다",
                    "대출 계산 결과(DTI)를 넣으면 판정할 수 있습니다");
        }
        String pct = r.getDti().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
        boolean ok = r.getDti().compareTo(DTI_LIMIT) <= 0;
        return new PolicyCondition("DTI 60% 이내", ok ? Result.MET : Result.NOT_MET,
                "DTI " + pct + " ((신규 대출 월 상환액 + 기존 대출 월 이자) ÷ 월 소득)",
                ok ? null : dtiHint(r));
    }

    // DTI 미충족 피드백: 월 소득의 60%를 넘지 않는 월 상환액 (월 소득이 없으면 부부합산 연소득 ÷ 12)
    private String dtiHint(PolicyRecommendationRequest r) {
        BigDecimal monthly = r.getIncome() != null ? r.getIncome()
                : r.getHouseholdAnnualIncome() != null
                        ? r.getHouseholdAnnualIncome().divide(BigDecimal.valueOf(12), 0, RoundingMode.DOWN)
                        : null;
        String target = monthly == null ? "월 소득의 60%"
                : String.format("약 %,d원", monthly.multiply(DTI_LIMIT).setScale(0, RoundingMode.DOWN).longValue());
        return "월 상환액과 기존 대출 월 이자의 합을 " + target + " 이하로 낮추면 충족 (대출 금액을 줄이거나 기간을 늘리기)";
    }

    // 범위 조건 피드백: 미충족 → 기준값으로 무엇을 바꾸면 되는지,
    // 확인 필요(가구 조건) → 해당하면 기준이 올라 충족될 수 있음, 확인 필요(입력 없음) → 입력 안내
    private PolicyCondition withHint(PolicyCondition c, String inputName, BigDecimal high, String notMetSuffix) {
        if (c.getResult() == Result.NOT_MET) {
            c.setHint(inputName + " " + format(high) + " " + notMetSuffix);
        } else if (c.getResult() == Result.UNKNOWN && c.getDetail() != null && c.getDetail().contains("기준이 가구 조건")) {
            c.setHint("생애최초·신혼·자녀 수 조건에 해당하면 기준이 최대 " + format(high) + "까지 올라 충족될 수 있습니다");
        } else if (c.getResult() == Result.UNKNOWN) {
            c.setHint(inputName + "을(를) 입력하면 판정할 수 있습니다");
        }
        return c;
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
            List<String> additionalChecks, BigDecimal maxLoan, String rate, String url, BigDecimal baseRate,
            String baseRateBasis) {
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
                .baseRate(baseRate).baseRateBasis(baseRate == null ? null : baseRateBasis)
                .build();
    }

    // 금리표 소득 구간에 쓸 연소득 (부부합산 연소득, 없으면 월 소득 × 12, 둘 다 없으면 null)
    private BigDecimal annualIncome(PolicyRecommendationRequest r) {
        if (r.getHouseholdAnnualIncome() != null)
            return r.getHouseholdAnnualIncome();
        return r.getIncome() == null ? null : r.getIncome().multiply(BigDecimal.valueOf(12));
    }

    // 기금 대출 기본금리 기준 설명 ("부부합산 연소득 4,800만 원 · 30년 만기 · 우대 전")
    private String fundBasis(PolicyRecommendationRequest r) {
        BigDecimal income = annualIncome(r);
        String who = r.getHouseholdAnnualIncome() != null ? "부부합산 연소득 " : "연소득(월 소득 × 12) ";
        return (income == null ? "" : who + format(income) + " · ")
                + termText(r.getTermMonths(), PolicyRates.fundTerm(r.getTermMonths())) + " · 우대 전";
    }

    // "30년 만기" / "대출 기간 모름 → 30년 만기" / "25년 → 30년 만기 금리"
    private String termText(Integer termMonths, int bucket) {
        if (termMonths == null)
            return "대출 기간 모름 → " + bucket + "년 만기";
        int years = (termMonths + 11) / 12;
        return years == bucket ? bucket + "년 만기" : years + "년 → " + bucket + "년 만기 금리";
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
