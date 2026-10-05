import axiosInstance from "./axiosInstance";
import { generateTraceId, DEFAULT_EVENT_VERSION } from "../utils/traceId";

// 인증 API
export const authApi = {
  // 회원가입 요청
  signup: (data) =>
    axiosInstance.post("/auth/signup", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),
};
