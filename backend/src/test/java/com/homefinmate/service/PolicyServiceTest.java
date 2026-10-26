package com.homefinmate.service;

import com.homefinmate.dto.policy.PolicyCondition;
import com.homefinmate.dto.policy.PolicyCondition.Result;
import com.homefinmate.dto.policy.PolicyProduct;
import com.homefinmate.dto.policy.PolicyProduct.Eligibility;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 정책대출 자격 판정 테스트 (조건별 충족·미충족·확인 필요)
class PolicyServiceTest {

    private static final String DIDIMDOL = "내집마련 디딤돌대출";
    private static final String BOGEUMJARI = "보금자리론";
    private static final String NEWBORN = "신생아 특례 디딤돌대출";

    private final PolicyService policyService = new PolicyService();

    // 모든 정보를 넣은 기본 요청: 월 400만(연 4,800만), 무주택, 주택 4억, 대출 2억, DTI 30%, 부산,
    // 가구 조건·2년 내 출산 해당 없음
    private PolicyRecommendationRequest fullRequest() {
        PolicyRecommendationRequest r = new PolicyRecommendationRequest();
        r.setIncome(new BigDecimal("4000000"));
        r.setPropertyType("NONE");
        r.setHousePrice(new BigDecimal("400000000"));
        r.setLoanAmount(new BigDecimal("200000000"));
        r.setDti(new BigDecimal("0.30"));
        r.setRegion("부산");
        r.setFirstHome(false);
        r.setNewlywed(false);
        r.setChildCount(0);
        r.setNewbornWithin2Years(false);
        r.setTraceId("trace-policy");
        return r;
    }

