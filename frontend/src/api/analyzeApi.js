import axiosInstance from "./axiosInstance";
import { generateTraceId, DEFAULT_EVENT_VERSION } from "../utils/traceId";

export const analyzeApi = {
  // 통합 분석 요청 (대출 + 정책 + 위험 평가 + 지역 분석)
  analyze: (data, traceId) =>
    axiosInstance.post("/analyze", {
      ...data,
      traceId: traceId || generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),
};
