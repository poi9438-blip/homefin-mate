package com.homefinmate.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

// 리포트 응답 (종류, 생성 시각, 내용 Map)
@Data
@Builder
public class ReportResponse {
    // 리포트 id (현재는 사용자 id)
    private Long reportId;
    private Long userId;
    // 리포트 종류 (현재 SUMMARY만)
    private String reportType;
    // 리포트 내용 (총 계산 수, 최근 계산, DTI 추이 등)
    private Map<String, Object> reportData;
    private LocalDateTime generatedAt;
    private String traceId;
}