    private PolicyProduct product(PolicyRecommendationResponse res, String name) {
        return res.getRecommendedPolicies().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private Result condition(PolicyProduct p, String name) {
        return p.getConditions().stream().filter(c -> c.getName().equals(name))
                .map(PolicyCondition::getResult).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("[TC-POL-301] 원화 기준 입력(연 4,800만·주택 4억·대출 2억)이면 디딤돌·보금자리론 조건 충족 (2년 내 출산이 아니면 신생아 특례만 미충족)")
    void koreanInput_bothEligible() {
        PolicyRecommendationResponse res = policyService.recommend(fullRequest());

        assertThat(product(res, DIDIMDOL).getEligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(product(res, BOGEUMJARI).getEligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(product(res, NEWBORN).getEligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
        assertThat(product(res, NEWBORN).getConditions()).filteredOn(c -> c.getResult() == Result.NOT_MET)
                .extracting(PolicyCondition::getName).containsExactly("2년 내 출산·입양");
        assertThat(res.getEligibleCount()).isEqualTo(2);
        // 디딤돌 한도: 일반 2억과 4억 × 70% = 2.8억 중 작은 값
        assertThat(product(res, DIDIMDOL).getMaxLoanAmount()).isEqualByComparingTo("200000000");
        assertThat(res.getSummary()).contains("조건 충족 2개");
        assertThat(res.getTraceId()).isEqualTo("trace-policy");
    }

    @Test
    @DisplayName("[TC-POL-201] 디딤돌 소득 경계: 연 6,000만 원은 충족, 넘으면 미충족 (가구 조건 해당 없음)")
    void didimdolIncomeBoundary() {
        PolicyRecommendationRequest atLimit = fullRequest();
        atLimit.setIncome(new BigDecimal("5000000"));
        PolicyRecommendationRequest over = fullRequest();
        over.setIncome(new BigDecimal("5000001"));

        assertThat(condition(product(policyService.recommend(atLimit), DIDIMDOL), "연소득")).isEqualTo(Result.MET);
        assertThat(condition(product(policyService.recommend(over), DIDIMDOL), "연소득")).isEqualTo(Result.NOT_MET);
    }

    @Test
    @DisplayName("[TC-POL-202] 가구 조건을 모르면 기준 사이 소득은 확인 필요, 생애최초면 충족")
    void unknownHousehold_needsCheck_thenFirstHomeMeets() {
        PolicyRecommendationRequest unknown = fullRequest();
        unknown.setIncome(new BigDecimal("5500000"));
        unknown.setFirstHome(null);
        unknown.setNewlywed(null);
        unknown.setChildCount(null);
        PolicyRecommendationRequest firstHome = fullRequest();
        firstHome.setIncome(new BigDecimal("5500000"));
        firstHome.setFirstHome(true);

        PolicyProduct didimdolUnknown = product(policyService.recommend(unknown), DIDIMDOL);
        assertThat(condition(didimdolUnknown, "연소득")).isEqualTo(Result.UNKNOWN);
        assertThat(didimdolUnknown.getEligibility()).isEqualTo(Eligibility.NEEDS_CHECK);
        assertThat(condition(product(policyService.recommend(firstHome), DIDIMDOL), "연소득")).isEqualTo(Result.MET);
    }

    @Test
    @DisplayName("[TC-POL-203] 주택 보유자: 디딤돌은 미충족, 보금자리론은 1주택 처분 조건이라 확인 필요")
    void owner_didimdolNotEligible_bogeumjariNeedsCheck() {
        PolicyRecommendationRequest r = fullRequest();
        r.setPropertyType("OWN");

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(product(res, DIDIMDOL).getEligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
        assertThat(product(res, BOGEUMJARI).getEligibility()).isEqualTo(Eligibility.NEEDS_CHECK);
        // 정렬: 확인 필요(보금자리론)가 미충족(신생아 특례·디딤돌)보다 앞
        assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getName).containsExactly(BOGEUMJARI,
                NEWBORN, DIDIMDOL);
    }

    @Test
    @DisplayName("[TC-POL-204] 주택 5.5억: 디딤돌은 가격 초과(신혼이면 6억 기준으로 충족), 보금자리론은 충족")
    void housePrice_didimdolLimitDependsOnNewlywed() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("550000000"));
        PolicyRecommendationRequest newlywed = fullRequest();
        newlywed.setHousePrice(new BigDecimal("550000000"));
        newlywed.setNewlywed(true);

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(condition(product(res, DIDIMDOL), "주택 가격")).isEqualTo(Result.NOT_MET);
        assertThat(condition(product(res, BOGEUMJARI), "주택 가격")).isEqualTo(Result.MET);
        assertThat(condition(product(policyService.recommend(newlywed), DIDIMDOL), "주택 가격")).isEqualTo(Result.MET);
    }

    @Test
    @DisplayName("[TC-POL-205] 대출 한도는 상품 한도와 주택 가격 × LTV 70% 중 작은 값")
    void loanLimit_isMinOfProductLimitAndLtv() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("300000000"));
        r.setLoanAmount(new BigDecimal("250000000"));

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(product(res, BOGEUMJARI).getMaxLoanAmount()).isEqualByComparingTo("210000000");
        assertThat(condition(product(res, BOGEUMJARI), "대출 금액")).isEqualTo(Result.NOT_MET);
        assertThat(condition(product(res, DIDIMDOL), "대출 금액")).isEqualTo(Result.NOT_MET);
    }

    @Test
    @DisplayName("[TC-POL-206] DTI 60% 초과면 DTI 조건 미충족")
    void highDti_failsDti() {
        PolicyRecommendationRequest r = fullRequest();
        r.setDti(new BigDecimal("0.61"));

        assertThat(condition(product(policyService.recommend(r), BOGEUMJARI), "DTI 60% 이내")).isEqualTo(Result.NOT_MET);
    }

    @Test
    @DisplayName("[TC-POL-207] 빈 요청은 모든 조건이 확인 필요 (충족·미충족으로 단정하지 않음, #60)")
    void emptyRequest_everythingNeedsCheck() {
        PolicyRecommendationResponse res = policyService.recommend(new PolicyRecommendationRequest());

        assertThat(res.getRecommendedPolicies()).extracting(PolicyProduct::getEligibility)
                .containsOnly(Eligibility.NEEDS_CHECK);
        assertThat(res.getRecommendedPolicies()).flatExtracting(PolicyProduct::getConditions)
                .extracting(PolicyCondition::getResult).containsOnly(Result.UNKNOWN);
        assertThat(res.getEligibleCount()).isZero();
    }

    @Test
    @DisplayName("[TC-POL-208] 모든 상품에 공식 출처 URL·확인 날짜·금리 안내가 있음")
    void everyProduct_hasSourceAndDate() {
        PolicyRecommendationResponse res = policyService.recommend(fullRequest());

        assertThat(res.getRecommendedPolicies()).allSatisfy(p -> {
            assertThat(p.getSourceUrl()).startsWith("https://").containsAnyOf("molit.go.kr", "hf.go.kr");
            assertThat(p.getCheckedDate()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(p.getInterestRate()).isNotBlank();
        });
    }

    // 추천: 둘 다 충족이면 금리 범위가 더 낮은 디딤돌대출
    @Test
    @DisplayName("[TC-POL-209] 두 상품 모두 충족이면 디딤돌대출 추천 (금리 범위가 더 낮음)")
    void bothEligible_recommendsDidimdol() {
        PolicyRecommendationResponse res = policyService.recommend(fullRequest());

        assertThat(res.getRecommendedProduct()).isEqualTo(DIDIMDOL);
        assertThat(res.getRecommendationReason()).contains("금리");
    }

    // 추천: 디딤돌만 미충족이면 보금자리론, 이유에 미충족 조건 이름
    @Test
    @DisplayName("[TC-POL-210] 디딤돌이 미충족(주택 5.5억)이고 보금자리론이 충족이면 보금자리론 추천")
    void didimdolNotEligible_recommendsBogeumjari() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("550000000"));

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(res.getRecommendedProduct()).isEqualTo(BOGEUMJARI);
        assertThat(res.getRecommendationReason()).contains("주택 가격");
    }

