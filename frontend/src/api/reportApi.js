import axiosInstance from "./axiosInstance";

// 리포트 API (요약)
export const reportApi = {
  // 내 요약 리포트
  getSummary: () => axiosInstance.get("/report/summary"),
};
