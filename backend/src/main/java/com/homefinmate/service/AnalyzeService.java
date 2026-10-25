package com.homefinmate.service;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.analyze.AnalyzeResponse;
import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationRequest;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationRequest;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.integration.RiskWatchClient;
import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 통합 분석: 대출 계산 => 정책 추천 => 위험 평가 => 지역 분석 => 결과 저장 (같은 traceId, 저장만 트랜잭션)
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzeService {

        private final LoanService loanService;
        private final PolicyService policyService;
        private final RiskWatchClient riskWatchClient;
        private final AnalysisService analysisService;
        private final UserRepository userRepository;
        private final AnalyzeResultSaver analyzeResultSaver;

        public AnalyzeResponse analyze(AnalyzeRequest request, String email) {
                String traceId = request.getTraceId();
                // 토큰 이메일 => 사용자 id (위험 평가 요청과 저장에 사용)
                Long userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);

                // 개인정보 없이 시작 기록 (사용자 id, 지역)
                log.info("[{}] Starting unified analysis for userId={} region={}", traceId, userId,
                                request.getRegion());

                // ① 대출 계산 (저장은 ⑤에서)
                LoanCalculationRequest loanReq = new LoanCalculationRequest();
                loanReq.setLoanAmount(request.getLoanAmount());
                loanReq.setAnnualInterestRate(request.getAnnualInterestRate());
                loanReq.setTermMonths(request.getTermMonths());
                loanReq.setMonthlyIncome(request.getMonthlyIncome());
                loanReq.setExistingMonthlyInterest(request.getExistingMonthlyInterest());
                loanReq.setRegion(request.getRegion());
                loanReq.setTraceId(traceId);
                LoanCalculationResponse loanResult = loanService.compute(loanReq);

                // ② 정책 추천 (①의 DTI 사용)
                PolicyRecommendationRequest policyReq = new PolicyRecommendationRequest();
                policyReq.setIncome(request.getMonthlyIncome());
                policyReq.setLoanAmount(request.getLoanAmount());
                policyReq.setDti(loanResult.getDti());
                policyReq.setPropertyType(request.getPropertyType());
                policyReq.setRegion(request.getRegion());
                // 정책대출 판정용 입력 전달 (주택 가격, 부부합산 연소득, 가구 조건)
                policyReq.setHousePrice(request.getHousePrice());
                policyReq.setHouseholdAnnualIncome(request.getHouseholdAnnualIncome());
                policyReq.setFirstHome(request.getFirstHome());
                policyReq.setNewlywed(request.getNewlywed());
                policyReq.setChildCount(request.getChildCount());
                policyReq.setTraceId(traceId);
                policyReq.setEventVersion(request.getEventVersion());
                PolicyRecommendationResponse policyResult = policyService.recommend(policyReq);

                // ③ RiskWatch 위험 평가 (실패해도 fallback으로 계속 진행)
                String userIdStr = userId != null ? String.valueOf(userId) : null;
                RiskEvaluationRequest riskReq = RiskEvaluationRequest.builder()
                                .userId(userIdStr)
                                .loanAmount(request.getLoanAmount())
                                .monthlyIncome(request.getMonthlyIncome())
                                // RiskWatch 호환: dsr은 예전 계산(기존 대출 이자 미포함), dti는 새 계산
                                .dsr(DtiRule.calculate(loanResult.getMonthlyPayment(), null,
                                                request.getMonthlyIncome()))
                                .dti(loanResult.getDti())
                                .region(request.getRegion())
                                .houseOwnership(request.getPropertyType())
                                .sourceSystem("HOMEFIT_MATE")
                                .traceId(traceId)
                                .eventVersion(request.getEventVersion() != null ? request.getEventVersion() : "v1")
                                .build();
                RiskEvaluationResponse riskResult = riskWatchClient.evaluateRisk(riskReq);

                // ④ 지역 분석 (지역이 있을 때만, 실패해도 fallback)
                RegionAnalysisResponse regionResult = null;
                if (request.getRegion() != null && !request.getRegion().isBlank()) {
                        regionResult = analysisService.getRegionAnalysis(request.getRegion(), traceId);
                }

                // ⑤ 저장: 계산 기록 + 이용 기록을 한 트랜잭션으로 (외부 호출이 모두 끝난 뒤)
                loanResult.setSimulationId(analyzeResultSaver.save(userId, loanReq, loanResult));
                log.info("[{}] Analysis complete: dtiStatus={} riskLevel={} riskAvailable={}",
                                traceId, loanResult.getDtiStatus(), riskResult.getRiskLevel(),
                                riskResult.isAvailable());

                return AnalyzeResponse.builder()
                                .loan(loanResult)
                                .policy(policyResult)
                                .risk(riskResult)
                                .region(regionResult)
                                .traceId(traceId)
                                .build();
        }
}
