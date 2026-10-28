package com.homefinmate.metrics;

import com.homefinmate.dto.risk.RiskEvaluationResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 업무 지표가 대시보드·알림 규칙이 찾는 이름·속성 그대로 나가는지 (메모리 OTel)
class BusinessMetricsTest {

        @RegisterExtension
        static final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

        // 이름·속성은 상수 대신 글자로 확인 (Grafana 쿼리가 글자로 찾으므로 상수가 바뀌면 대시보드가 비게 됨)
        private List<LongPointData> points(String name) {
                return otel.getMetrics().stream()
                                .filter(m -> m.getName().equals(name))
                                .flatMap(m -> m.getLongSumData().getPoints().stream())
                                .toList();
        }

        private long value(String name, Attributes attributes) {
                return points(name).stream().filter(p -> p.getAttributes().equals(attributes))
                                .mapToLong(LongPointData::getValue).sum();
        }

        @Test
        @DisplayName("[TC-OBS-203] 업무 지표 이름·속성: hfm.riskwatch.evaluations{status}, hfm.loan.applications{product_code, outcome} 1씩, 나머지 조합은 시작부터 0")
        void exportsNamesAndAttributes() {
                BusinessMetrics metrics = new BusinessMetrics(otel.getOpenTelemetry());
                metrics.riskEvaluation(RiskEvaluationResponse.Status.UNAVAILABLE);
                metrics.application("BOGEUMJARI", "CONFLICT");

                AttributeKey<String> status = AttributeKey.stringKey("status");
                AttributeKey<String> product = AttributeKey.stringKey("product_code");
                AttributeKey<String> outcome = AttributeKey.stringKey("outcome");
                assertThat(value("hfm.riskwatch.evaluations", Attributes.of(status, "UNAVAILABLE"))).isEqualTo(1);
                assertThat(value("hfm.loan.applications", Attributes.of(product, "BOGEUMJARI", outcome, "CONFLICT")))
                                .isEqualTo(1);
                // 미리 만든 0: 상태 3가지, 상품 3 × 결과 6 = 18가지
                assertThat(points("hfm.riskwatch.evaluations")).hasSize(3);
                assertThat(points("hfm.loan.applications")).hasSize(18);
                assertThat(value("hfm.loan.applications", Attributes.of(product, "DIDIMDOL", outcome, "UNAVAILABLE")))
                                .isZero();
                assertThat(points("hfm.riskwatch.evaluations")).extracting(p -> p.getAttributes().get(status))
                                .containsExactlyInAnyOrder("EVALUATED", "UNAVAILABLE", "NOT_EVALUATED");
        }
}