    // 추천: 확인 필요인 상품은 추천하지 않음. 디딤돌이 확인 필요면 충족한 보금자리론 추천 + 안내
    @Test
    @DisplayName("[TC-POL-211] 디딤돌이 확인 필요(가구 조건 모름)이면 충족한 보금자리론 추천, 디딤돌은 확인 시 가능 안내")
    void didimdolNeedsCheck_recommendsBogeumjariWithHint() {
        PolicyRecommendationRequest r = fullRequest();
        r.setIncome(new BigDecimal("5500000"));
        r.setFirstHome(null);
        r.setNewlywed(null);
        r.setChildCount(null);

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(product(res, DIDIMDOL).getEligibility()).isEqualTo(Eligibility.NEEDS_CHECK);
        assertThat(res.getRecommendedProduct()).isEqualTo(BOGEUMJARI);
        assertThat(res.getRecommendationReason()).contains("연소득").contains("더 낮은 금리");
    }

    // 추천: 둘 다 미충족이면 추천 없음
    @Test
    @DisplayName("[TC-POL-212] 모든 상품이 미충족(주택 7억, 2년 내 출산 아님)이면 추천 없음")
    void bothNotEligible_noRecommendation() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("700000000"));

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(res.getRecommendedProduct()).isNull();
        assertThat(res.getRecommendationReason()).contains("모든 상품");
    }

    // 추천: 충족한 상품 없이 확인 필요만 있으면 추천 없음
    @Test
    @DisplayName("[TC-POL-213] 빈 요청(모두 확인 필요)이면 추천 없음, 입력 보완 안내")
    void onlyNeedsCheck_noRecommendation() {
        PolicyRecommendationResponse res = policyService.recommend(new PolicyRecommendationRequest());

        assertThat(res.getRecommendedProduct()).isNull();
        assertThat(res.getRecommendationReason()).contains("확인이 필요한 조건");
    }

    // 부부합산 연소득이 있으면 월 소득 대신 그 값으로 판정하고 "다시 확인" 안내를 빼고, 없으면 안내를 넣음
    @Test
    @DisplayName("[TC-POL-214] 부부합산 연소득(7,500만)이 있으면 그 값으로 판정, 없으면 월 소득 × 12 + 다시 확인 안내")
    void householdIncome_overridesMonthlyIncome() {
        PolicyRecommendationRequest withHousehold = fullRequest();
        withHousehold.setHouseholdAnnualIncome(new BigDecimal("75000000"));

        PolicyProduct didimdol = product(policyService.recommend(withHousehold), DIDIMDOL);
        PolicyProduct fallback = product(policyService.recommend(fullRequest()), DIDIMDOL);

        assertThat(condition(didimdol, "연소득")).isEqualTo(Result.NOT_MET);
        assertThat(didimdol.getConditions().get(0).getDetail()).contains("부부합산 연소득");
        assertThat(didimdol.getAdditionalChecks()).doesNotContain("부부합산 연소득으로 다시 확인");
        assertThat(condition(fallback, "연소득")).isEqualTo(Result.MET);
        assertThat(fallback.getAdditionalChecks()).contains("부부합산 연소득으로 다시 확인");
    }

    private String hint(PolicyProduct p, String name) {
        return p.getConditions().stream().filter(c -> c.getName().equals(name))
                .map(PolicyCondition::getHint).findFirst().orElseThrow();
    }

    // 피드백: 대출 금액 미충족이면 줄여야 할 금액(상품 한도와 주택 가격 × LTV 중 작은 값)을 알려 줌
    @Test
    @DisplayName("[TC-POL-215] 대출 금액 미충족이면 '2.1억 원 이하로 줄이면 충족' 피드백 (주택 3억 × 70%)")
    void loanAmountNotMet_hintShowsMaxAmount() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("300000000"));
        r.setLoanAmount(new BigDecimal("250000000"));

        assertThat(hint(product(policyService.recommend(r), BOGEUMJARI), "대출 금액"))
                .contains("2.1억 원").contains("줄이면 충족");
    }

    // 피드백: 주택 가격 미충족이면 기준 가격과 기준이 올라가는 조건을 알려 줌
    @Test
    @DisplayName("[TC-POL-216] 디딤돌 주택 가격 미충족(5.5억)이면 '5억 원 이하 주택이면 충족' 피드백")
    void housePriceNotMet_hintShowsLimit() {
        PolicyRecommendationRequest r = fullRequest();
        r.setHousePrice(new BigDecimal("550000000"));

        assertThat(hint(product(policyService.recommend(r), DIDIMDOL), "주택 가격"))
                .contains("5억 원").contains("신혼");
    }

    // 피드백: 가구 조건을 몰라 확인 필요이면 해당하면 기준이 최대 얼마까지 오르는지 알려 줌
    @Test
    @DisplayName("[TC-POL-217] 가구 조건 모름으로 소득이 확인 필요면 '해당하면 최대 8,500만 원까지' 피드백")
    void incomeNeedsCheck_hintShowsHouseholdCondition() {
        PolicyRecommendationRequest r = fullRequest();
        r.setIncome(new BigDecimal("5500000"));
        r.setFirstHome(null);
        r.setNewlywed(null);
        r.setChildCount(null);

        assertThat(hint(product(policyService.recommend(r), DIDIMDOL), "연소득"))
                .contains("생애최초").contains("8,500만 원");
    }

    // 피드백: DTI 미충족이면 넘지 말아야 할 월 상환액(월 소득 × 60%)을 알려 줌
    @Test
    @DisplayName("[TC-POL-218] DTI 미충족이면 '월 상환액 약 2,400,000원 이하' 피드백 (월 소득 400만 × 60%)")
    void dtiNotMet_hintShowsMaxMonthlyPayment() {
        PolicyRecommendationRequest r = fullRequest();
        r.setDti(new BigDecimal("0.61"));

        assertThat(hint(product(policyService.recommend(r), BOGEUMJARI), "DTI 60% 이내"))
                .contains("약 2,400,000원");
    }

    // 피드백: 충족한 조건에는 피드백이 없고, 입력이 없는 조건에는 입력 안내가 있음
    @Test
    @DisplayName("[TC-POL-219] 충족 조건은 피드백 없음, 입력 없는 조건은 '입력하면 판정' 안내")
    void hints_emptyForMet_andInputGuideForMissing() {
        PolicyRecommendationResponse full = policyService.recommend(fullRequest());
        PolicyRecommendationResponse empty = policyService.recommend(new PolicyRecommendationRequest());

        assertThat(full.getRecommendedPolicies()).flatExtracting(PolicyProduct::getConditions)
                .extracting(PolicyCondition::getHint).containsOnlyNulls();
        assertThat(empty.getRecommendedPolicies()).flatExtracting(PolicyProduct::getConditions)
                .extracting(PolicyCondition::getHint).doesNotContainNull()
                .anySatisfy(h -> assertThat(h).contains("입력하면 판정"));
    }

    // 2년 내 출산 가구는 세 상품 모두 충족, 기본금리가 가장 낮은 신생아 특례 추천
    @Test
    @DisplayName("[TC-POL-220] 2년 내 출산 가구(연 4,800만·30년)는 세 상품 충족, 공식 기본금리 2.65%·3.80%·5.20% 비교로 신생아 특례 추천")
    void newbornEligible_recommendedByBaseRate() {
        PolicyRecommendationRequest r = fullRequest();
        r.setNewbornWithin2Years(true);
        r.setTermMonths(360);

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(res.getEligibleCount()).isEqualTo(3);
        assertThat(product(res, NEWBORN).getBaseRate()).isEqualByComparingTo("2.65");
        assertThat(product(res, DIDIMDOL).getBaseRate()).isEqualByComparingTo("3.80");
        assertThat(product(res, BOGEUMJARI).getBaseRate()).isEqualByComparingTo("5.20");
        assertThat(res.getRecommendedProduct()).isEqualTo(NEWBORN);
        assertThat(res.getRecommendationReason()).contains("연 2.65%").contains("연 3.80%").contains("연 5.20%")
                .contains("30년 만기").contains("우대 전").contains("신생아 특례 디딤돌대출(연 2.65%)을 추천")
                .doesNotContain("(는)");
        assertThat(product(res, NEWBORN).getSourceUrl()).contains("FP05030801");
    }

    // 신생아 특례 소득 기준: 1.5억이면 맞벌이 충족(3.80%), 외벌이 미충족, 모르면 확인 필요
    @Test
    @DisplayName("[TC-POL-221] 신생아 특례 부부합산 1.5억: 맞벌이면 충족(1.3~1.5억 행 30년 3.80%), 외벌이면 미충족, 모르면 확인 필요 + 맞벌이 안내")
    void newbornIncome_dependsOnDualIncome() {
        PolicyRecommendationRequest r = fullRequest();
        r.setNewbornWithin2Years(true);
        r.setHouseholdAnnualIncome(new BigDecimal("150000000"));
        r.setTermMonths(360);

        r.setDualIncome(null);
        PolicyProduct unknown = product(policyService.recommend(r), NEWBORN);
        assertThat(condition(unknown, "연소득")).isEqualTo(Result.UNKNOWN);
        assertThat(unknown.getConditions()).filteredOn(c -> c.getName().equals("연소득"))
                .singleElement().satisfies(c -> assertThat(c.getHint()).contains("맞벌이"));

        r.setDualIncome(true);
        PolicyProduct dual = product(policyService.recommend(r), NEWBORN);
        assertThat(condition(dual, "연소득")).isEqualTo(Result.MET);
        assertThat(dual.getBaseRate()).isEqualByComparingTo("3.80");

        r.setDualIncome(false);
        assertThat(condition(product(policyService.recommend(r), NEWBORN), "연소득")).isEqualTo(Result.NOT_MET);
    }

    // 주택 8억: 디딤돌(5억)·보금자리론(6억)은 가격 초과, 신생아 특례(9억)만 충족, 한도 4억(8억 × 70% = 5.6억보다 작음)
    @Test
    @DisplayName("[TC-POL-222] 주택 8억이면 디딤돌·보금자리론은 주택 가격 미충족, 신생아 특례만 충족해 추천 (한도 4억)")
    void expensiveHouse_onlyNewborn() {
        PolicyRecommendationRequest r = fullRequest();
        r.setNewbornWithin2Years(true);
        r.setHousePrice(new BigDecimal("800000000"));

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(condition(product(res, DIDIMDOL), "주택 가격")).isEqualTo(Result.NOT_MET);
        assertThat(condition(product(res, BOGEUMJARI), "주택 가격")).isEqualTo(Result.NOT_MET);
        assertThat(res.getRecommendedProduct()).isEqualTo(NEWBORN);
        assertThat(product(res, NEWBORN).getMaxLoanAmount()).isEqualByComparingTo("400000000");
        assertThat(res.getRecommendationReason()).contains("주택 가격").contains("조건이 맞지 않습니다");
    }

    // 대출 기간별 기본금리: 15년, 25년(→30년 구간), 50년(기금은 최장 30년 구간)
    @Test
    @DisplayName("[TC-POL-223] 기본금리 만기 구간: 15년은 15년 금리, 25년은 30년 구간, 50년은 기금 30년·보금자리론 50년 금리")
    void baseRate_usesTermBucket() {
        PolicyRecommendationRequest r = fullRequest();

        r.setTermMonths(180);
        PolicyRecommendationResponse y15 = policyService.recommend(r);
        assertThat(product(y15, DIDIMDOL).getBaseRate()).isEqualByComparingTo("3.65");
        assertThat(product(y15, BOGEUMJARI).getBaseRate()).isEqualByComparingTo("5.10");
        assertThat(product(y15, DIDIMDOL).getBaseRateBasis()).contains("15년 만기");

        r.setTermMonths(300);
        PolicyProduct y25 = product(policyService.recommend(r), DIDIMDOL);
        assertThat(y25.getBaseRate()).isEqualByComparingTo("3.80");
        assertThat(y25.getBaseRateBasis()).contains("25년 → 30년 만기 금리");

        r.setTermMonths(600);
        PolicyRecommendationResponse y50 = policyService.recommend(r);
        assertThat(product(y50, DIDIMDOL).getBaseRate()).isEqualByComparingTo("3.80");
        assertThat(product(y50, BOGEUMJARI).getBaseRate()).isEqualByComparingTo("5.30");
    }

    // 출산 여부를 모르면 신생아 특례는 확인 필요라 추천하지 않지만, 더 낮은 금리일 수 있다고 안내
    @Test
    @DisplayName("[TC-POL-224] 출산 여부를 모르면 디딤돌 추천, 신생아 특례는 '2년 내 출산·입양 확인 시 더 낮은 금리' 안내")
    void newbornUnknown_recommendsDidimdolWithHint() {
        PolicyRecommendationRequest r = fullRequest();
        r.setNewbornWithin2Years(null);

        PolicyRecommendationResponse res = policyService.recommend(r);

        assertThat(product(res, NEWBORN).getEligibility()).isEqualTo(Eligibility.NEEDS_CHECK);
        assertThat(res.getRecommendedProduct()).isEqualTo(DIDIMDOL);
        assertThat(res.getRecommendationReason()).contains("2년 내 출산·입양 확인").contains("더 낮은 금리");
    }

    // 공식 금리표 모서리 값과 표 범위 밖 소득
    @Test
    @DisplayName("[TC-POL-225] 금리표 모서리 값이 공식 표와 같음 (신생아 1.80·4.50, 디딤돌 2.85·4.15), 표 범위를 넘는 소득은 기본금리 없음")
    void rateTable_cornersMatchOfficial() {
        assertThat(PolicyRates.newborn(new BigDecimal("20000000"), 120)).isEqualByComparingTo("1.80");
        assertThat(PolicyRates.newborn(new BigDecimal("200000000"), 360)).isEqualByComparingTo("4.50");
        assertThat(PolicyRates.didimdol(new BigDecimal("20000000"), 120)).isEqualByComparingTo("2.85");
        assertThat(PolicyRates.didimdol(new BigDecimal("85000000"), 360)).isEqualByComparingTo("4.15");
        assertThat(PolicyRates.didimdol(new BigDecimal("85000001"), 360)).isNull();
        assertThat(PolicyRates.newborn(new BigDecimal("200000001"), 360)).isNull();
        assertThat(PolicyRates.bogeumjari(120)).isEqualByComparingTo("5.00");
    }
}
