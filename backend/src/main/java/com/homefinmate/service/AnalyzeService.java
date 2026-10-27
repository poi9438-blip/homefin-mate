package com.homefinmate.service;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import com.homefinmate.dto.analyze.AnalyzeResponse;
import com.homefinmate.dto.analysis.RegionAnalysisResponse;
import com.homefinmate.dto.loan.LoanCalculationRequest;
import com.homefinmate.dto.loan.LoanCalculationResponse;
import com.homefinmate.dto.policy.PolicyRecommendationResponse;
import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.integration.RiskWatchClient;
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
        private final AnalyzeResultSaver analyzeResultSaver;

        // userId: 로그인 필터가 확인한 사용자 id (위험 평가 요청과 저장에 사용, #14)
        public AnalyzeResponse analyze(AnalyzeRequest request, Long userId) {
                String traceId = request.getTraceId();

                // 개인정보 없이 시작 기록 (사용자 id, 지역)
                log.info("[{}] Starting unified analysis for userId={} region={}", traceId, userId,
                                request.getRegion());

                // ① 대출 계산 (저장은 ⑤에서)
                LoanCalculationRequest loanReq = PolicyLoanInputs.loanRequest(request);
                LoanCalculationResponse loanResult = loanService.compute(loanReq);

                // ② 정책 추천 (①의 DTI 사용, 신청할 때도 같은 변환으로 다시 판정)
                PolicyRecommendationResponse policyResult = policyService
                                .recommend(PolicyLoanInputs.policyRequest(request, loanResult.getDti()));

                // ③ RiskWatch 판정 (추천 상품 하나, 실패해도 fallback으로 계속, 입력이 모자라면 부르지 않음)
                RiskWatchInput.Plan riskPlan = RiskWatchInput.plan(request, loanResult, policyResult, userId, traceId);
                RiskEvaluationResponse riskResult = riskPlan.request() != null
                                ? riskWatchClient.evaluateRisk(riskPlan.request())
                                : RiskEvaluationResponse.notEvaluated(riskPlan.skipReason(), traceId);

                // ④ 지역 분석 (지역이 있을 때만, 실패해도 fallback)
                RegionAnalysisResponse regionResult = null;
                if (request.getRegion() != null && !request.getRegion().isBlank()) {
                        regionResult = analysisService.getRegionAnalysis(request.getRegion(), traceId);
                }

                // ⑤ 저장: 계산 기록 + 이용 기록을 한 트랜잭션으로 (외부 호출이 모두 끝난 뒤)
                loanResult.setSimulationId(analyzeResultSaver.save(userId, loanReq, loanResult));
                log.info("[{}] Analysis complete: dtiStatus={} riskStatus={} verdict={}",
                                traceId, loanResult.getDtiStatus(), riskResult.getStatus(),
                                riskResult.getVerdict());

                return AnalyzeResponse.builder()
                                .loan(loanResult)
                                .policy(policyResult)
                                .risk(riskResult)
                                .region(regionResult)
                                .traceId(traceId)
                                .build();
        }
}
