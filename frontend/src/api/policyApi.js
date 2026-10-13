import axiosInstance from "./axiosInstance";
import { generateTraceId, DEFAULT_EVENT_VERSION } from "../utils/traceId";

// 정책 API (추천, 목록)
export const policyApi = {
  // 정책 추천 (상위 3개)
  recommend: (data) =>
    axiosInstance.post("/policy/recommend", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),

  // 정책 목록
  listPolicies: () => axiosInstance.get("/policy/list"),
};
