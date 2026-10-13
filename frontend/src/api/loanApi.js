import axiosInstance from "./axiosInstance";
import { generateTraceId, DEFAULT_EVENT_VERSION } from "../utils/traceId";

// 대출 API (계산, DSR 단독 계산, 내 계산 이력)
export const loanApi = {
  // 대출 계산 (결과 저장, 로그인 필요)
  calculate: (data) =>
    axiosInstance.post("/loan/calculate", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),

  // DSR 단독 계산 (로그인 불필요)
  calculateDsr: (data) =>
    axiosInstance.post("/loan/dsr", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),

  // 내 계산 이력 (최신순)
  getHistory: () => axiosInstance.get("/loan/history"),
};
