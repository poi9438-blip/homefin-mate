package com.homefinmate.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

// 모든 API의 공통 응답 형식 {status, message, data, traceId}

// Getter가 특히 중요: Jackson은 getter 메서드를 보고 JSON 필드를 만들기 때문
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

// null 필드는 JSON에서 제외
@JsonInclude(JsonInclude.Include.NON_NULL)

public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;
    private String traceId;

    // 성공 응답 생성
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message("OK")
                .data(data)
                .traceId(traceId)
                .build();
    }

    // 실패 응답 생성 (data 없음)
    public static <T> ApiResponse<T> error(String message, String traceId) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .traceId(traceId)
                .build();
    }
}
