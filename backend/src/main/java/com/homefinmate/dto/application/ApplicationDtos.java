package com.homefinmate.dto.application;

import com.homefinmate.dto.analyze.AnalyzeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

// 대출 신청 API의 요청·응답 (HomeFin ↔ 화면, FinFlow 응답 변환)
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    // 신청 요청: 통합 분석과 같은 입력 + 고른 상품 (+ 그 입력으로 만든 계산 기록 id, 선택)
    public record ApplyRequest(
            @NotBlank(message = "Product code is required")
            @Pattern(regexp = "DIDIMDOL|NEWBORN_DIDIMDOL|BOGEUMJARI",
                    message = "Product code must be one of DIDIMDOL, NEWBORN_DIDIMDOL, BOGEUMJARI")
            String productCode,

            @Positive(message = "Simulation id must be positive")
            Long simulationId,

            @NotNull(message = "Analysis inputs are required")
            @Valid
            AnalyzeRequest analysis) {
    }

    // 신청 접수 결과 (replayed: 같은 Idempotency-Key로 이미 접수된 신청을 다시 받은 것)
    public record Receipt(String applicationId, String productCode, String status, OffsetDateTime receivedAt,
            boolean replayed) {
    }

    // FinFlow 신청 조회·취소 응답 (신청자 본인용, 연소득·DTI 없음)
    public record FinFlowView(String applicationId, String productCode, String status, Long requestedAmount,
            Integer termMonths, OffsetDateTime receivedAt, OffsetDateTime decidedAt, String rejectReason) {
    }

    // 내 신청 한 줄 (available=false면 FinFlow에서 상태를 가져오지 못함, 신청 번호·상품·신청 시각만)
    public record MyApplication(String applicationId, String productCode, boolean available, String status,
            Long requestedAmount, Integer termMonths, OffsetDateTime receivedAt, OffsetDateTime decidedAt,
            String rejectReason) {

        public static MyApplication of(FinFlowView v) {
            return new MyApplication(v.applicationId(), v.productCode(), true, v.status(), v.requestedAmount(),
                    v.termMonths(), v.receivedAt(), v.decidedAt(), v.rejectReason());
        }

        public static MyApplication unavailable(String applicationId, String productCode) {
            return new MyApplication(applicationId, productCode, false, null, null, null, null, null, null);
        }
    }
}
