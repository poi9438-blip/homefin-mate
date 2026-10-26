package com.homefinmate.common;

// 본문에 traceId가 있는 요청 (서버가 헤더 기준 traceId로 덮어씀, #90)
public interface TraceIdCarrier {
    void setTraceId(String traceId);
}
