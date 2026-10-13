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

  // 로그인 요청 (성공 시 토큰 발급)
  login: (data) =>
    axiosInstance.post("/auth/login", {
      ...data,
      traceId: generateTraceId(),
      eventVersion: DEFAULT_EVENT_VERSION,
    }),

  // 로그아웃 요청
  logout: () => axiosInstance.post("/auth/logout"),
};
