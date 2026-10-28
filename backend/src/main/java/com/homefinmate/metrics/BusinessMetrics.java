package com.homefinmate.metrics;

import com.homefinmate.dto.risk.RiskEvaluationResponse;
import com.homefinmate.integration.FinFlowClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 업무 지표: 통합 분석의 RiskWatch 결과, 신청의 FinFlow 결과 (Prometheus 이름은 …_total)
@Component
public class BusinessMetrics {

    static final String RISKWATCH_EVALUATIONS = "hfm.riskwatch.evaluations";
    static final String LOAN_APPLICATIONS = "hfm.loan.applications";
    static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    static final AttributeKey<String> PRODUCT_CODE = AttributeKey.stringKey("product_code");
    static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    // 신청 결과 (FinFlow 실패는 FinFlowException.Failure 이름 그대로)
    public static final String RECEIVED = "RECEIVED";
    public static final String REPLAYED = "REPLAYED";

    // 신청할 수 있는 상품 (ApplicationDtos.ApplyRequest의 productCode 규칙과 같음)
    static final List<String> PRODUCT_CODES = List.of("DIDIMDOL", "NEWBORN_DIDIMDOL", "BOGEUMJARI");

    private final LongCounter riskWatchEvaluations;
    private final LongCounter loanApplications;

    public BusinessMetrics(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter("homefin-mate");
        this.riskWatchEvaluations = meter.counterBuilder(RISKWATCH_EVALUATIONS)
                .setDescription("RiskWatch results in unified analysis")
                .setUnit("{evaluation}")
                .build();
        this.loanApplications = meter.counterBuilder(LOAN_APPLICATIONS)
                .setDescription("FinFlow results of loan applications")
                .setUnit("{application}")
                .build();
        // 정해진 조합을 0으로 미리 만듦 (알림의 increase()가 첫 건도 잡게)
        for (RiskEvaluationResponse.Status status : RiskEvaluationResponse.Status.values()) {
            riskWatchEvaluations.add(0, Attributes.of(STATUS, status.name()));
        }
        List<String> outcomes = new ArrayList<>(List.of(RECEIVED, REPLAYED));
        for (FinFlowClient.Failure failure : FinFlowClient.Failure.values()) {
            outcomes.add(failure.name());
        }
        for (String product : PRODUCT_CODES) {
            for (String outcome : outcomes) {
                loanApplications.add(0, Attributes.of(PRODUCT_CODE, product, OUTCOME, outcome));
            }
        }
    }

    // 통합 분석의 RiskWatch 결과 (판정 / 응답 없음 / 입력 모자람, 상태가 없으면 UNKNOWN)
    public void riskEvaluation(RiskEvaluationResponse.Status status) {
        riskWatchEvaluations.add(1, Attributes.of(STATUS, status == null ? "UNKNOWN" : status.name()));
    }

    // 신청의 FinFlow 결과 (RECEIVED / REPLAYED / FinFlow 실패 종류)
    public void application(String productCode, String outcome) {
        loanApplications.add(1, Attributes.of(PRODUCT_CODE, productCode, OUTCOME, outcome));
    }
}
