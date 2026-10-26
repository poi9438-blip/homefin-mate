import axiosInstance from "./axiosInstance";
import { generateTraceId, DEFAULT_EVENT_VERSION } from "../utils/traceId";

// 대출 API (계산, 내 계산 이력)
export const loanApi = {
  // 대출 계산 (결과 저장, 로그인 필요)
  calculate: (data) =>
    axiosInstance.post("/loan/calculate", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),

  // 내 계산 이력 한 페이지 (최신순, params: { page, size }, size는 최대 100, #51)
  getHistory: (params) => axiosInstance.get("/loan/history", { params }),
};
